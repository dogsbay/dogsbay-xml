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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.xagent.event.AgentEvent;

/**
 * The risky part of the agent→UI pipeline is concurrency: events are emitted on
 * the agent's virtual/loop thread but must reach Swing on the EDT, in order.
 * This verifies {@link EdtEventDispatcher} marshals to the EDT and preserves
 * order when fed from a background thread.
 */
class EdtEventDispatcherTest {

    @Test
    void deliversEventsOnEdtInOrder() throws Exception {
        int n = 50;
        List<String> received = new CopyOnWriteArrayList<>();
        List<Boolean> onEdt = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(n);

        EdtEventDispatcher dispatcher = new EdtEventDispatcher(event -> {
            onEdt.add(SwingUtilities.isEventDispatchThread());
            if (event instanceof AgentEvent.MessageUpdate u) {
                received.add(u.partialText());
            }
            done.countDown();
        });

        // emit from a non-EDT (virtual) thread, like the agent loop does
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < n; i++) {
                dispatcher.accept(new AgentEvent.MessageUpdate("e" + i));
            }
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).as("all events delivered").isTrue();
        assertThat(onEdt).as("every delegate call on the EDT").containsOnly(Boolean.TRUE);
        assertThat(received).hasSize(n);
        for (int i = 0; i < n; i++) {
            assertThat(received.get(i)).isEqualTo("e" + i);   // order preserved
        }
    }

    @Test
    void runsInlineWhenAlreadyOnEdt() throws Exception {
        boolean[] ran = {false};
        EdtEventDispatcher dispatcher = new EdtEventDispatcher(e -> ran[0] = true);
        SwingUtilities.invokeAndWait(() -> dispatcher.accept(new AgentEvent.TurnStart()));
        assertThat(ran[0]).isTrue();
    }
}
