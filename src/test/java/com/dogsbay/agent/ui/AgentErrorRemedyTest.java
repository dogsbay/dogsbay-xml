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

import org.junit.jupiter.api.Test;

/**
 * A backend that refuses the model says which model, but not how to change it —
 * and the model is set by a slash command, which nothing in the panel advertises.
 * The remedy line closes that gap.
 */
class AgentErrorRemedyTest {

    @Test
    void aRejectedModelPointsAtTheModelCommand() {
        String remedy = AgentChatPanel.remedyFor(new RuntimeException(
            "Codex backend HTTP 400: {\"detail\":\"The 'gpt-5.4' model is not supported "
            + "when using Codex with a ChatGPT account.\"}"));

        assertThat(remedy).contains("/model");
    }

    @Test
    void rejectedCredentialsPointAtLogin() {
        assertThat(AgentChatPanel.remedyFor(new RuntimeException("HTTP 401: Unauthorized")))
            .contains("/login");
    }

    @Test
    void aWrappedProviderErrorStillGetsTheRemedy() {
        // The error reaches the panel wrapped, and the wrapper's own message
        // says nothing about the model.
        Throwable wrapped = new RuntimeException("stream failed",
            new java.io.IOException("Codex backend HTTP 400: The 'gpt-5.4' model is not "
                + "supported when using Codex with a ChatGPT account."));

        assertThat(AgentChatPanel.remedyFor(wrapped)).contains("/model");
    }

    @Test
    void anOrdinaryFailureGetsNoAdvice() {
        // Guessing at a remedy for an error we don't recognise is worse than
        // saying nothing: it sends the reader off fixing what isn't broken.
        assertThat(AgentChatPanel.remedyFor(new RuntimeException("Connection reset by peer")))
            .isNull();
        assertThat(AgentChatPanel.remedyFor(new RuntimeException("HTTP 503: upstream busy")))
            .isNull();
        assertThat(AgentChatPanel.remedyFor(null)).isNull();
    }
}
