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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * In front of an executor that writes files without the editor (the headless MCP server and the CLI):
 * refuses a change to a file that a running editor holds with unsaved changes, or to a folder holding one,
 * since writing the file would leave the writer choosing between their work and the change. A file open
 * there without unsaved changes is fine: the editor reloads it when it changes on disk.
 */
public final class UnsavedInEditorGuard implements CommandExecutor {

    private final CommandExecutor delegate;
    private final Supplier<Set<Path>> unsaved;

    /**
     * @param unsaved the real paths of the documents a running editor holds with unsaved changes (empty when
     *                no editor runs), asked once per changing command
     */
    public UnsavedInEditorGuard(CommandExecutor delegate, Supplier<Set<Path>> unsaved) {
        this.delegate = delegate;
        this.unsaved = unsaved;
    }

    @Override
    public <R> R execute(Command<R> command) throws CommandException {
        check(command);
        return delegate.execute(command);
    }

    @Override
    public <R> R execute(Command<R> command, com.dogsbay.agent.session.AgentSession session)
            throws CommandException {
        check(command);
        return delegate.execute(command, session);
    }

    private void check(Command<?> command) throws CommandException {
        CommandTargets targets = CommandTargets.of(command);
        if (!targets.isMutating() || targets.allPaths().isEmpty()) {
            return;
        }
        Set<Path> held;
        try {
            held = unsaved.get();
        } catch (RuntimeException e) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT, e.getMessage());
        }
        if (held.isEmpty()) {
            return;
        }
        List<Path> clash = new ArrayList<>();
        for (Path target : targets.allPaths()) {
            Path real = WriteGate.real(target);
            for (Path open : held) {
                if (open.equals(real) || open.startsWith(real)) {
                    clash.add(open);
                }
            }
        }
        if (!clash.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT, "The editor has "
                    + (clash.size() == 1 ? clash.get(0) : clash.size() + " documents this changes (" + clash + ")")
                    + " open with unsaved changes; nothing was changed. Save or close "
                    + (clash.size() == 1 ? "it" : "them") + " in the editor, or make this change through the editor "
                    + "(its MCP server, or dogsbay-xml tool --editor).");
        }
    }
}
