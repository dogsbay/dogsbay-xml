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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Asking a question left the caret nowhere: the box is disabled while the
 * agent works, which takes the focus off it, and nothing gave it back — so the
 * next question needed a click first.
 */
@Tag("ui")
class ChatFocusTest {

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

    /** A panel in a shown window, so isShowing() is true as it is in the editor. */
    private static void onShownPanel(java.util.function.Consumer<AgentChatPanel> body) throws Exception {
        var frame = new java.util.concurrent.atomic.AtomicReference<JFrame>();
        var panel = new java.util.concurrent.atomic.AtomicReference<AgentChatPanel>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                panel.set(panel());
                JFrame f = new JFrame("test");
                f.add(panel.get());
                f.pack();
                f.setVisible(true);
                frame.set(f);
            });
            SwingUtilities.invokeAndWait(() -> body.accept(panel.get()));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame.get() != null) {
                    frame.get().dispose();
                }
            });
        }
    }

    @Test
    void theBoxTakesTheFocusBackWhenNothingElseHasIt() throws Exception {
        onShownPanel(panel -> {
            // Disabling the box on submit is what drops the focus, so by the
            // time the turn ends nothing holds it.
            assertThat(panel.shouldTakeFocus(null)).isTrue();
        });
    }

    @Test
    void theBoxTakesTheFocusBackFromTheTranscript() throws Exception {
        onShownPanel(panel -> {
            // Clicking the transcript to read it is not a decision to type there.
            java.awt.Component transcript = firstOfType(panel, javax.swing.JTextPane.class);
            assertThat(transcript).isNotNull();
            assertThat(panel.shouldTakeFocus(transcript)).isTrue();
        });
    }

    @Test
    void theBoxLeavesTheFocusWhereTheReaderPutIt() throws Exception {
        onShownPanel(panel -> {
            // The reader moved to the document while the agent worked; that is
            // where they meant to be, and taking it back would be rude.
            assertThat(panel.shouldTakeFocus(new JTextArea())).isFalse();
            assertThat(panel.shouldTakeFocus(new JButton("elsewhere"))).isFalse();
        });
    }

    @Test
    void aPanelNobodyIsLookingAtTakesNothing() throws Exception {
        // A hidden tab must not pull the focus out of the window when its turn
        // finishes.
        var panel = new java.util.concurrent.atomic.AtomicReference<AgentChatPanel>();
        SwingUtilities.invokeAndWait(() -> panel.set(panel()));

        assertThat(panel.get().isShowing()).isFalse();
        assertThat(panel.get().shouldTakeFocus(null)).isFalse();
    }

    private static java.awt.Component firstOfType(java.awt.Container root, Class<?> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return c;
            }
            if (c instanceof java.awt.Container inner) {
                java.awt.Component found = firstOfType(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
