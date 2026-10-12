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
package com.xagent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

	@Test
	void redactsOpenAiKeyShape() {
		String out = SecretMasker.mask("token is sk-abcdEFGH1234567890ijklmnop done", List.of());
		assertThat(out).doesNotContain("sk-abcdEFGH1234567890ijklmnop");
		assertThat(out).contains(SecretMasker.REDACTED);
	}

	@Test
	void redactsAnthropicGeminiGithubAwsAndJwt() {
		assertThat(SecretMasker.mask("sk-ant-abcdefghijklmnop1234567890", List.of())).contains(SecretMasker.REDACTED);
		assertThat(SecretMasker.mask("AIzaSyABCDEFGHIJKLMNOPQRSTUVWXYZ012345", List.of())).contains(SecretMasker.REDACTED);
		assertThat(SecretMasker.mask("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", List.of())).contains(SecretMasker.REDACTED);
		assertThat(SecretMasker.mask("AKIAABCDEFGHIJKLMNOP", List.of())).contains(SecretMasker.REDACTED);
		assertThat(SecretMasker.mask(
			"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r",
			List.of())).contains(SecretMasker.REDACTED);
	}

	@Test
	void redactsPrivateKeyBlock() {
		String pem = "-----BEGIN PRIVATE KEY-----\nMIIEv...lines...\n-----END PRIVATE KEY-----";
		assertThat(SecretMasker.mask("key:\n" + pem + "\nend", List.of())).doesNotContain("MIIEv");
	}

	@Test
	void redactsExactEnvSecretValues() {
		String secret = "super-secret-value-12345";
		String out = SecretMasker.mask("config: API_KEY=" + secret, List.of(secret));
		assertThat(out).isEqualTo("config: API_KEY=" + SecretMasker.REDACTED);
	}

	@Test
	void leavesOrdinaryTextUntouched() {
		String text = "Validated 12 files; 0 errors. See docs/install.md for details.";
		assertThat(SecretMasker.mask(text, List.of())).isEqualTo(text);
	}

	@Test
	void shortExactSecretsAreNotRedacted() {
		// avoid over-redacting trivial values
		assertThat(SecretMasker.mask("the key is abc", List.of("abc"))).isEqualTo("the key is abc");
	}

	@Test
	void handlesNullAndEmpty() {
		assertThat(SecretMasker.mask(null, List.of())).isNull();
		assertThat(SecretMasker.mask("", List.of())).isEmpty();
	}
}
