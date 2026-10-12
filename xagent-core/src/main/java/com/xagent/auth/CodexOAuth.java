/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xagent.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * "Sign in with ChatGPT" (OpenAI Codex) OAuth: authorization-code + PKCE
 * via a localhost callback, plus OpenAI's bespoke device-code flow for
 * headless environments. Uses Codex CLI's public client id — the flow
 * OpenAI has endorsed for third-party tools (see plans/codex-auth.md for
 * the policy background).
 */
public class CodexOAuth {

	public static final String PROVIDER_ID = "openai-codex";
	public static final String CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
	public static final String DEFAULT_ISSUER = "https://auth.openai.com";
	public static final int CALLBACK_PORT = 1455;
	public static final String ORIGINATOR = "xagent";

	private static final String SCOPE = "openid profile email offline_access";
	private static final String JWT_AUTH_CLAIM = "https://api.openai.com/auth";
	private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600;
	private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(5);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String issuer;
	private final int callbackPort;
	private final HttpClient http;

	public CodexOAuth() {
		this(DEFAULT_ISSUER, CALLBACK_PORT);
	}

	/** @param issuer auth server base URL, @param callbackPort localhost callback port (injectable for tests) */
	public CodexOAuth(String issuer, int callbackPort) {
		this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		this.callbackPort = callbackPort;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	}

	private String redirectUri() {
		return "http://localhost:" + callbackPort + "/auth/callback";
	}

	/** Build the browser authorization URL for the given PKCE pair and state. */
	public String buildAuthorizeUrl(PkceUtil.Pkce pkce, String state) {
		var params = new LinkedHashMap<String, String>();
		params.put("response_type", "code");
		params.put("client_id", CLIENT_ID);
		params.put("redirect_uri", redirectUri());
		params.put("scope", SCOPE);
		params.put("code_challenge", pkce.challenge());
		params.put("code_challenge_method", "S256");
		params.put("id_token_add_organizations", "true");
		params.put("codex_cli_simplified_flow", "true");
		params.put("state", state);
		params.put("originator", ORIGINATOR);
		return issuer + "/oauth/authorize?" + formEncode(params);
	}

