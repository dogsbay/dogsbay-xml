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
package com.xagent.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Skills shipped inside xagent-core (under the {@code /skills} classpath
 * resource). They are extracted to a per-version cache directory so they are
 * always in sync with the running release — no manual copying, no drift.
 *
 * <p>Lives in xagent-core (not xagent-cli) so every embedder — the CLI/TUI and
 * the DogsBay editor plugin — ships the same default skills. The extracted
 * directory is added to skill discovery as the lowest-precedence source, so a
 * user's own skill with the same name still wins.
 */
public final class BundledSkills {

    private static final String INDEX_RESOURCE = "/skills/index.txt";

    private BundledSkills() {}

    /**
     * Extract the bundled skills to {@code <cacheRoot>/<version>/} and return
     * that directory, or null if there are no bundled skills on the classpath.
     * Existing files are overwritten so a rebuilt jar refreshes them.
     */
    public static Path extract(Path cacheRoot, String version) throws IOException {
        List<String> names = readIndex();
        if (names.isEmpty()) {
            return null;
        }
        Path dir = cacheRoot.resolve(version);
        for (String name : names) {
            String content = readResource("/skills/" + name + "/SKILL.md");
            if (content == null) {
                continue; // listed but missing — skip rather than fail
            }
            Path skillDir = dir.resolve(name);
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), content);
        }
        return dir;
    }

    /** Default cache location: {@code ~/.xagent/bundled-skills}. */
    public static Path defaultCacheRoot() {
        return Path.of(System.getProperty("user.home"), ".xagent", "bundled-skills");
    }

    static List<String> readIndex() throws IOException {
        String index = readResource(INDEX_RESOURCE);
        if (index == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String line : index.split("\n")) {
            String name = line.strip();
            if (!name.isEmpty() && !name.startsWith("#")) {
                names.add(name);
            }
        }
        return names;
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = BundledSkills.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
