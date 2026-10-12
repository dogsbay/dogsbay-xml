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
package com.xagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xagent.core.UsageStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OneShotFormatterTest {

	@Test
	void parsesFormats() {
		assertThat(OneShotFormatter.parseFormat(null)).isEqualTo(OneShotFormatter.Format.TEXT);
		assertThat(OneShotFormatter.parseFormat("text")).isEqualTo(OneShotFormatter.Format.TEXT);
		assertThat(OneShotFormatter.parseFormat("JSON")).isEqualTo(OneShotFormatter.Format.JSON);
		assertThat(OneShotFormatter.parseFormat("md")).isEqualTo(OneShotFormatter.Format.MARKDOWN);
		assertThat(OneShotFormatter.parseFormat("markdown")).isEqualTo(OneShotFormatter.Format.MARKDOWN);
		assertThat(OneShotFormatter.parseFormat("bogus")).isEqualTo(OneShotFormatter.Format.TEXT);
	}

	@Test
	void textFormatIsBareResponse() {
		String out = OneShotFormatter.format(OneShotFormatter.Format.TEXT, "the answer", null, null);
		assertThat(out).isEqualTo("the answer");
	}

	@Test
	void textFormatHandlesNullResponse() {
		String out = OneShotFormatter.format(OneShotFormatter.Format.TEXT, null, null, null);
		assertThat(out).isEmpty();
	}

	@Test
	void jsonFormatIsValidAndComplete() throws Exception {
		var usage = new UsageStats();
		usage.addUsage(100, 50, 150);
		String out = OneShotFormatter.format(OneShotFormatter.Format.JSON, "line1\n\"quoted\"", usage, "sess-1");

		var node = new ObjectMapper().readTree(out);
		assertThat(node.get("response").asText()).isEqualTo("line1\n\"quoted\"");
		assertThat(node.get("inputTokens").asLong()).isEqualTo(100);
		assertThat(node.get("outputTokens").asLong()).isEqualTo(50);
		assertThat(node.get("totalTokens").asLong()).isEqualTo(150);
		assertThat(node.get("sessionId").asText()).isEqualTo("sess-1");
		assertThat(node.has("estimatedCostUsd")).isTrue();
	}

	@Test
	void jsonFormatOmitsAbsentSessionAndUsage() throws Exception {
		String out = OneShotFormatter.format(OneShotFormatter.Format.JSON, "hi", null, null);
		var node = new ObjectMapper().readTree(out);
		assertThat(node.get("response").asText()).isEqualTo("hi");
		assertThat(node.has("sessionId")).isFalse();
		assertThat(node.has("totalTokens")).isFalse();
	}

	@Test
	void markdownFormatAppendsMetadataFooter() {
		var usage = new UsageStats();
		usage.addUsage(10, 5, 15);
		String out = OneShotFormatter.format(OneShotFormatter.Format.MARKDOWN, "body", usage, "sess-2");
		assertThat(out).startsWith("body");
		assertThat(out).contains("---");
		assertThat(out).contains("tokens: 15 (10 in / 5 out)");
		assertThat(out).contains("session: sess-2");
	}
}