	/**
	 * Browser login: starts the localhost callback server, hands the
	 * authorization URL to {@code onAuthUrl} (print it / open a browser),
	 * waits for the redirect, and exchanges the code for tokens.
	 */
	public OAuthCredentials loginViaBrowser(Consumer<String> onAuthUrl) throws IOException {
		var pkce = PkceUtil.generate();
		String state = PkceUtil.randomState();
		var codeFuture = new CompletableFuture<String>();

		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress("localhost", callbackPort), 0);
		} catch (java.net.BindException inUse) {
			// "Address already in use" on its own sends people hunting for a
			// process. The usual cause is this flow itself: a sign-in whose
			// browser was closed holds the port until it times out.
			throw new IOException("The sign-in callback port " + callbackPort + " is in use. "
				+ "A previous sign-in may still be waiting for its browser tab, or the Codex CLI "
				+ "is signing in. Close that tab or wait for it to time out, then try again.",
				inUse);
		}
		server.createContext("/auth/callback", exchange -> {
			Map<String, String> query = parseQuery(exchange.getRequestURI());
			String html;
			IOException failure = null;
			String code = null;
			if (!state.equals(query.get("state"))) {
				html = page("Sign-in failed", "State mismatch. Close this tab and retry in the terminal.");
				failure = new IOException("OAuth state mismatch");
			} else if (query.containsKey("code")) {
				html = page("Signed in", "xagent is connected to your ChatGPT account. You can close this tab.");
				code = query.get("code");
			} else {
				// error params are attacker-influenced -- always escape (opencode had an XSS here)
				String error = escapeHtml(query.getOrDefault("error_description",
					query.getOrDefault("error", "unknown error")));
				html = page("Sign-in failed", error);
				failure = new IOException("OAuth error: " + error);
			}
			// respond fully before completing the future: completion lets the
			// login thread stop the server, which would sever this connection
			byte[] body = html.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
			if (failure != null) {
				codeFuture.completeExceptionally(failure);
			} else {
				codeFuture.complete(code);
			}
		});
		server.start();
		try {
			onAuthUrl.accept(buildAuthorizeUrl(pkce, state));
			String code = codeFuture.get(LOGIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
			return exchangeCode(code, pkce.verifier(), redirectUri());
		} catch (TimeoutException e) {
			throw new IOException("Login timed out after " + LOGIN_TIMEOUT.toMinutes() + " minutes");
		} catch (ExecutionException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Login interrupted");
		} finally {
			server.stop(0);
		}
	}

	/** Exchange an authorization code for credentials. */
	public OAuthCredentials exchangeCode(String code, String verifier, String redirectUri) throws IOException {
		var params = new LinkedHashMap<String, String>();
		params.put("grant_type", "authorization_code");
		params.put("client_id", CLIENT_ID);
		params.put("code", code);
		params.put("code_verifier", verifier);
		params.put("redirect_uri", redirectUri);
		return tokenRequest(params);
	}

	/**
	 * Refresh credentials. The refresh token rotates: the returned
	 * credentials carry a NEW refresh token that must be persisted.
	 */
	public OAuthCredentials refresh(OAuthCredentials current) throws IOException {
		var params = new LinkedHashMap<String, String>();
		params.put("grant_type", "refresh_token");
		params.put("refresh_token", current.refreshToken());
		params.put("client_id", CLIENT_ID);
		OAuthCredentials refreshed = tokenRequest(params);
		// some refresh responses omit id_token claims; keep the known account id
		if (refreshed.accountId() == null && current.accountId() != null) {
			return new OAuthCredentials(refreshed.accessToken(), refreshed.refreshToken(),
				refreshed.expiresAtMs(), current.accountId());
		}
		return refreshed;
	}

	// --- device-code flow (headless) ---

	/** A pending device authorization. */
	public record DeviceAuth(String deviceAuthId, String userCode, long intervalSeconds, String verifyUrl) {}

	/** Start the device flow; show {@code userCode} and {@code verifyUrl} to the user. */
	public DeviceAuth requestDeviceCode() throws IOException {
		JsonNode response = postJson(issuer + "/api/accounts/deviceauth/usercode",
			MAPPER.createObjectNode().put("client_id", CLIENT_ID).toString());
		return new DeviceAuth(
			response.path("device_auth_id").asText(),
			response.path("user_code").asText(),
			Math.max(response.path("interval").asLong(5), 1),
			issuer + "/codex/device");
	}

	/**
	 * Poll until the user approves the device code, then exchange the
	 * server-issued authorization code. Blocks; honors the interval plus a
	 * safety margin.
	 */
	public OAuthCredentials pollDeviceToken(DeviceAuth device) throws IOException, InterruptedException {
		String body = MAPPER.createObjectNode()
			.put("device_auth_id", device.deviceAuthId())
			.put("user_code", device.userCode())
			.toString();
		long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			HttpResponse<String> response = send(HttpRequest.newBuilder()
				.uri(URI.create(issuer + "/api/accounts/deviceauth/token"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build());
			if (response.statusCode() == 200) {
				JsonNode parsed = MAPPER.readTree(response.body());
				// the server supplies both the code and the PKCE verifier here
				return exchangeCode(
					parsed.path("authorization_code").asText(),
					parsed.path("code_verifier").asText(),
					issuer + "/deviceauth/callback");
			}
			if (response.statusCode() != 403 && response.statusCode() != 404) {
				throw new IOException("Device auth failed: HTTP " + response.statusCode() + " " + response.body());
			}
			Thread.sleep((device.intervalSeconds() * 1000) + 3000);
		}
		throw new IOException("Device login timed out");
	}

	// --- internals ---

	private OAuthCredentials tokenRequest(Map<String, String> params) throws IOException {
		HttpResponse<String> response = send(HttpRequest.newBuilder()
			.uri(URI.create(issuer + "/oauth/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
			.build());
		if (response.statusCode() != 200) {
			throw new IOException("Token request failed: HTTP " + response.statusCode() + " " + response.body());
		}
		JsonNode parsed = MAPPER.readTree(response.body());
		String accessToken = parsed.path("access_token").asText(null);
		if (accessToken == null) {
			throw new IOException("Token response missing access_token");
		}
		long expiresIn = parsed.path("expires_in").asLong(DEFAULT_EXPIRES_IN_SECONDS);
		String accountId = extractAccountId(parsed.path("id_token").asText(null));
		if (accountId == null) {
			accountId = extractAccountId(accessToken);
		}
		return new OAuthCredentials(
			accessToken,
			parsed.path("refresh_token").asText(null),
			System.currentTimeMillis() + expiresIn * 1000,
			accountId);
	}

	/**
	 * Extract the ChatGPT account id from a JWT's claims. Checked in order:
	 * the "https://api.openai.com/auth" claim object, the top-level
	 * chatgpt_account_id, the first organization id.
	 */
	static String extractAccountId(String jwt) {
		if (jwt == null) return null;
		String[] parts = jwt.split("\\.");
		if (parts.length < 2) return null;
		try {
			byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
			JsonNode claims = MAPPER.readTree(payload);
			JsonNode auth = claims.path(JWT_AUTH_CLAIM);
			if (auth.path("chatgpt_account_id").isTextual()) {
				return auth.path("chatgpt_account_id").asText();
			}
			if (claims.path("chatgpt_account_id").isTextual()) {
				return claims.path("chatgpt_account_id").asText();
			}
			JsonNode orgs = claims.path("organizations");
			if (orgs.isArray() && !orgs.isEmpty() && orgs.get(0).path("id").isTextual()) {
				return orgs.get(0).path("id").asText();
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private JsonNode postJson(String url, String body) throws IOException {
		HttpResponse<String> response = send(HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build());
		if (response.statusCode() != 200) {
			throw new IOException("Request failed: HTTP " + response.statusCode() + " " + response.body());
		}
		return MAPPER.readTree(response.body());
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Request interrupted", e);
		}
	}

	private static String formEncode(Map<String, String> params) {
		var sb = new StringBuilder();
		params.forEach((key, value) -> {
			if (!sb.isEmpty()) sb.append('&');
			sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
		});
		return sb.toString();
	}

	private static Map<String, String> parseQuery(URI uri) {
		var result = new HashMap<String, String>();
		String query = uri.getRawQuery();
		if (query == null) return result;
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				result.put(
					URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return result;
	}

	private static String page(String title, String message) {
		return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + escapeHtml(title)
			+ "</title></head><body style=\"font-family: system-ui; max-width: 32rem; margin: 4rem auto;\">"
			+ "<h1>" + escapeHtml(title) + "</h1><p>" + message + "</p></body></html>";
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;");
	}
}
