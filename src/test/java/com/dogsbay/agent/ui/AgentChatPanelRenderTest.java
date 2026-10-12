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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.testkit.GoldenImages;
import com.xagent.event.AgentEvent;
import com.xagent.tool.AgentToolResult;

/**
 * Tier-2 render test (see docs-dev/testing-tiers.md): feed a scripted
 * {@link AgentEvent} stream into {@link AgentChatPanel} and verify it renders
 * the transcript offscreen — no agent, no network, no editor. Runs against the
 * panel exactly as the standalone {@code AgentApp} uses it.
 */
class AgentChatPanelRenderTest {

    @Test
    void rendersScriptedConversation() throws Exception {
        AtomicReference<AgentChatPanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = new AgentChatPanel(
                    java.util.List.of("anthropic", "openai", "ollama"),
                    new AgentChatPanel.Actions() {
                        public void submit(String text) { /* no-op */ }
                        public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { /* no-op */ }
                        public void login() { /* no-op */ }
                        public void command(String raw) { /* no-op */ }
                        public void sessions() { /* no-op */ }
                        public void abort() { /* no-op */ }
                    });
            panel.setModelLabel("gemini", "gemini-3.1-flash-lite-preview", "405559f2abcd");
            panel.setHint("No API key for openai. Pick a provider below (Ollama needs no key).");
            panel.renderTurn("You", "a restored question");
            panel.renderTurn("Agent", "a restored answer");
            panel.handle(new AgentEvent.MessageStart());
            panel.handle(new AgentEvent.MessageUpdate("I'll validate the topic for you."));
            panel.handle(new AgentEvent.ToolExecutionStart(
                    "t1", "editor_validate", "{\"file\":\"install.dita\"}"));
            panel.handle(new AgentEvent.ToolExecutionEnd(
                    "t1", "editor_validate", AgentToolResult.success("Valid — no errors")));
            panel.handle(new AgentEvent.MessageUpdate(" The document is valid."));
            panel.handle(new AgentEvent.UsageUpdate(1200, 890, 2090, 4, 0.0031));
            panel.handle(new AgentEvent.TurnEnd());
            ref.set(panel);
        });

        AgentChatPanel panel = ref.get();

        assertThat(panel.hintText()).contains("Ollama needs no key");
        assertThat(panel.modelLabelText()).contains("gemini").contains("session 405559f2");
        assertThat(panel.statusText()).contains("1200").contains("890").contains("$0.0031");

        String text = panel.transcriptText();
        assertThat(text).contains("Agent:");
        assertThat(text).contains("editor_validate");
        assertThat(text).contains("Valid");
        assertThat(text).contains("validate the topic");
        assertThat(text).contains("a restored question");   // renderTurn (resume)

        BufferedImage image = GoldenImages.render(panel, 700, 500);
        assertThat(image.getWidth()).isEqualTo(700);
        assertThat(GoldenImages.isBlank(image)).as("transcript should paint text").isFalse();
        assertThat(GoldenImages.contentPixelRatio(image, Color.WHITE))
                .as("rendered conversation density").isGreaterThan(0.002);
    }

    @Test
    void aNoteBeforeTheFirstChunkDoesNotLeaveAnEmptyHeader() throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = new AgentChatPanel(java.util.List.of("openai"), new AgentChatPanel.Actions() {
                public void submit(String t) { }
                public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { }
                public void login() { }
                public void command(String raw) { }
                public void sessions() { }
                public void abort() { }
            });
            panel.handle(new AgentEvent.MessageStart());
            panel.note("Signed in to ChatGPT.");
            panel.handle(new AgentEvent.MessageUpdate("All **good**."));
            panel.handle(new AgentEvent.MessageEnd(null));
            text.set(panel.transcriptText());
        });
        assertThat(text.get()).contains("• Signed in to ChatGPT.").contains("Agent: All good.");
        assertThat(text.get()).doesNotContain("Agent: \n").doesNotContain("Agent: \n\nAgent:");
    }

    @Test
    void droppedFilesBecomeMentionsInTheBuiltInBoxToo() throws Exception {
        AtomicReference<String[]> got = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = new AgentChatPanel(java.util.List.of("openai"), new AgentChatPanel.Actions() {
                public void submit(String t) { }
                public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { }
                public void login() { }
                public void command(String raw) { }
                public void sessions() { }
                public void abort() { }
                @Override public String mention(java.io.File f) {
                    return f.getName().endsWith(".dita") ? "@topics/" + f.getName() : null;
                }
            });
            panel.dropFiles(java.util.List.of(new java.io.File("/p/topics/a.dita"), new java.io.File("/tmp/x.txt")));
            got.set(new String[] {panel.inputText(), panel.transcriptText()});
        });
        assertThat(got.get()[0]).isEqualTo("@topics/a.dita ");
        assertThat(got.get()[1]).contains("Not attached (").contains("x.txt");
    }

    @Test
    void multiLinePromptsSendWholeAndCompletionsListTheBuiltInCommands() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        AtomicReference<String> cmd = new AtomicReference<>();
        AtomicReference<AgentChatPanel> ref = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger turns = new java.util.concurrent.atomic.AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = new AgentChatPanel(java.util.List.of("openai"), new AgentChatPanel.Actions() {
                public void submit(String t) { sent.set(t); }
                public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { }
                public void login() { }
                public void command(String raw) { cmd.set(raw); }
                public void sessions() { }
                public void abort() { }
            });
            panel.onTurnEnded(turns::incrementAndGet);
            panel.typeAndSend("first line\nsecond line");
            panel.handle(new AgentEvent.TurnEnd());
            panel.typeAndSend("/help");
            ref.set(panel);
        });
        assertThat(sent.get()).isEqualTo("first line\nsecond line");
        assertThat(ref.get().transcriptText()).contains("You: first line\nsecond line\n");
        assertThat(cmd.get()).isEqualTo("/help");
        assertThat(turns.get()).isEqualTo(1);
        assertThat(ref.get().completions("/re")).extracting(HostedChatPanel.Command::name)
                .containsExactly("revert", "rename", "resume");
        assertThat(ref.get().completions("/")).hasSize(AgentChatPanel.BUILTIN_COMMANDS.size());
        assertThat(ref.get().completions("/help now")).isEmpty();
    }

    @Test
    void textSentFromOutsideIsSubmittedOrParkedWhileBusy() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        AtomicReference<String[]> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = new AgentChatPanel(java.util.List.of("openai"), new AgentChatPanel.Actions() {
                public void submit(String t) { sent.set(t); }
                public void chooseProvider(com.dogsbay.agent.ProviderChoice choice) { }
                public void login() { }
                public void command(String raw) { }
                public void sessions() { }
                public void abort() { }
            });
            panel.submitText("validate topics/a.dita");
            String first = sent.get();
            panel.submitText("then fix it");   // busy now
            after.set(new String[] {first, sent.get(), panel.inputText(), panel.transcriptText()});
        });
        assertThat(after.get()[0]).isEqualTo("validate topics/a.dita");
        assertThat(after.get()[1]).isEqualTo("validate topics/a.dita");
        assertThat(after.get()[2]).isEqualTo("then fix it");
        assertThat(after.get()[3]).contains("The agent is busy; the selection is in the box");
    }
}
