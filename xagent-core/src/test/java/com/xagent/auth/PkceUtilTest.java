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

import static org.assertj.core.api.Assertions.assertThat;

class PkceUtilTest {

	@Test
	void challengeMatchesRfc7636TestVector() {
		// RFC 7636 appendix B
		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
		assertThat(PkceUtil.challengeFor(verifier))
			.isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
	}

	@Test
	void generatesUrlSafeVerifierAndChallenge() {
		var pkce = PkceUtil.generate();
		assertThat(pkce.verifier()).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(pkce.challenge()).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(pkce.challenge()).isEqualTo(PkceUtil.challengeFor(pkce.verifier()));
	}

	@Test
	void generatesDistinctValues() {
		assertThat(PkceUtil.generate().verifier()).isNotEqualTo(PkceUtil.generate().verifier());
		assertThat(PkceUtil.randomState()).isNotEqualTo(PkceUtil.randomState());
	}
}
