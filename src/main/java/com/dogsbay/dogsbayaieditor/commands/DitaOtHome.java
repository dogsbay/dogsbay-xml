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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/**
 * Where DITA-OT is for a project, without the running editor. The editor finds its
 * engine from the frameworks it has registered; a build started by an agent, or from
 * the command line, used to find nothing unless it was given a path, and refused with
 * "DITA-OT is not configured" on a machine where the editor itself could publish.
 *
 * <p>The order follows the editor's: an explicit path, the project's personal
 * {@code local.xml} path, the framework the project names in {@code config.xml} (as
 * registered in {@code settings.xml}, or installed under the frameworks folder), the
 * DITA-OT the editor bundles, then the first registered framework that is installed.
 */
public final class DitaOtHome {

    /** The folder name the bundled framework installs under; matches {@code DefaultDitaOtFramework}. */
    static final String BUNDLED_FRAMEWORK = "DITA-OT 4.3.5";

    /** For tests: points the lookup at another DogsBay home, with its own frameworks folder and settings.xml. */
    static final String HOME_PROPERTY = "dogsbay.home.dir";

    private static final Pattern FRAMEWORK = Pattern.compile("<framework-properties>(.*?)</framework-properties>", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("<name>(.*?)</name>", Pattern.DOTALL);
    private static final Pattern DITA_OT_PATH = Pattern.compile("<dita-ot-path>(.*?)</dita-ot-path>", Pattern.DOTALL);

    private DitaOtHome() {
    }

    /**
     * @param projectRoot the project root
     * @param explicit    a DITA-OT home the caller gave, or null
     * @return the DITA-OT home, or null when none is configured or installed
     */
    public static Path resolve(Path projectRoot, Path explicit) {
        return resolve(projectRoot, explicit, dogsbayHome());
    }

    /** As {@link #resolve(Path, Path)}, against the given DogsBay home ({@code ~/.dogsbay}). */
    static Path resolve(Path projectRoot, Path explicit, Path dogsbayHome) {
        if (explicit != null) {
            return explicit;
        }
        DogsbayProjectConfig config = projectRoot == null ? null : DogsbayProjectConfig.load(projectRoot);
        Path personal = config == null ? null : safePath(config.getDitaOtPath());
        if (isHome(personal)) {
            return personal;
        }
        if (dogsbayHome == null) {
            return null;
        }
        Map<String, Path> registered = registeredFrameworks(dogsbayHome.resolve("settings.xml"));
        Path frameworksDir = dogsbayHome.resolve("frameworks");
        String named = config == null ? null : config.getFramework();
        if (named != null && !named.isBlank()) {
            if (isHome(registered.get(named))) {
                return registered.get(named);
            }
            Path installed = frameworksDir.resolve(named).resolve("dita-ot");
            if (isHome(installed)) {
                return installed;
            }
        }
        Path bundled = frameworksDir.resolve(BUNDLED_FRAMEWORK).resolve("dita-ot");
        if (isHome(bundled)) {
            return bundled;
        }
        for (Path path : registered.values()) {
            if (isHome(path)) {
                return path;
            }
        }
        return null;
    }

    /** The DogsBay home: {@code ~/.dogsbay}, unless a test redirects it. */
    static Path dogsbayHome() {
        String override = System.getProperty(HOME_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".dogsbay");
    }

    /**
     * The frameworks registered in the editor's settings, name to DITA-OT path, in the order
     * the editor lists them. Read as text, so no parser configuration can reach out; an unreadable
     * or missing file is no frameworks.
     */
    static Map<String, Path> registeredFrameworks(Path settingsFile) {
        Map<String, Path> out = new LinkedHashMap<>();
        String text;
        try {
            text = Files.readString(settingsFile, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return out;
        }
        Matcher block = FRAMEWORK.matcher(text);
        while (block.find()) {
            Matcher name = NAME.matcher(block.group(1));
            Matcher path = DITA_OT_PATH.matcher(block.group(1));
            if (name.find() && path.find()) {
                Path p = safePath(unescape(path.group(1).trim()));
                if (p != null) {
                    out.putIfAbsent(unescape(name.group(1).trim()), p);
                }
            }
        }
        return out;
    }

    /** A path, or null when the text is blank or not a valid path on this platform. */
    static Path safePath(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    /**
     * A folder that looks like a DITA-OT installation: a {@code plugins} folder, and either the
     * Ant {@code build.xml} or the {@code dita} launcher. The engine the editor bundles is trimmed
     * for in-process builds and has no {@code bin} folder, so the launcher alone cannot be the test.
     */
    static boolean isHome(Path dir) {
        return dir != null && Files.isDirectory(dir.resolve("plugins"))
                && (Files.isRegularFile(dir.resolve("build.xml"))
                        || Files.isRegularFile(dir.resolve("bin").resolve("dita"))
                        || Files.isRegularFile(dir.resolve("bin").resolve("dita.bat")));
    }
}
