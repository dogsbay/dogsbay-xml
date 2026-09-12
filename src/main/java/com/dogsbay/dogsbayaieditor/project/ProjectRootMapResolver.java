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

package com.dogsbay.dogsbayaieditor.project;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

/**
 * Resolves which configured project a file belongs to and that project's
 * Default Root Map — so DITA key resolution works from the File Explorer
 * without requiring a selection in the Projects panel.
 */
public final class ProjectRootMapResolver {

    private ProjectRootMapResolver() {
    }

    /**
     * The configured project whose folder contains {@code file} — the
     * deepest (most specific) folder when projects nest. Null when the file
     * is null or no project folder contains it.
     */
    public static ProjectProperties projectContaining(Vector projects, File file) {
        if (projects == null || file == null) {
            return null;
        }
        String path = canonical(file);
        ProjectProperties best = null;
        int bestLength = -1;
        for (int i = 0; i < projects.size(); i++) {
            Object o = projects.elementAt(i);
            if (!(o instanceof ProjectProperties)) {
                continue;
            }
            ProjectProperties project = (ProjectProperties) o;
            String folder = project.getFolderPath();
            if (folder == null || folder.isEmpty()) {
                continue;
            }
            String folderCanon = canonical(new File(folder));
            if ((path.equals(folderCanon)
                    || path.startsWith(folderCanon + File.separator))
                    && folderCanon.length() > bestLength) {
                best = project;
                bestLength = folderCanon.length();
            }
        }
        return best;
    }

    /**
     * The project's Default Root Map as an existing file (relative maps are
     * resolved against the project folder); null when unset or missing.
     */
    public static File rootMapFile(ProjectProperties project) {
        if (project == null) {
            return null;
        }
        String rootMap = project.getDefaultRootMap();
        if (rootMap == null || rootMap.trim().isEmpty()) {
            return null;
        }
        File mapFile = new File(rootMap);
        if (!mapFile.isAbsolute()) {
            String folder = project.getFolderPath();
            if (folder == null || folder.isEmpty()) {
                return null;
            }
            mapFile = new File(folder, rootMap);
        }
        return mapFile.isFile() ? mapFile : null;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
