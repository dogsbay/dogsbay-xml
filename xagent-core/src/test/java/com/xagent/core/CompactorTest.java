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

import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactorTest {

	@Test
	void estimateTokensBasedOnCharCount() {
		var compactor = new Compactor(CompactionConfig.DEFAULT);
		var messages = new ArrayList<AgentMessage>();
		// 400 chars ~= 100 tokens
		messages.add(new UserMessage("x".repeat(400)));

		assertThat(compactor.estimateTokens(messages)).isEqualTo(100);
	}

	@Test
	void needsCompactionWhenOverLimit() {
		var config = new CompactionConfig(100, 20);
		var compactor = new Compactor(config);

		var messages = new ArrayList<AgentMessage>();
		// 500 tokens worth of chars = 2000 chars
		messages.add(new UserMessage("x".repeat(2000)));

		assertThat(compactor.needsCompaction(messages)).isTrue();
	}

	@Test
	void doesNotNeedCompactionWhenUnderLimit() {
		var config = new CompactionConfig(100, 20);
		var compactor = new Compactor(config);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));

		assertThat(compactor.needsCompaction(messages)).isFalse();
	}

	@Test
	void compactReplacesOlderMessagesWithSummary() {
		// maxContextTokens=25 tokens (100 chars), keepRecentTokens=5 tokens (20 chars)
		var config = new CompactionConfig(25, 5);
		var compactor = new Compactor(config);

		var messages = new ArrayList<AgentMessage>();
		// 3 messages totaling ~200 chars = ~50 tokens, well over limit of 25
		messages.add(new UserMessage("Tell me about the project " + "x".repeat(60)));
		messages.add(new AssistantMessage("The project is interesting " + "x".repeat(60),
			Instant.now(), "model", "provider"));
		messages.add(new UserMessage("What about the tests?"));

		assertThat(compactor.needsCompaction(messages)).isTrue();

		String summary = compactor.compact(messages);

		assertThat(summary).isNotNull();
		assertThat(summary).contains("Conversation summary");
		// First message should now be the summary
		assertThat(messages.getFirst().role()).isEqualTo("user");
		assertThat(((UserMessage) messages.getFirst()).content()).contains("Conversation summary");
	}

	@Test
	void compactReturnsNullWhenNotNeeded() {
		var config = new CompactionConfig(100_000, 20_000);
		var compactor = new Compactor(config);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));

		assertThat(compactor.compact(messages)).isNull();
	}

	@Test
	void summaryIncludesTopicsAndFiles() {
		var compactor = new Compactor(CompactionConfig.DEFAULT);

		var toolCall = ToolExecutionRequest.builder()
			.id("c1").name("read").arguments("{\"path\":\"src/Main.java\"}")
			.build();
		var writeCall = ToolExecutionRequest.builder()
			.id("c2").name("write").arguments("{\"path\":\"output.txt\"}")
			.build();

		var messages = List.<AgentMessage>of(
			new UserMessage("Read the main file"),
			new AssistantMessage("Reading...", Instant.now(), "m", "p",
				List.of(toolCall), null),
			new ToolResultMessage("c1", "read", "public class Main {}", false),
			new UserMessage("Now write something"),
			new AssistantMessage("Writing...", Instant.now(), "m", "p",
				List.of(writeCall), null),
			new ToolResultMessage("c2", "write", "OK", false)
		);

		String summary = compactor.buildSummary(messages);

		assertThat(summary).contains("Read the main file");
		assertThat(summary).contains("src/Main.java");
		assertThat(summary).contains("output.txt");
		assertThat(summary).contains("Files read");
		assertThat(summary).contains("Files modified");
	}

	@Test
	void findSplitPointKeepsRecentMessages() {
		var config = new CompactionConfig(50, 20);
		var compactor = new Compactor(config);

		var messages = new ArrayList<AgentMessage>();
		// Each message ~25 tokens (100 chars)
		messages.add(new UserMessage("a".repeat(100)));  // 0: old
		messages.add(new UserMessage("b".repeat(100)));  // 1: old
		messages.add(new UserMessage("c".repeat(100)));  // 2: recent (within keepRecentTokens)

		int split = compactor.findSplitPoint(messages);

		// Should split so that ~20 tokens worth of recent messages are kept
		assertThat(split).isGreaterThan(0);
		assertThat(split).isLessThan(messages.size());
	}

	@Test
	void disabledCompactionNeverTriggers() {
		var compactor = new Compactor(CompactionConfig.DISABLED);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("x".repeat(1_000_000)));

		assertThat(compactor.needsCompaction(messages)).isFalse();
	}
}
