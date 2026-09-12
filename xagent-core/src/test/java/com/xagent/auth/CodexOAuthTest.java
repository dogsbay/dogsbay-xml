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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodexOAuthTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private HttpServer issuer;
	private String issuerUrl;
	private final AtomicReference<Map<String, String>> lastTokenRequest = new AtomicReference<>();
	private final AtomicReference<String> tokenResponse = new AtomicReference<>();
	private final AtomicInteger tokenStatus = new AtomicInteger(200);

	@BeforeEach
	void startIssuer() throws IOException {
		issuer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		issuer.createContext("/oauth/token", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			lastTokenRequest.set(parseForm(body));
			byte[] response = tokenResponse.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(tokenStatus.get(), response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		issuer.start();
		issuerUrl = "http://localhost:" + issuer.getAddress().getPort();
		tokenResponse.set(tokenJson("access-1", "refresh-1", jwt(Map.of(
			"https://api.openai.com/auth", Map.of("chatgpt_account_id", "acct-42")))));
		tokenStatus.set(200);
	}

	@AfterEach
	void stopIssuer() {
		issuer.stop(0);
	}

	private static String tokenJson(String access, String refresh, String idToken) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("access_token", access);
		node.put("refresh_token", refresh);
		node.put("expires_in", 3600);
		if (idToken != null) node.put("id_token", idToken);
		return node.toString();
	}

	/** Build an unsigned JWT with the given claims map. */
	private static String jwt(Map<String, Object> claims) {
		var encoder = Base64.getUrlEncoder().withoutPadding();
		try {
			String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
			String payload = encoder.encodeToString(MAPPER.writeValueAsBytes(claims));
			return header + "." + payload + ".sig";
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Map<String, String> parseForm(String body) {
		var result = new HashMap<String, String>();
		for (String pair : body.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				result.put(
					URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return result;
	}

	// --- tests ---

	@Test
	void authorizeUrlCarriesRequiredParams() {
		var oauth = new CodexOAuth(issuerUrl, 19999);
		var pkce = PkceUtil.generate();

		String url = oauth.buildAuthorizeUrl(pkce, "state-1");

		assertThat(url).startsWith(issuerUrl + "/oauth/authorize?");
		assertThat(url).contains("client_id=" + CodexOAuth.CLIENT_ID);
		assertThat(url).contains("code_challenge_method=S256");
		assertThat(url).contains("code_challenge=" + pkce.challenge());
		assertThat(url).contains("codex_cli_simplified_flow=true");
		assertThat(url).contains("id_token_add_organizations=true");
		assertThat(url).contains("state=state-1");
		assertThat(url).contains("originator=xagent");
		assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A19999%2Fauth%2Fcallback");
	}

	@Test
	void exchangeCodeSendsFormAndExtractsAccountId() throws IOException {
		var oauth = new CodexOAuth(issuerUrl, 19999);

		var credentials = oauth.exchangeCode("code-1", "verifier-1", "http://localhost:19999/auth/callback");

		assertThat(credentials.accessToken()).isEqualTo("access-1");
		assertThat(credentials.refreshToken()).isEqualTo("refresh-1");
		assertThat(credentials.accountId()).isEqualTo("acct-42");
		assertThat(credentials.expiresAtMs()).isGreaterThan(System.currentTimeMillis());

		var form = lastTokenRequest.get();
		assertThat(form).containsEntry("grant_type", "authorization_code");
		assertThat(form).containsEntry("client_id", CodexOAuth.CLIENT_ID);
		assertThat(form).containsEntry("code", "code-1");
		assertThat(form).containsEntry("code_verifier", "verifier-1");
		assertThat(form).containsEntry("redirect_uri", "http://localhost:19999/auth/callback");
	}

	@Test
	void refreshRotatesTokenAndPreservesAccountId() throws IOException {
		var oauth = new CodexOAuth(issuerUrl, 19999);
		// refresh responses often omit id_token -- account id must survive
		tokenResponse.set(tokenJson("access-2", "refresh-2", null));
		var current = new OAuthCredentials("access-1", "refresh-1", 0, "acct-42");

		var refreshed = oauth.refresh(current);

		assertThat(refreshed.accessToken()).isEqualTo("access-2");
		assertThat(refreshed.refreshToken()).isEqualTo("refresh-2");
		assertThat(refreshed.accountId()).isEqualTo("acct-42");
		assertThat(lastTokenRequest.get()).containsEntry("grant_type", "refresh_token");
		assertThat(lastTokenRequest.get()).containsEntry("refresh_token", "refresh-1");
	}

	@Test
	void tokenErrorsRaiseIoException() {
		var oauth = new CodexOAuth(issuerUrl, 19999);
		tokenStatus.set(400);
		tokenResponse.set("{\"error\": \"invalid_grant\"}");

		assertThatThrownBy(() -> oauth.exchangeCode("bad", "v", "http://localhost/cb"))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("400");
	}

	@Test
	void extractAccountIdChecksClaimLocations() {
		assertThat(CodexOAuth.extractAccountId(jwt(Map.of(
			"https://api.openai.com/auth", Map.of("chatgpt_account_id", "a1"))))).isEqualTo("a1");
		assertThat(CodexOAuth.extractAccountId(jwt(Map.of(
			"chatgpt_account_id", "a2")))).isEqualTo("a2");
		assertThat(CodexOAuth.extractAccountId(jwt(Map.of(
			"organizations", java.util.List.of(Map.of("id", "org-1")))))).isEqualTo("org-1");
		assertThat(CodexOAuth.extractAccountId(jwt(Map.of("sub", "x")))).isNull();
		assertThat(CodexOAuth.extractAccountId("not-a-jwt")).isNull();
		assertThat(CodexOAuth.extractAccountId(null)).isNull();
	}

	@Test
	void browserLoginRoundTrip() throws Exception {
		int callbackPort = freePort();
		var oauth = new CodexOAuth(issuerUrl, callbackPort);
		var authUrl = new CompletableFuture<String>();

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var login = executor.submit(() -> oauth.loginViaBrowser(authUrl::complete));

			// simulate the browser redirect back with the state from the auth URL
			String url = authUrl.get(5, TimeUnit.SECONDS);
			String state = parseForm(URI.create(url).getRawQuery().replace("&", "&")).get("state");
			var http = HttpClient.newHttpClient();
			HttpResponse<String> callback = http.send(HttpRequest.newBuilder()
					.uri(URI.create("http://localhost:" + callbackPort
						+ "/auth/callback?code=code-9&state=" + state))
					.build(),
				HttpResponse.BodyHandlers.ofString());

			assertThat(callback.statusCode()).isEqualTo(200);
			assertThat(callback.body()).contains("Signed in");
			var credentials = login.get(5, TimeUnit.SECONDS);
			assertThat(credentials.accessToken()).isEqualTo("access-1");
			assertThat(lastTokenRequest.get()).containsEntry("code", "code-9");
		}
	}

	@Test
	void browserLoginRejectsStateMismatch() throws Exception {
		int callbackPort = freePort();
		var oauth = new CodexOAuth(issuerUrl, callbackPort);
		var authUrl = new CompletableFuture<String>();

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var login = executor.submit(() -> oauth.loginViaBrowser(authUrl::complete));
			authUrl.get(5, TimeUnit.SECONDS);

			var http = HttpClient.newHttpClient();
			http.send(HttpRequest.newBuilder()
					.uri(URI.create("http://localhost:" + callbackPort
						+ "/auth/callback?code=code-9&state=WRONG"))
					.build(),
				HttpResponse.BodyHandlers.ofString());

			assertThatThrownBy(() -> login.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(IOException.class)
				.hasMessageContaining("state mismatch");
		}
	}

	@Test
	void deviceFlowPollsUntilApproved() throws Exception {
		var polls = new AtomicInteger();
		issuer.createContext("/api/accounts/deviceauth/usercode", exchange -> {
			byte[] response = "{\"device_auth_id\":\"dev-1\",\"user_code\":\"ABCD-1234\",\"interval\":0}"
				.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		issuer.createContext("/api/accounts/deviceauth/token", exchange -> {
			byte[] response;
			int status;
			if (polls.incrementAndGet() < 2) {
				status = 403;
				response = "{}".getBytes(StandardCharsets.UTF_8);
			} else {
				status = 200;
				response = "{\"authorization_code\":\"dev-code\",\"code_verifier\":\"dev-verifier\"}"
					.getBytes(StandardCharsets.UTF_8);
			}
			exchange.sendResponseHeaders(status, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		var oauth = new CodexOAuth(issuerUrl, 19999);

		var device = oauth.requestDeviceCode();
		assertThat(device.userCode()).isEqualTo("ABCD-1234");
		assertThat(device.verifyUrl()).contains("/codex/device");

		var credentials = oauth.pollDeviceToken(device);

		assertThat(credentials.accessToken()).isEqualTo("access-1");
		assertThat(polls.get()).isEqualTo(2);
		assertThat(lastTokenRequest.get()).containsEntry("code", "dev-code");
		assertThat(lastTokenRequest.get()).containsEntry("code_verifier", "dev-verifier");
	}

	private static int freePort() throws IOException {
		try (var socket = new java.net.ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
