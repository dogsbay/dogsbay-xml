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

import java.io.File;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AuditLog;

/**
 * Turns an executed command into an audit entry. {@link ReadOnlyCommand}s
 * are not recorded; everything else an agent session runs is, whether it
 * succeeded or not.
 */
public final class CommandAudit {

    private CommandAudit() {
    }

    public static boolean isReadOnly(Command<?> command) {
        return command instanceof ReadOnlyCommand;
    }

    /** Record {@code command} for {@code session} unless it is read-only. */
    public static void record(AuditLog log, AgentSession session, Command<?> command, String outcome) {
        if (log == null || session == null || !session.isAgent() || isReadOnly(command)) {
            return;
        }
        log.record(session, name(command), files(command), isDryRun(command), outcome, null);
    }

    /** {@code RenameKeyCommand} becomes {@code rename-key}. */
    public static String name(Command<?> command) {
        String simple = command.getClass().getSimpleName();
        if (simple.endsWith("Command")) {
            simple = simple.substring(0, simple.length() - "Command".length());
        }
        return simple.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /**
     * Path-valued record components, plus string components whose name says
     * they are a file, root, path or map. Null components are skipped.
     */
    public static List<String> files(Command<?> command) {
        List<String> out = new ArrayList<>();
        CommandTargets targets = CommandTargets.of(command);
        for (Path p : targets.allPaths()) {
            out.add(p.toString());
        }
        if (targets.activeDocument()) {
            out.add("<active-document>");
        }
        if (!out.isEmpty()) {
            return out;
        }
        for (RecordComponent rc : command.getClass().getRecordComponents()) {
            Object value = read(command, rc);
            if (value == null) {
                continue;
            }
            String n = rc.getName().toLowerCase(Locale.ROOT);
            if (value instanceof Path || value instanceof File) {
                out.add(value.toString());
            } else if (value instanceof String s && !s.isBlank() && looksLikePathName(n)) {
                out.add(s);
            } else if (value instanceof List<?> list && looksLikePathName(n)) {
                for (Object o : list) {
                    if (o instanceof Path || o instanceof File) {
                        out.add(o.toString());
                    }
                }
            }
        }
        return out;
    }

    /**
     * True when the command is a plan, not an application: an {@code apply}
     * component that is false, or a {@code dryRun} component that is true.
     */
    public static boolean isDryRun(Command<?> command) {
        for (RecordComponent rc : command.getClass().getRecordComponents()) {
            if (rc.getType() != boolean.class) {
                continue;
            }
            Object value = read(command, rc);
            if ("apply".equals(rc.getName())) {
                return !Boolean.TRUE.equals(value);
            }
            if ("dryRun".equals(rc.getName())) {
                return Boolean.TRUE.equals(value);
            }
        }
        return false;
    }

    private static boolean looksLikePathName(String n) {
        return n.contains("file") || n.contains("root") || n.contains("path")
                || n.contains("map") || n.contains("catalog") || n.contains("schema")
                || n.equals("input") || n.equals("output");
    }

    private static Object read(Command<?> command, RecordComponent rc) {
        try {
            return rc.getAccessor().invoke(command);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
