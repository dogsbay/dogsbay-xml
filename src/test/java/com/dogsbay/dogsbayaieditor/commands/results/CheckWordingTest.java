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

package com.dogsbay.dogsbayaieditor.commands.results;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The sentence the lesson pages quote, and the one place it is written.
 *
 * <p>The deliverable names in it are the project's own, so a long project writes a long sentence. The seven names
 * below are the ones stage 26 of the tutorial builds; they are why the sentence counts instead of naming past three.
 */
class CheckWordingTest {

	private static final List<String> SEVEN = List.of("full", "beginner-mac", "beginner-windows",
			"podcaster-linux", "review", "install-variants", "collection");

	@Test
	void oneDeliverableIsNamed() {
		assertThat(CheckWording.linksVerdict(List.of("html5")))
				.isEqualTo("every link in the pages of html5 leads somewhere");
	}

	@Test
	void threeDeliverablesAreStillNamed() {
		assertThat(CheckWording.linksVerdict(List.of("full", "review", "collection")))
				.isEqualTo("every link in the pages of full, review, collection leads somewhere");
	}

	@Test
	void aLongProjectIsCountedRatherThanListed() {
		// Naming all seven ran to 134 characters of names inside one clause; the count is what the
		// tutorial team asked for after reading stage 26's verdict (2026-10-08).
		assertThat(CheckWording.linksVerdict(SEVEN))
				.isEqualTo("every link in the pages of 7 deliverables leads somewhere");
	}

	@Test
	void theCountIsTheRealNumberAndNotTheThreshold() {
		assertThat(CheckWording.linksVerdict(List.of("a", "b", "c", "d")))
				.isEqualTo("every link in the pages of 4 deliverables leads somewhere");
		assertThat(CheckWording.linksVerdict(SEVEN.subList(0, 5)))
				.contains("5 deliverables");
	}

	@Test
	void nothingReadDoesNotClaimALinkWasFollowed() {
		// A PDF-only project reaches the output stage with nothing to read. "every link ... leads
		// somewhere" would be a claim about links nobody looked at.
		assertThat(CheckWording.linksVerdict(List.of()))
				.isEqualTo("every deliverable wrote something");
	}

	@Test
	void theSentenceIsLeftForTheCallerToEnd() {
		assertThat(CheckWording.linksVerdict(List.of("html5"))).doesNotEndWith(".");
	}
}
