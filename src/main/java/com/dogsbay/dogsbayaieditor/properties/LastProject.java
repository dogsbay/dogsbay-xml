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

package com.dogsbay.dogsbayaieditor.properties;

import java.nio.file.Path;
import java.util.List;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;

/**
 * Which project to reopen at startup, from what the settings remember: the folder that was
 * open, the project's name, and the document that was active.
 *
 * <p>Folder and name used to be written by different code paths (Open Recent and creating a project
 * through the agent tools wrote the folder only, creating a project in Manage Projects wrote the name
 * only, and an agent's open_project wrote neither), so they could name two different projects. The
 * editor then restored the folder's project while the open documents and root map came from the other
 * one, and a hosted agent resolved paths in a project the writer did not think was open (2026-09-17).
 * They are written together now ({@link ConfigurationProperties#rememberOpenProject}); for settings
 * written before that, a disagreement is settled by the active document, which says where the writer
 * was working.
 */
public final class LastProject {

    private LastProject() {
    }

    /**
     * The project to reopen, or null.
     *
     * @param projects       the registered projects
     * @param lastFolder     the folder that was open, or null
     * @param lastName       the project name recorded with it, or null
     * @param activeDocument the document that was active, or null
     */
    public static ProjectProperties choose(List<ProjectProperties> projects, String lastFolder, String lastName,
            String activeDocument) {
        ProjectProperties byFolder = null;
        ProjectProperties byName = null;
        for (ProjectProperties project : projects) {
            if (byFolder == null && samePath(lastFolder, project.getFolderPath())) {
                byFolder = project;
            }
            if (byName == null && lastName != null && !lastName.isBlank() && lastName.equals(project.getName())) {
                byName = project;
            }
        }
        if (byFolder != null && byName != null && byFolder != byName) {
            // The settings disagree: reopen the project the active document is in
            if (contains(byName.getFolderPath(), activeDocument) && !contains(byFolder.getFolderPath(), activeDocument)) {
                return byName;
            }
            return byFolder;
        }
        return byFolder != null ? byFolder : byName;
    }

    /** The registered project whose folder is {@code folder}, or null. */
    public static ProjectProperties at(List<ProjectProperties> projects, String folder) {
        for (ProjectProperties project : projects) {
            if (samePath(folder, project.getFolderPath())) {
                return project;
            }
        }
        return null;
    }

    static boolean samePath(String a, String b) {
        Path x = normal(a);
        return x != null && x.equals(normal(b));
    }

    private static boolean contains(String folder, String file) {
        Path f = normal(folder);
        Path d = normal(file);
        return f != null && d != null && d.startsWith(f);
    }

    /**
     * A path from the settings as a normalized path, or null when it is not one. Session documents are
     * stored as a URL's path ({@code URL.getFile()}): percent-encoded, and {@code /C:/...} on Windows,
     * which {@link Path#of} rejects there.
     */
    static Path normal(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            String p = path.startsWith("file:") ? java.nio.file.Path.of(java.net.URI.create(path)).toString() : path;
            if (p.contains("%")) {
                p = java.net.URLDecoder.decode(p.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (p.matches("^/[A-Za-z]:[/\\\\].*")) {
                p = p.substring(1);
            }
            return Path.of(p).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
