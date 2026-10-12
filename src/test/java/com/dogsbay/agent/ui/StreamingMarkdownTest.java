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

package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.xagent.event.AgentEvent;
import com.xagent.message.AssistantMessage;

/**
 * A reply used to arrive as raw markdown — literal {@code **bold**} and {@code
 * ##} headings — until the turn ended, then snap into style. It is now styled
 * as it streams, on completed lines only.
 */
class StreamingMarkdownTest {

    private static AgentChatPanel panel() {
        return new AgentChatPanel(List.of("openai-codex"), new AgentChatPanel.Actions() {
            @Override public void submit(String text) { }
            @Override public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { }
            @Override public void login() { }
            @Override public void command(String raw) { }
            @Override public void sessions() { }
            @Override public void abort() { }
        });
    }

    /** Feed a scripted stream on the EDT and return what the transcript shows. */
    private static String stream(String... chunks) throws Exception {
        StringBuilder shown = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.handle(new AgentEvent.MessageStart());
            for (String chunk : chunks) {
                p.handle(new AgentEvent.MessageUpdate(chunk));
            }
            shown.append(p.transcriptText());
        });
        return shown.toString();
    }

    @Test
    void acompletedLineIsStyledBeforeTheTurnEnds() throws Exception {
        String shown = stream("## Findings\n", "still writing");

        // The heading markers are gone: that line has been rendered already.
        assertThat(shown).contains("Findings");
        assertThat(shown).doesNotContain("## Findings");
        // The unfinished line is left alone, as raw text.
        assertThat(shown).endsWith("still writing");
    }

    @Test
    void aHalfWrittenLineIsNotStyled() throws Exception {
        // Styling mid-line would render "**bold" as prose and then restyle it
        // once the closing ** arrived — visible flicker on every reply.
        String shown = stream("Some **bol");

        assertThat(shown).contains("Some **bol");
    }

    @Test
    void anUnclosedCodeFenceWaitsForItsClosingOne() throws Exception {
        String open = stream("```java\n", "int x = 1;\n");
        assertThat(open).contains("```java");

        String closed = stream("```java\n", "int x = 1;\n", "```\n");
        assertThat(closed).doesNotContain("```java");
        assertThat(closed).contains("int x = 1;");
    }


    @Test
    void aStyledLineKeepsTheNewlineAfterIt() throws Exception {
        // The styler trims trailing newlines, so without putting them back the
        // next chunk lands on the end of the line just styled.
        String shown = stream("## Findings\n", "Second line here\n", "third");

        assertThat(shown).endsWith("Second line here\nthird");
    }

    @Test
    void aFenceMentionedInProseDoesNotStopTheStyling() throws Exception {
        // Counting ``` anywhere made the parity odd for the rest of a reply
        // that merely talked about fences — and silently stopped styling it.
        var panel = new java.util.concurrent.atomic.AtomicReference<AgentChatPanel>();
        StringBuilder shown = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(panel());
            panel.get().handle(new AgentEvent.MessageStart());
            panel.get().handle(new AgentEvent.MessageUpdate("Use ``` to open a fence.\n"));
        });
        Thread.sleep(200);   // past the throttle, so the next line can restyle
        SwingUtilities.invokeAndWait(() -> {
            panel.get().handle(new AgentEvent.MessageUpdate("## Then this\n"));
            shown.append(panel.get().transcriptText());
        });
        // The second line reached a restyle: the mention did not latch the
        // fence open for the rest of the reply.
        assertThat(shown.toString()).doesNotContain("## Then this");
        assertThat(AgentChatPanel.openFence("```java\nx\n")).isTrue();
        assertThat(AgentChatPanel.openFence("```java\nx\n```\n")).isFalse();
        assertThat(AgentChatPanel.openFence("Use ``` in prose\n")).isFalse();
    }

    @Test
    void aToolLineBetweenChunksIsNotSwallowed() throws Exception {
        StringBuilder shown = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.handle(new AgentEvent.MessageStart());
            p.handle(new AgentEvent.MessageUpdate("## Head\n"));
            // Appended past the message's end; a later restyle of that span
            // would delete it, so the reply has to be closed first.
            p.handle(new AgentEvent.ToolExecutionStart("id1", "read_file", "{}"));
            p.handle(new AgentEvent.MessageUpdate("**after tool**\n"));
            shown.append(p.transcriptText());
        });

        assertThat(shown.toString()).contains("read_file");
        assertThat(shown.toString()).contains("after tool");
    }
    @Test
    void theWholeReplyIsStyledWhenTheTurnEnds() throws Exception {
        StringBuilder shown = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.handle(new AgentEvent.MessageStart());
            p.handle(new AgentEvent.MessageUpdate("**done**"));
            p.handle(new AgentEvent.MessageEnd(
                new AssistantMessage("**done**", java.time.Instant.now(), null, null)));
            shown.append(p.transcriptText());
        });

        // The last line never ends with a newline, so the final pass is what
        // styles it — the streaming passes must not have consumed the text.
        assertThat(shown.toString()).contains("done").doesNotContain("**done**");
    }
}
