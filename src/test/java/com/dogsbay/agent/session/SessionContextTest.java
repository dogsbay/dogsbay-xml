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
package com.dogsbay.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SessionContextTest {

    private final AgentSessionRegistry registry = new AgentSessionRegistry();

    @Test
    void unboundIsTheAnonymousUser() {
        assertThat(SessionContext.isBound()).isFalse();
        assertThat(SessionContext.current().kind()).isEqualTo(SessionKind.USER);
        assertThat(SessionContext.current().identity()).isEqualTo("user:unknown");
    }

    @Test
    void bindsForTheDurationOfTheCallAndRestoresAfter() throws Exception {
        AgentSession outer = registry.open(SessionKind.BUILTIN, "outer", null, null);
        AgentSession inner = registry.open(SessionKind.EXTERNAL_MCP, "inner", null, null);

        String result = SessionContext.call(outer, () -> {
            assertThat(SessionContext.current()).isEqualTo(outer);
            SessionContext.run(inner, () -> assertThat(SessionContext.current()).isEqualTo(inner));
            assertThat(SessionContext.current()).isEqualTo(outer);
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(SessionContext.isBound()).isFalse();
    }

    @Test
    void unbindsEvenWhenTheBodyThrows() {
        AgentSession s = registry.open(SessionKind.BUILTIN, "s", null, null);
        assertThatThrownBy(() -> SessionContext.run(s, () -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThat(SessionContext.isBound()).isFalse();
    }

    @Test
    void bindingIsPerThread() throws Exception {
        AgentSession s = registry.open(SessionKind.BUILTIN, "s", null, null);
        AtomicReference<AgentSession> seenOnOtherThread = new AtomicReference<>();
        SessionContext.run(s, () -> {
            Thread t = new Thread(() -> seenOnOtherThread.set(SessionContext.current()));
            t.start();
            try { t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        assertThat(seenOnOtherThread.get().kind()).isEqualTo(SessionKind.USER);
    }
}
