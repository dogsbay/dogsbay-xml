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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Transcripts accumulated with no way to remove them: 237 files on the machine
 * that prompted this, the oldest six months old, none of them reachable through
 * the picker's fifteen-session list.
 */
class SessionDeletionTest {

    private static Path session(Path dir, String id, Duration age) throws Exception {
        Path file = dir.resolve(id + ".jsonl");
        Files.writeString(file, "{\"type\":\"header\"}\n");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
        return file;
    }

    @Test
    void deletesOneSession(@TempDir Path dir) throws Exception {
        Path file = session(dir, "abc123", Duration.ZERO);
        var manager = new SessionManager(dir);

        assertThat(manager.deleteSession("abc123")).isTrue();
        assertThat(file).doesNotExist();
        // Deleting what is already gone is not an error — the picker and a
        // retention sweep can race, and neither should fail for it.
        assertThat(manager.deleteSession("abc123")).isFalse();
    }

    @Test
    void refusesAnIdThatIsReallyAPath(@TempDir Path dir) throws Exception {
        Path outside = Files.createTempFile("not-a-session", ".jsonl");
        var manager = new SessionManager(dir);
        try {
            assertThatThrownBy(() -> manager.deleteSession("../" + outside.getFileName()))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void retentionDeletesOnlyWhatIsOldEnough(@TempDir Path dir) throws Exception {
        Path old = session(dir, "aaaaaaaa", Duration.ofDays(120));
        Path borderline = session(dir, "bbbbbbbb", Duration.ofDays(31));
        Path recent = session(dir, "cccccccc", Duration.ofDays(3));
        var manager = new SessionManager(dir);

        assertThat(manager.deleteSessionsOlderThan(30)).containsExactly("aaaaaaaa", "bbbbbbbb");
        assertThat(old).doesNotExist();
        assertThat(borderline).doesNotExist();
        assertThat(recent).exists();
        assertThat(manager.sessionCount()).isEqualTo(1);
    }

    @Test
    void retentionReturnsTheIdsOldestFirst(@TempDir Path dir) throws Exception {
        // Ids are random hex, so "oldest first" has to mean the file times, not
        // the names — which sorting the ids would silently give instead.
        session(dir, "zzzzzzzz", Duration.ofDays(300));
        session(dir, "aaaaaaaa", Duration.ofDays(100));
        var manager = new SessionManager(dir);

        assertThat(manager.deleteSessionsOlderThan(30)).containsExactly("zzzzzzzz", "aaaaaaaa");
    }

    @Test
    void aMissingSessionsDirectoryIsNotAnError(@TempDir Path dir) throws Exception {
        var manager = new SessionManager(dir.resolve("never-created"));

        assertThat(manager.sessionCount()).isZero();
        assertThat(manager.deleteSessionsOlderThan(30)).isEmpty();
        assertThat(manager.listSessions()).isEmpty();
    }

    @Test
    void zeroDaysKeepsEverything(@TempDir Path dir) throws Exception {
        session(dir, "aaaaaaaa", Duration.ofDays(3650));
        var manager = new SessionManager(dir);

        // The default. "Delete everything older than 0 days" must never be read
        // as "delete everything", which is the one mistake this cannot undo.
        assertThat(manager.deleteSessionsOlderThan(0)).isEmpty();
        assertThat(manager.deleteSessionsOlderThan(-1)).isEmpty();
        assertThat(manager.sessionCount()).isEqualTo(1);
    }

	// ── sessions that were never spoken in ──────────────────────────────

	@Test
	void aReservedSessionWritesNothingUntilSomethingIsSaid(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);

		String id = manager.reserveSession("/project", "openai-codex", "gpt-5.6-sol");
		assertThat(manager.sessionFile(id)).doesNotExist();
		assertThat(manager.sessionCount()).isZero();

		manager.appendMessage(id, new com.xagent.message.UserMessage("summarise the project"), null);

		// The header lands first, or the file cannot be read back.
		assertThat(manager.sessionFile(id)).exists();
		assertThat(manager.getHeader(id).id()).isEqualTo(id);
		assertThat(manager.loadMessages(id)).hasSize(1);
		assertThat(manager.listSessions()).extracting("id").containsExactly(id);
	}

	@Test
	void emptySessionsAreSweptAway(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String spoken = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(spoken, new com.xagent.message.UserMessage("hello"), null);
		manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.createSession("/project", "openai-codex", "gpt-5.6-sol");

		assertThat(manager.deleteEmptySessions()).isEqualTo(2);
		assertThat(manager.sessionCount()).isEqualTo(1);
		assertThat(manager.loadMessages(spoken)).hasSize(1);
	}

	@Test
	void aSessionSummarisedBeforeTheFixStillPreviewsWhatWasAsked(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage(
			"\n## Editor context\n- Mode: Agent\n- Active file: /x/y.dita"
			+ SessionManager.CONTEXT_MARKER + "summarise the project"), null);
		// What the old code wrote: the first 80 characters of the raw input,
		// which is the context block and stops before the marker.
		manager.appendSummary(id, " ## Editor context - Mode: Agent - Active file: /x/y.dita...");

		assertThat(manager.listSessions().get(0).preview()).isEqualTo("summarise the project");
	}

