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

package com.dogsbay.xml.dita;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the Node.js executable that runs the dogsbay publishing converter.
 *
 * <p>An editor started from the desktop often does not have the shell's PATH (nvm,
 * fnm and Volta add Node.js there in shell start-up files), so after the configured
 * path, {@code DOGSBAY_NODE} and the PATH, it looks in the folders those version
 * managers and the usual installers use. The first executable found wins; for
 * version managers, the highest installed version.
 */
public class NodeLocator {

    /** System property with the Node.js path from the editor's preferences. */
    public static final String PATH_PROPERTY = "dogsbay.node.path";

    /** Environment variable naming the Node.js executable. */
    public static final String ENV_VARIABLE = "DOGSBAY_NODE";

    private final Map<String, String> env;
    private final Path home;
    private final boolean windows;
    private final String configured;

    /** A locator for this machine: its environment, home folder, operating system and the preference. */
    public static NodeLocator system() {
        return system(System.getProperty(PATH_PROPERTY));
    }

    /** A locator for this machine with {@code configured} (may be null) in place of the preference. */
    public static NodeLocator system(String configured) {
        return new NodeLocator(System.getenv(), Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").toLowerCase().startsWith("windows"), configured);
    }

    NodeLocator(Map<String, String> env, Path home, boolean windows, String configured) {
        this.env = env;
        this.home = home;
        this.windows = windows;
        this.configured = configured;
    }

    /** The Node.js executable, or empty when none is found. */
    public Optional<Path> find() {
        for (Path candidate : candidates()) {
            if (isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    /** Where to look, in order. */
    List<Path> candidates() {
        List<Path> out = new ArrayList<>();
        String exe = windows ? "node.exe" : "node";
        if (configured != null && !configured.isBlank()) {
            out.add(Path.of(configured.trim()));
        }
        String fromEnv = env.get(ENV_VARIABLE);
        if (fromEnv != null && !fromEnv.isBlank()) {
            out.add(Path.of(fromEnv.trim()));
        }
        String path = env.get(windows ? pathKey() : "PATH");
        if (path != null) {
            for (String dir : path.split(windows ? ";" : ":")) {
                if (!dir.isBlank()) {
                    out.add(Path.of(dir.trim(), exe));
                }
            }
        }
        if (windows) {
            for (String base : new String[] { env.get("ProgramFiles"), env.get("ProgramFiles(x86)") }) {
                if (base != null) {
                    out.add(Path.of(base, "nodejs", exe));
                }
            }
            String appData = env.get("APPDATA");
            if (appData != null) {
                out.addAll(newestFirst(Path.of(appData, "nvm"), "", exe));
                out.addAll(newestFirst(Path.of(appData, "fnm", "node-versions"), "installation", exe));
            }
            String localAppData = env.get("LOCALAPPDATA");
            if (localAppData != null) {
                out.add(Path.of(localAppData, "Volta", "bin", exe));
            }
            return out;
        }
        String nvmDir = env.get("NVM_DIR");
        out.addAll(newestFirst(nvmDir != null ? Path.of(nvmDir, "versions", "node") : home.resolve(".nvm/versions/node"), "bin", exe));
        out.addAll(newestFirst(home.resolve(".local/share/fnm/node-versions"), "installation/bin", exe));
        out.addAll(newestFirst(home.resolve("Library/Application Support/fnm/node-versions"), "installation/bin", exe));
        out.add(home.resolve(".volta/bin").resolve(exe));
        out.add(home.resolve(".asdf/shims").resolve(exe));
        out.add(Path.of("/opt/homebrew/bin", exe));
        out.add(Path.of("/usr/local/bin", exe));
        out.add(Path.of("/usr/bin", exe));
        return out;
    }

    /** Windows environment keys are case-insensitive; Java's map is not. */
    private String pathKey() {
        return env.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
    }

    /** {@code <versions>/<version>/<bin>/<exe>} for each version folder, the highest version first. */
    private static List<Path> newestFirst(Path versions, String bin, String exe) {
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(versions)) {
            return dirs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> versionKey(p.getFileName().toString())).reversed())
                    .map(p -> (bin.isEmpty() ? p : p.resolve(bin)).resolve(exe))
                    .toList();
        } catch (java.io.IOException e) {
            return List.of();
        }
    }

    /** A sortable key for a version folder name such as {@code v22.3.0}: each number zero-padded. */
    static String versionKey(String name) {
        StringBuilder key = new StringBuilder();
        for (String part : name.replaceFirst("^v", "").split("[.\\-]")) {
            key.append(part.matches("\\d+") ? String.format("%010d", Long.parseLong(part)) : part).append('.');
        }
        return key.toString();
    }

    private static boolean isExecutable(Path candidate) {
        File file = candidate.toFile();
        return file.isFile() && file.canExecute();
    }
}
