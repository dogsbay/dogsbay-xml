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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStorageTest {

	@TempDir
	Path tempDir;

	private AuthStorage storage() {
		return new AuthStorage(tempDir.resolve(".xagent").resolve("auth.json"));
	}

	private static OAuthCredentials creds(String access, long expiresAt) {
		return new OAuthCredentials(access, "refresh-1", expiresAt, "acct-1");
	}

	@Test
	void roundTripsCredentials() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("access-1", 123456789L));

		var loaded = storage.get("openai-codex");

		assertThat(loaded).isPresent();
		assertThat(loaded.get().accessToken()).isEqualTo("access-1");
		assertThat(loaded.get().refreshToken()).isEqualTo("refresh-1");
		assertThat(loaded.get().expiresAtMs()).isEqualTo(123456789L);
		assertThat(loaded.get().accountId()).isEqualTo("acct-1");
	}

	@Test
	void missingProviderIsEmpty() {
		assertThat(storage().get("nope")).isEmpty();
	}

	@Test
	void removeDeletesEntry() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("a", 1));

		assertThat(storage.remove("openai-codex")).isTrue();
		assertThat(storage.get("openai-codex")).isEmpty();
		assertThat(storage.remove("openai-codex")).isFalse();
	}

	@Test
	void listShowsEntriesWithTypes() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("a", 1));

		assertThat(storage.list()).containsEntry("openai-codex", "oauth");
	}

	@Test
	void fileIsOwnerOnly() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("a", 1));

		// Windows file systems have no POSIX modes; AuthStorage skips them there too.
		org.junit.jupiter.api.Assumptions.assumeTrue(
			storage.file().getFileSystem().supportedFileAttributeViews().contains("posix"),
			"POSIX permissions not supported on this file system");
		var permissions = Files.getPosixFilePermissions(storage.file());
		assertThat(permissions).containsExactlyInAnyOrder(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
	}

	@Test
	void corruptedFileBehavesAsEmpty() throws IOException {
		var storage = storage();
		Files.createDirectories(storage.file().getParent());
		Files.writeString(storage.file(), "not json at all");

		assertThat(storage.get("openai-codex")).isEmpty();
		// and recovers on write
		storage.set("openai-codex", creds("a", 1));
		assertThat(storage.get("openai-codex")).isPresent();
	}

	@Test
	void getValidReturnsUnexpiredWithoutRefreshing() throws IOException {
		var storage = storage();
		long future = System.currentTimeMillis() + 3_600_000;
		storage.set("openai-codex", creds("fresh", future));
		var refreshes = new AtomicInteger();

		var result = storage.getValid("openai-codex", current -> {
			refreshes.incrementAndGet();
			return current;
		});

		assertThat(result).isPresent();
		assertThat(result.get().accessToken()).isEqualTo("fresh");
		assertThat(refreshes.get()).isZero();
	}

	@Test
	void getValidRefreshesExpiredAndPersistsRotation() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("stale", System.currentTimeMillis() - 1000));

		var result = storage.getValid("openai-codex", current ->
			new OAuthCredentials("new-access", "rotated-refresh",
				System.currentTimeMillis() + 3_600_000, current.accountId()));

		assertThat(result).isPresent();
		assertThat(result.get().accessToken()).isEqualTo("new-access");
		// the rotated refresh token must survive a reload from disk
		var reloaded = storage.get("openai-codex");
		assertThat(reloaded.get().refreshToken()).isEqualTo("rotated-refresh");
	}

	@Test
	void getValidReturnsEmptyWhenRefreshFails() throws IOException {
		var storage = storage();
		storage.set("openai-codex", creds("stale", System.currentTimeMillis() - 1000));

		var result = storage.getValid("openai-codex", current -> {
			throw new IOException("refresh rejected");
		});

		assertThat(result).isEmpty();
	}

	@Test
	void getValidIsEmptyForUnknownProvider() {
		assertThat(storage().getValid("nope", current -> current)).isEmpty();
	}
}
