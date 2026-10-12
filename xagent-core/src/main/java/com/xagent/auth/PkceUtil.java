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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (RFC 7636) helpers: code verifier generation and S256 challenge.
 */
public final class PkceUtil {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

	private PkceUtil() {}

	/** A verifier and its S256 challenge. */
	public record Pkce(String verifier, String challenge) {}

	public static Pkce generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String verifier = BASE64_URL.encodeToString(bytes);
		return new Pkce(verifier, challengeFor(verifier));
	}

	static String challengeFor(String verifier) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return BASE64_URL.encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	/** Random URL-safe state parameter. */
	public static String randomState() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return BASE64_URL.encodeToString(bytes);
	}
}
