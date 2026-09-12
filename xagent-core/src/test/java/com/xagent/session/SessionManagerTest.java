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
package com.xagent.session;

import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerTest {

	@TempDir
	Path tempDir;

	private SessionManager manager;

	@BeforeEach
	void setUp() {
		manager = new SessionManager(tempDir);
	}

	@Test
	void createSessionWritesHeaderFile() throws IOException {
		String id = manager.createSession("/home/user/project", "openai", "gpt-5.4");

		assertThat(id).isNotBlank();
		Path file = manager.sessionFile(id);
		assertThat(Files.exists(file)).isTrue();

		var entries = manager.loadSession(id);
		assertThat(entries).hasSize(1);
		assertThat(entries.getFirst()).isInstanceOf(SessionEntry.Header.class);

		var header = (SessionEntry.Header) entries.getFirst();
		assertThat(header.id()).isEqualTo(id);
		assertThat(header.version()).isEqualTo(1);
		assertThat(header.cwd()).isEqualTo("/home/user/project");
		assertThat(header.provider()).isEqualTo("openai");
		assertThat(header.model()).isEqualTo("gpt-5.4");
	}

	@Test
	void appendAndLoadMessages() throws IOException {
		String id = manager.createSession("/tmp", "anthropic", "claude-sonnet-4-6-20250217");

		var userMsg = new UserMessage("Hello");
		manager.appendMessage(id, userMsg, null);

		var assistantMsg = new AssistantMessage("Hi there!", Instant.now(), "claude-sonnet-4-6-20250217", "anthropic");
		manager.appendMessage(id, assistantMsg, null);

		var entries = manager.loadSession(id);
		assertThat(entries).hasSize(3); // header + 2 messages

		assertThat(entries.get(1)).isInstanceOf(SessionEntry.Message.class);
		var msg1 = (SessionEntry.Message) entries.get(1);
		assertThat(msg1.role()).isEqualTo("user");
		assertThat(msg1.content()).isEqualTo("Hello");

		var msg2 = (SessionEntry.Message) entries.get(2);
		assertThat(msg2.role()).isEqualTo("assistant");
		assertThat(msg2.content()).isEqualTo("Hi there!");
	}

	@Test
	void appendToolResultMessage() throws IOException {
		String id = manager.createSession("/tmp", "openai", "gpt-5.4");

		var toolResult = new ToolResultMessage("call_1", "read", "file contents", false);
		manager.appendMessage(id, toolResult, null);

		var entries = manager.loadSession(id);
		assertThat(entries).hasSize(2);
		assertThat(entries.get(1)).isInstanceOf(SessionEntry.ToolResult.class);

		var tr = (SessionEntry.ToolResult) entries.get(1);
		assertThat(tr.toolCallId()).isEqualTo("call_1");
		assertThat(tr.toolName()).isEqualTo("read");
		assertThat(tr.content()).isEqualTo("file contents");
		assertThat(tr.isError()).isFalse();
	}

	@Test
	void loadMessagesReconstructsConversation() throws IOException {
		String id = manager.createSession("/tmp", "openai", "gpt-5.4");

		manager.appendMessage(id, new UserMessage("Hi"), null);
		manager.appendMessage(id, new AssistantMessage("Hello!", Instant.now(), "gpt-5.4", "openai"), null);
		manager.appendMessage(id,
			new ToolResultMessage("call_1", "read", "content", false), null);

		var messages = manager.loadMessages(id);
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0).role()).isEqualTo("user");
		assertThat(messages.get(1).role()).isEqualTo("assistant");
		assertThat(messages.get(2).role()).isEqualTo("toolResult");
	}

	@Test
	void listSessionsReturnsMostRecentFirst() throws IOException {
		String id1 = manager.createSession("/tmp", "openai", "gpt-5.4");
		manager.appendMessage(id1, new UserMessage("First session"), null);

		// Small delay to ensure different timestamps
		String id2 = manager.createSession("/tmp", "anthropic", "claude-sonnet-4-6-20250217");
		manager.appendMessage(id2, new UserMessage("Second session"), null);

		var sessions = manager.listSessions();
		assertThat(sessions).hasSize(2);
		assertThat(sessions.get(0).id()).isEqualTo(id2);
		assertThat(sessions.get(1).id()).isEqualTo(id1);
	}

	@Test
	void listSessionsShowsPreviewFromFirstUserMessage() throws IOException {
		String id = manager.createSession("/tmp", "openai", "gpt-5.4");
		manager.appendMessage(id, new UserMessage("Hello world"), null);

		var sessions = manager.listSessions();
		assertThat(sessions).hasSize(1);
		assertThat(sessions.getFirst().preview()).isEqualTo("Hello world");
	}

	@Test
	void listSessionsShowsSummaryOverUserMessage() throws IOException {
		String id = manager.createSession("/tmp", "openai", "gpt-5.4");
		manager.appendMessage(id, new UserMessage("Hello world"), null);
		manager.appendSummary(id, "Custom summary");

		var sessions = manager.listSessions();
		assertThat(sessions).hasSize(1);
		assertThat(sessions.getFirst().preview()).isEqualTo("Custom summary");
	}

	@Test
	void lastSessionIdReturnsNewest() throws IOException {
		manager.createSession("/tmp", "openai", "gpt-5.4");
		String id2 = manager.createSession("/tmp", "anthropic", "claude-sonnet-4-6-20250217");

		assertThat(manager.lastSessionId()).isEqualTo(id2);
	}

	@Test
	void lastSessionIdReturnsNullWhenEmpty() throws IOException {
		assertThat(manager.lastSessionId()).isNull();
	}

	@Test
	void loadNonexistentSessionThrows() {
		assertThatThrownBy(() -> manager.loadSession("nonexistent"))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("Session not found");
	}

	@Test
	void getHeaderReturnsSessionHeader() throws IOException {
		String id = manager.createSession("/home/user", "gemini", "gemini-3.1-flash-lite-preview");

		var header = manager.getHeader(id);
		assertThat(header.provider()).isEqualTo("gemini");
		assertThat(header.model()).isEqualTo("gemini-3.1-flash-lite-preview");
		assertThat(header.cwd()).isEqualTo("/home/user");
	}

	@Test
	void jsonRoundTrip() throws IOException {
		var header = new SessionEntry.Header("abc", 1, Instant.now(), "/tmp", "openai", "gpt-5.4");
		String json = manager.entryToJson(header);
		var parsed = manager.jsonToEntry(json);

		assertThat(parsed).isInstanceOf(SessionEntry.Header.class);
		var h = (SessionEntry.Header) parsed;
		assertThat(h.id()).isEqualTo("abc");
		assertThat(h.provider()).isEqualTo("openai");

		var msg = new SessionEntry.Message("def", "abc", Instant.now(), "user", "Hello", false);
		json = manager.entryToJson(msg);
		parsed = manager.jsonToEntry(json);

		assertThat(parsed).isInstanceOf(SessionEntry.Message.class);
		var m = (SessionEntry.Message) parsed;
		assertThat(m.parentId()).isEqualTo("abc");
		assertThat(m.content()).isEqualTo("Hello");
	}
}
