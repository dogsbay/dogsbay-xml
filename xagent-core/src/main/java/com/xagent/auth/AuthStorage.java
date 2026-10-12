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
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Credential store: {@code ~/.xagent/auth.json}, one entry per provider id.
 *
 * <pre>
 * {
 *   "openai-codex": {"type": "oauth", "access": "...", "refresh": "...",
 *                    "expires": 1718200000000, "accountId": "..."}
 * }
 * </pre>
 *
 * The file is written with owner-only permissions and updated atomically
 * (temp file + move). Refresh uses in-process single-flight plus an OS file
 * lock, because OAuth refresh tokens rotate: two concurrent refreshes with
 * the same token log the user out.
 */
public class AuthStorage {

	/** Refresh this long before actual expiry. */
	public static final long EXPIRY_SAFETY_WINDOW_MS = 60_000;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final Path file;
	private final Object refreshLock = new Object();

	public AuthStorage() {
		this(Path.of(System.getProperty("user.home"), ".xagent", "auth.json"));
	}

	/** @param file the auth.json path (injectable for tests) */
	public AuthStorage(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/**
	 * Optional process-wide secret backend. When set, the whole credential map
	 * is stored through it (e.g. an OS keychain) instead of {@code auth.json}.
	 * The CLI leaves this unset and keeps using the file; an embedder (the
	 * DogsBay editor) registers a keychain-backed implementation.
	 */
	public interface SecretBackend {
		Optional<String> read();
		void write(String json) throws IOException;
	}

	private static volatile SecretBackend backend;

	/** Register a process-wide secret backend (replaces file storage). */
	public static void useBackend(SecretBackend b) {
		backend = b;
	}

	/**
	 * One-time copy of an existing {@code auth.json} into the registered backend
	 * (no-op if no backend is set, or the backend already has data). The file is
	 * left in place so other tools (the CLI) keep working.
	 */
	public static void migrateFileToBackend() {
		SecretBackend b = backend;
		if (b == null || b.read().isPresent()) {
			return;
		}
		Path f = Path.of(System.getProperty("user.home"), ".xagent", "auth.json");
		try {
			if (Files.isRegularFile(f)) {
				b.write(Files.readString(f));
			}
		} catch (IOException ignored) {
			// best-effort migration
		}
	}

	/** Stored OAuth credentials for the provider, if any. */
	public synchronized Optional<OAuthCredentials> get(String providerId) {
		JsonNode entry = readAll().get(providerId);
		if (entry == null || !"oauth".equals(entry.path("type").asText())) {
			return Optional.empty();
		}
		return Optional.of(new OAuthCredentials(
			entry.path("access").asText(null),
			entry.path("refresh").asText(null),
			entry.path("expires").asLong(0),
			entry.path("accountId").asText(null)
		));
	}

	public synchronized void set(String providerId, OAuthCredentials credentials) throws IOException {
		updateFile(root -> {
			ObjectNode entry = root.putObject(providerId);
			entry.put("type", "oauth");
			entry.put("access", credentials.accessToken());
			entry.put("refresh", credentials.refreshToken());
			entry.put("expires", credentials.expiresAtMs());
			if (credentials.accountId() != null) {
				entry.put("accountId", credentials.accountId());
			}
			return root;
		});
	}

	public synchronized boolean remove(String providerId) throws IOException {
		var root = readAll();
		if (!root.has(providerId)) {
			return false;
		}
		updateFile(r -> {
			r.remove(providerId);
			return r;
		});
		return true;
	}

	/** Provider ids with stored entries, in insertion order. */
	public synchronized Map<String, String> list() {
		var result = new LinkedHashMap<String, String>();
		var fields = readAll().fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			result.put(entry.getKey(), entry.getValue().path("type").asText("unknown"));
		}
		return result;
	}

	/**
	 * Refreshes a token. Receives the current credentials, returns the new
	 * ones (with the rotated refresh token).
	 */
	public interface TokenRefresher {
		OAuthCredentials refresh(OAuthCredentials current) throws IOException;
	}

	/**
	 * Get credentials, refreshing (and persisting the rotation) if the
	 * access token is expired. Single-flight: concurrent callers share one
	 * refresh. Returns empty if the provider has no entry or refresh fails.
	 */
	public Optional<OAuthCredentials> getValid(String providerId, TokenRefresher refresher) {
		var current = get(providerId);
		if (current.isEmpty()) {
			return Optional.empty();
		}
		if (!current.get().isExpired(System.currentTimeMillis(), EXPIRY_SAFETY_WINDOW_MS)) {
			return current;
		}
		synchronized (refreshLock) {
			// re-check: another thread may have refreshed while we waited
			var reread = get(providerId);
			if (reread.isEmpty()) {
				return Optional.empty();
			}
			if (!reread.get().isExpired(System.currentTimeMillis(), EXPIRY_SAFETY_WINDOW_MS)) {
				return reread;
			}
			try {
				OAuthCredentials refreshed = refresher.refresh(reread.get());
				set(providerId, refreshed);
				return Optional.of(refreshed);
			} catch (IOException e) {
				return Optional.empty();
			}
		}
	}

	// --- file handling ---

	private ObjectNode readAll() {
		SecretBackend b = backend;
		if (b != null) {
			return b.read().map(AuthStorage::parseObject).orElseGet(MAPPER::createObjectNode);
		}
		if (!Files.isRegularFile(file)) {
			return MAPPER.createObjectNode();
		}
		try {
			JsonNode parsed = MAPPER.readTree(Files.readString(file));
			return parsed.isObject() ? (ObjectNode) parsed : MAPPER.createObjectNode();
		} catch (IOException e) {
			return MAPPER.createObjectNode();
		}
	}

	private static ObjectNode parseObject(String json) {
		try {
			JsonNode parsed = MAPPER.readTree(json);
			return parsed.isObject() ? (ObjectNode) parsed : MAPPER.createObjectNode();
		} catch (IOException e) {
			return MAPPER.createObjectNode();
		}
	}

	private void updateFile(UnaryOperator<ObjectNode> mutation) throws IOException {
		SecretBackend b = backend;
		if (b != null) {
			ObjectNode root = mutation.apply(readAll());
			b.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
			return;
		}
		Files.createDirectories(file.getParent());
		Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
		try (FileChannel channel = FileChannel.open(lockFile,
			StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock ignored = channel.lock()) {
			// re-read under the cross-process lock so concurrent agents compose
			ObjectNode root = mutation.apply(readAll());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
			restrictPermissions(temp);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
	}

	private static void restrictPermissions(Path path) {
		try {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException | IOException ignored) {
			// non-POSIX filesystem (Windows): rely on the user profile ACL
		}
	}
}
