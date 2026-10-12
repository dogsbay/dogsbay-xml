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

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * The session on whose behalf the current command is executing.
 *
 * <p>The executors bind it for the duration of {@code execute}, so code deep
 * inside a command (the write gate, the proposals feature, audit) can read
 * it without every signature carrying a session. Bound per thread and
 * inherited by nothing: work handed to another thread must rebind.
 */
public final class SessionContext {

    private static final ThreadLocal<AgentSession> CURRENT = new ThreadLocal<>();

    private SessionContext() {
    }

    /** The bound session, or the anonymous user when nothing is bound. */
    public static AgentSession current() {
        AgentSession s = CURRENT.get();
        return s != null ? s : AgentSession.anonymousUser();
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /** Run {@code body} with {@code session} bound on this thread. */
    public static <T> T call(AgentSession session, Callable<T> body) throws Exception {
        Objects.requireNonNull(session, "session");
        AgentSession previous = CURRENT.get();
        CURRENT.set(session);
        try {
            return body.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Run {@code body} with {@code session} bound on this thread. */
    public static void run(AgentSession session, Runnable body) {
        try {
            call(session, () -> {
                body.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
