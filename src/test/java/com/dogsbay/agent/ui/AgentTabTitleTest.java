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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The built-in agent's tab is named after its conversation, the way a hosted
 * agent's is — so several sessions can be told apart at a glance.
 */
class AgentTabTitleTest {

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
    void aNewConversationCarriesTheDefaultName() {
        assertThat(panel().sessionTitle()).isEqualTo(AgentChatPanel.DEFAULT_TITLE);
    }

    @Test
    void namingTheSessionTellsWhoeverDrawsTheTab() {
        AgentChatPanel panel = panel();
        AtomicInteger redraws = new AtomicInteger();
        panel.onTitleChanged(redraws::incrementAndGet);

        panel.setSessionTitle("Audacity cleanup");

        assertThat(panel.sessionTitle()).isEqualTo("Audacity cleanup");
        assertThat(redraws.get()).isEqualTo(1);
        // Setting the same name again is not a change, and must not repaint.
        panel.setSessionTitle("Audacity cleanup");
        assertThat(redraws.get()).isEqualTo(1);
    }

    @Test
    void clearingTheNameFallsBackToTheDefault() {
        AgentChatPanel panel = panel();
        panel.setSessionTitle("Audacity cleanup");

        panel.setSessionTitle(null);
        assertThat(panel.sessionTitle()).isEqualTo(AgentChatPanel.DEFAULT_TITLE);

        panel.setSessionTitle("   ");
        assertThat(panel.sessionTitle()).isEqualTo(AgentChatPanel.DEFAULT_TITLE);
    }
}
