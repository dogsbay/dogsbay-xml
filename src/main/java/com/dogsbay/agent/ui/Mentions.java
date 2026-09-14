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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @path} mentions in a prompt: a project-relative path the user typed
 * or dropped, whose file travels with the prompt. Shared by the hosted and
 * the built-in chat so both spell them the same way.
 */
public final class Mentions {

    /** {@code @path}; a trailing period or comma belongs to the sentence, not the path. */
    public static final Pattern PATTERN = Pattern.compile("(?<![\\w/])@([\\w./\\-]*[\\w/\\-])");

    /** Files larger than this are mentioned by path only. */
    static final long MAX_INLINE_BYTES = 512 * 1024;

    private Mentions() {
    }

    /** The mention for {@code file} under {@code root}, or null when the syntax cannot carry it or it is outside. */
    public static String forFile(File file, Path root) {
        if (root == null || file == null) {
            return null;
        }
        Path p = file.toPath().toAbsolutePath().normalize();
        Path base = root.toAbsolutePath().normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) {
            return null;
        }
        String rel = base.relativize(p).toString().replace(File.separatorChar, '/');
        String mention = "@" + rel;
        Matcher m = PATTERN.matcher(mention);
        return m.find() && m.group(1).equals(rel) ? mention : null;
    }

    /** The mentioned project files in {@code text}, in order, existing regular files only. */
    public static List<Path> files(String text, Path root) {
        List<Path> out = new ArrayList<>();
        if (root == null || text == null) {
            return out;
        }
        Matcher m = PATTERN.matcher(text);
        while (m.find()) {
            Path p = root.resolve(m.group(1)).normalize();
            if (p.startsWith(root) && Files.isRegularFile(p) && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * {@code text} with each mentioned file's content appended, for agents
     * that take no attachments. Unreadable or very large files stay a path.
     */
    public static String inlined(String text, Path root) {
        List<Path> files = files(text, root);
        if (files.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (Path p : files) {
            try {
                if (Files.size(p) > MAX_INLINE_BYTES) {
                    continue;
                }
                // Forward slashes on every platform, matching the @mention it came from.
                sb.append("\n\n--- ").append(root.relativize(p).toString().replace(File.separatorChar, '/'))
                        .append(" ---\n")
                        .append(Files.readString(p, StandardCharsets.UTF_8));
            } catch (IOException unreadable) {
                // the mention stays as text
            }
        }
        return sb.toString();
    }
}
