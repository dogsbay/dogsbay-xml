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
package com.dogsbay.dogsbayaieditor.commands;

import java.util.Objects;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.SessionContext;

/**
 * The executor the editor hands out: wraps the real one with everything a
 * session needs. Applied once, at the composition root, so any executor
 * (editor, headless, a test double) gets the same treatment.
 *
 * <ul>
 * <li>An unbound caller is the person at the keyboard.</li>
 * <li>The session is bound on the thread for the duration, so code deep in a
 *     command can read it.</li>
 * <li>Mutating commands from agent sessions pass the {@link WriteGate}.</li>
 * <li>Reads by agent sessions are remembered for the gate's conflict check.</li>
 * <li>Everything an agent session ran that could change something is
 *     appended to the {@link AuditLog}, succeed or fail.</li>
 * </ul>
 */
public final class SessionExecutor implements CommandExecutor {

    private final CommandExecutor delegate;
    private final AgentSessionRegistry sessions;
    private final WriteGate gate;
    private final AuditLog audit;

    public SessionExecutor(CommandExecutor delegate, AgentSessionRegistry sessions,
            WriteGate gate, AuditLog audit) {
        this.delegate = Objects.requireNonNull(delegate);
        this.sessions = Objects.requireNonNull(sessions);
        this.gate = gate;
        this.audit = audit;
    }

    public WriteGate gate() {
        return gate;
    }

    @Override
    public <R> R execute(Command<R> command) throws CommandException {
        AgentSession session = SessionContext.isBound() ? SessionContext.current() : sessions.user();
        return execute(command, session);
    }

    @Override
    public <R> R execute(Command<R> command, AgentSession session) throws CommandException {
        String outcome = "ok";
        try {
            if (command instanceof UserOnlyCommand && session.isAgent()) {
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                        "Only the writer may run " + CommandAudit.name(command) + "; " + session.displayName()
                        + " may leave a comment with review-comment instead");
            }
            if (gate != null) {
                gate.check(session, command);
            }
            R result;
            try {
                result = SessionContext.call(session, () -> delegate.execute(command, session));
            } catch (Exception e) {
                if (gate != null) {
                    gate.abandon(session, command);
                }
                throw e;
            }
            if (gate != null) {
                remember(session, command, result);
            }
            return result;
        } catch (CommandException e) {
            outcome = "error:" + e.getCode();
            throw e;
        } catch (RuntimeException e) {
            outcome = "error:" + e.getClass().getSimpleName();
            throw e;
        } catch (Exception e) {
            outcome = "error:" + e.getClass().getSimpleName();
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
        } finally {
            if (audit != null) {
                CommandAudit.record(audit, session, command, outcome);
            }
        }
    }

    private void remember(AgentSession session, Command<?> command, Object result) throws CommandException {
        if (!session.isAgent()) {
            return;
        }
        // Any read that shows the agent the document's shape counts: content,
        // the selection, or either outline (the author workflow edits by block id).
        switch (command) {
            case GetContentCommand c when result instanceof String text -> gate.noteRead(session, c.file(), text);
            case GetSelectionCommand c -> gate.noteRead(session, null, gate.currentContent(null));
            case GetOutlineCommand c -> gate.noteRead(session, c.file(), gate.currentContent(c.file()));
            case AuthorOutlineCommand c -> gate.noteRead(session, c.file(), gate.currentContent(c.file()));
            default -> gate.noteWritten(session, command);
        }
    }
}
