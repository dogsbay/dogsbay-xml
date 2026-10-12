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

/**
 * Executes commands and returns typed results.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link HeadlessExecutor} — for operations that don't need a GUI</li>
 *   <li>{@link EditorExecutor} — for operations on a running editor instance</li>
 * </ul>
 */
public interface CommandExecutor {

    /**
     * Execute on behalf of whichever session is bound on this thread
     * ({@link com.dogsbay.agent.session.SessionContext#current()}), which is
     * the person at the keyboard when nothing is bound.
     */
    <R> R execute(Command<R> command) throws CommandException;

    /**
     * Execute on behalf of {@code session}: the session is bound for the
     * duration so the command, the write gate and the audit trail can see it.
     */
    default <R> R execute(Command<R> command, com.dogsbay.agent.session.AgentSession session)
            throws CommandException {
        try {
            return com.dogsbay.agent.session.SessionContext.call(session, () -> execute(command));
        } catch (CommandException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
        }
    }
}
