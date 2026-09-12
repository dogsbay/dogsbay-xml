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
package com.xagent.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderFactoryTest {

	@Test
	void createOpenAiModel() {
		var config = ProviderConfig.resolve("openai", "gpt-4o", "sk-test-key", null);
		var model = ProviderFactory.create(config);

		assertThat(model).isNotNull();
	}

	@Test
	void createAnthropicModel() {
		var config = ProviderConfig.resolve("anthropic", "claude-sonnet-4-20250514", "sk-ant-test", null);
		var model = ProviderFactory.create(config);

		assertThat(model).isNotNull();
	}

	@Test
	void createOllamaModel() {
		var config = ProviderConfig.resolve("ollama", "llama3", null, null);
		var model = ProviderFactory.create(config);

		assertThat(model).isNotNull();
	}

	@Test
	void unknownProviderThrows() {
		var config = ProviderConfig.resolve("unknown", "model", "key", null);

		assertThatThrownBy(() -> ProviderFactory.create(config))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unknown provider: unknown");
	}

	@Test
	void openAiWithoutApiKeyThrows() {
		var config = ProviderConfig.resolve("openai", "gpt-4o", null, null);

		// This will throw only if OPENAI_API_KEY env var is not set
		if (System.getenv("OPENAI_API_KEY") == null) {
			assertThatThrownBy(() -> ProviderFactory.create(config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("API key required");
		}
	}

	@Test
	void anthropicWithoutApiKeyThrows() {
		var config = ProviderConfig.resolve("anthropic", "claude-sonnet-4-20250514", null, null);

		if (System.getenv("ANTHROPIC_API_KEY") == null) {
			assertThatThrownBy(() -> ProviderFactory.create(config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("API key required");
		}
	}
}
