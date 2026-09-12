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

/**
 * An empty transcript under a provider banner reads as a conversation already
 * under way with nothing in it — which is what it looked like, since the
 * session id was shown before the session existed.
 */
class AgentEmptyStateTest {

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

    @Test
    void aPanelNobodyHasSpokenToSaysSo() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> shown.set(emptyStateText(panel())));

        assertThat(shown.get()).contains("Nothing asked yet");
        assertThat(shown.get()).contains("Ask about the project");
    }

    /** The labels of the empty-state card, which is shown in place of the transcript. */
    private static String emptyStateText(java.awt.Container panel) {
        StringBuilder text = new StringBuilder();
        collect(panel, text);
        return text.toString();
    }

    private static void collect(java.awt.Container from, StringBuilder into) {
        for (java.awt.Component c : from.getComponents()) {
            if (c instanceof javax.swing.JLabel label && label.getText() != null) {
                into.append(label.getText()).append('\n');
            }
            if (c instanceof javax.swing.JButton button && button.isVisible()) {
                into.append(button.getText()).append('\n');
            }
            if (c instanceof java.awt.Container inner) {
                collect(inner, into);
            }
        }
    }

    @Test
    void itGoesWhenTheConversationStarts() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.handle(new AgentEvent.MessageStart());
            p.handle(new AgentEvent.MessageUpdate("Working on it.\n"));
            shown.set(p.transcriptText());
        });

        assertThat(shown.get()).contains("Working on it");
    }

    @Test
    void aResumedConversationReplacesIt() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.renderTurn("You", "fix the broken conrefs");
            shown.set(p.transcriptText());
        });

        assertThat(shown.get()).contains("fix the broken conrefs");
    }

    @Test
    void aNewSessionBringsItBack() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.handle(new AgentEvent.MessageStart());
            p.handle(new AgentEvent.MessageUpdate("Working on it.\n"));
            p.clearTranscript();
            shown.set(emptyStateText(p));
        });

        assertThat(shown.get()).contains("Nothing asked yet");
    }

    @Test
    void aFirstRunSaysHowToGetAnAgentAtAll() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.showEmptyState(false);   // no key, no sign-in: a fresh install
            shown.set(emptyStateText(p));
        });

        // A button, not a command someone has to be told about.
        assertThat(shown.get()).contains("No provider configured");
        assertThat(shown.get()).contains("Configure provider");
        assertThat(shown.get()).doesNotContain("Nothing asked yet");
    }

    @Test
    void signingInReplacesTheCardRatherThanFreezingIt() throws Exception {
        var cardBefore = new java.util.concurrent.atomic.AtomicBoolean();
        var cardAfter = new java.util.concurrent.atomic.AtomicBoolean();
        var transcript = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.showEmptyState(false);              // a fresh install, no provider
            cardBefore.set(p.emptyStateShowing());

            p.note("Signed in to ChatGPT.");      // what onSignedIn does
            cardAfter.set(p.emptyStateShowing());
            transcript.set(p.transcriptText());
        });

        // The note used to land behind the card, which then refused to redraw
        // over a non-empty transcript: "No provider configured" stayed up until
        // the next question was asked.
        assertThat(cardBefore.get()).isTrue();
        assertThat(cardAfter.get()).isFalse();
        assertThat(transcript.get()).contains("Signed in to ChatGPT.");
    }

    @Test
    void theCardDoesNotComeBackOverANote() throws Exception {
        var returned = new java.util.concurrent.atomic.AtomicBoolean(true);
        var showing = new java.util.concurrent.atomic.AtomicBoolean(true);
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.note("Signed in to ChatGPT.");
            returned.set(p.showEmptyState(true));   // refreshStatus, right after
            showing.set(p.emptyStateShowing());
        });

        assertThat(returned.get()).isFalse();
        assertThat(showing.get()).isFalse();
    }

    @Test
    void theCardReportsWhetherItTookTheFloor() throws Exception {
        var onEmpty = new java.util.concurrent.atomic.AtomicBoolean();
        var onConversation = new java.util.concurrent.atomic.AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            onEmpty.set(p.showEmptyState(false));

            p.renderTurn("You", "fix the broken conrefs");
            onConversation.set(p.showEmptyState(false));
        });

        // The controller drops the duplicate auth banner only when the card is
        // up to carry the message itself.
        assertThat(onEmpty.get()).isTrue();
        assertThat(onConversation.get()).isFalse();
    }

    @Test
    void signingInSwapsItForTheQuestionPrompt() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.showEmptyState(false);
            p.showEmptyState(true);   // what refreshStatus does after a sign-in
            shown.set(emptyStateText(p));
        });

        assertThat(shown.get()).contains("Nothing asked yet");
        assertThat(shown.get()).doesNotContain("No provider configured");
    }

    @Test
    void aConversationIsNeverWrittenOver() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.renderTurn("You", "fix the broken conrefs");
            // refreshStatus runs on every provider change, including mid-session.
            p.showEmptyState(false);
            shown.set(p.transcriptText());
        });

        assertThat(shown.get()).contains("fix the broken conrefs");
    }


    @Test
    void theBannerNamesNobodyWithoutCredentials() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            // What the controller does when nothing can answer yet.
            p.setModelLabel(null, null, null);
            shown.set(p.modelLabelText());
        });

        // Naming a provider nothing can reach is a promise the panel cannot keep.
        assertThat(shown.get().strip()).isEmpty();
    }

    @Test
    void theBannerNamesTheProviderOnceSignedIn() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            // Signed in, nothing asked yet: no session file, so no id.
            p.setModelLabel("openai-codex", "gpt-5.6-sol", null);
            shown.set(p.modelLabelText());
        });

        // The confirmation that the sign-in took. Only the id waits for a session.
        assertThat(shown.get()).contains("openai-codex").contains("gpt-5.6-sol");
        assertThat(shown.get()).doesNotContain("session");
    }

    @Test
    void theBannerNamesThemOnceItHasStarted() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel p = panel();
            p.setModelLabel("openai-codex", "gpt-5.6-sol", "09e6c1d4");
            shown.set(p.modelLabelText());
        });

        assertThat(shown.get()).contains("openai-codex").contains("gpt-5.6-sol").contains("09e6c1d4");
    }
}
