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

package com.dogsbay.agent.runtime;

import java.util.function.Consumer;

import com.xagent.core.Agent;
import com.xagent.event.AgentEvent;

/**
 * Runs {@link Agent#prompt} on a virtual thread and streams its events to a UI
 * consumer on the EDT (via {@link EdtEventDispatcher}). The agent loop blocks on
 * the virtual thread; on JDK 24+ its {@code synchronized} sections no longer pin
 * the carrier thread (a reason the editor moved to JDK 25).
 *
 * <p>With lazy provider startup, {@code prompt} throws on the first send when no
 * credentials are usable; that exception is caught and surfaced as an
 * {@link AgentEvent.ErrorOccurred} so the UI shows guidance and stays responsive
 * (the user can then pick a provider / paste a key / switch to Ollama and retry).
 */
public final class AgentRunner {

    private final Agent agent;
    private final EdtEventDispatcher dispatcher;

    public AgentRunner(Agent agent, Consumer<AgentEvent> uiSink) {
        this.agent = agent;
        this.dispatcher = new EdtEventDispatcher(uiSink);
        agent.subscribe(dispatcher);
    }

    /** Send a prompt; returns immediately, work proceeds on a virtual thread. */
    public void submit(String input) {
        Thread.ofVirtual().name("agent-prompt").start(() -> {
            try {
                agent.prompt(input);
            } catch (Throwable t) {
                // missing credentials / provider build failure surfaces here
                dispatcher.accept(new AgentEvent.ErrorOccurred(t));
            }
        });
    }

    /** Request the current turn be cancelled. */
    public void abort() {
        agent.abort();
    }

    public boolean isStreaming() {
        return agent.isStreaming();
    }
}