	@Test
	void thePreviewIsWhatTheUserAskedNotTheContextTheEditorAdded(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage(
			"## Editor context\n- Mode: Agent\n- Active file: /x/y.dita"
			+ SessionManager.CONTEXT_MARKER + "summarise the project"), null);

		// Previewing the injected block made every row read "## Editor context…",
		// so no session could be told from another in the picker.
		assertThat(manager.listSessions().get(0).preview()).isEqualTo("summarise the project");
	}

	// ── naming a session ────────────────────────────────────────────────

	@Test
	void aRenamedSessionKeepsItsNameInThePicker(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage("summarise the project"), null);
		manager.appendSummary(id, "summarise the project");

		manager.renameSession(id, "Audacity cleanup");

		assertThat(manager.listSessions().get(0).preview()).isEqualTo("Audacity cleanup");
		// The conversation itself is untouched by naming it.
		assertThat(manager.loadMessages(id)).hasSize(1);
	}

	@Test
	void aBlankNameRestoresTheAutomaticOne(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage(
			"## Editor context\n- Mode: Agent" + SessionManager.CONTEXT_MARKER
			+ "summarise the project"), null);
		manager.renameSession(id, "Audacity cleanup");

		manager.renameSession(id, "   ");

		// Back to the first thing asked — and without the editor context, which
		// is what the automatic name means.
		assertThat(manager.listSessions().get(0).preview()).isEqualTo("summarise the project");
	}

	// ── what the review found ───────────────────────────────────────────

	@Test
	void aNameGivenBeforeTheFirstPromptIsNotOverwrittenByIt(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.reserveSession("/project", "openai-codex", "gpt-5.6-sol");

		// Named before anything was said — the rename dialog is reachable on a
		// brand-new tab.
		manager.renameSession(id, "Audacity cleanup");
		assertThat(manager.hasName(id)).isTrue();

		// The first prompt names an unnamed session; this one is named.
		manager.appendMessage(id, new com.xagent.message.UserMessage("fix the build"), null);
		assertThat(manager.sessionTitle(id)).isEqualTo("Audacity cleanup");
		assertThat(manager.listSessions().get(0).preview()).isEqualTo("Audacity cleanup");
	}

	@Test
	void clearingTheNameOfAnUnspokenSessionIsNotAnError(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.reserveSession("/project", "openai-codex", "gpt-5.6-sol");

		// The dialog says blank restores the automatic name; on a session with
		// nothing in it there is no file to read, and that is not a failure.
		manager.renameSession(id, "   ");

		assertThat(manager.sessionFile(id)).doesNotExist();
		assertThat(manager.sessionTitle(id)).isEmpty();
	}

	@Test
	void anAppendRebuildsAFileThatWentMissing(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage("hello"), null);

		// Another window's sweep deletes it mid-conversation.
		java.nio.file.Files.delete(manager.sessionFile(id));
		manager.appendMessage(id, new com.xagent.message.UserMessage("still here?"), null);

		// The header comes back first, so the session is still listable and
		// resumable rather than a file whose first line is a message.
		assertThat(manager.getHeader(id).id()).isEqualTo(id);
		assertThat(manager.listSessions()).extracting("id").contains(id);
		assertThat(manager.loadMessages(id)).hasSize(1);
	}

	@Test
	void theNameSurvivesReadingItBackOneFileAtATime(@TempDir Path dir) throws Exception {
		var manager = new SessionManager(dir);
		String id = manager.createSession("/project", "openai-codex", "gpt-5.6-sol");
		manager.appendMessage(id, new com.xagent.message.UserMessage(
			"## Editor context" + SessionManager.CONTEXT_MARKER + "summarise the project"), null);

		// No summary yet: the name is the first thing asked, without the context.
		assertThat(manager.sessionTitle(id)).isEqualTo("summarise the project");
		assertThat(manager.hasName(id)).isFalse();

		manager.renameSession(id, "Project tour");
		assertThat(manager.sessionTitle(id)).isEqualTo("Project tour");

		// Cleared: back to the first thing asked.
		manager.renameSession(id, "");
		assertThat(manager.sessionTitle(id)).isEqualTo("summarise the project");
	}
}
