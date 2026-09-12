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

package com.dogsbay.dogsbayaieditor.dita.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;

/** Tests the launch-restore map precedence: persisted last-open map, else default root map. */
class DitaMapRestoreTest {

    private static ProjectProperties ditaProject(Path folder, String rootMap) {
        ProjectProperties p = new ProjectProperties("demo");
        p.setProjectType(ProjectProperties.TYPE_DITA);
        p.setFolderPath(folder.toString());
        if (rootMap != null) {
            p.setDefaultRootMap(rootMap);
        }
        return p;
    }

    @Test
    void prefersPersistedMapWhenItExistsUnderProject(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("root.ditamap"), "<map/>");
        Path last = folder.resolve("sub/working.ditamap");
        Files.createDirectories(last.getParent());
        Files.writeString(last, "<map/>");

        File chosen = DitaExplorerPanel.resolveMapToShow(ditaProject(folder, "root.ditamap"), last.toString());
        assertEquals(last.toFile(), chosen, "the map the user last had open wins");
    }

    @Test
    void fallsBackToDefaultRootMapWhenPersistedIsMissingOrOutsideProject(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("root.ditamap"), "<map/>");
        ProjectProperties project = ditaProject(folder, "root.ditamap");
        File expected = folder.resolve("root.ditamap").toFile();

        // persisted map no longer exists → default root map
        assertEquals(expected, DitaExplorerPanel.resolveMapToShow(project, folder.resolve("gone.ditamap").toString()));
        // persisted map exists but lives outside the project → default root map
        assertEquals(expected, DitaExplorerPanel.resolveMapToShow(project, "/etc/hosts"));
        // nothing persisted → default root map
        assertEquals(expected, DitaExplorerPanel.resolveMapToShow(project, null));
    }

    @Test
    void resolvesAbsoluteDefaultRootMapPath(@TempDir Path folder, @TempDir Path elsewhere) throws Exception {
        // Default root map stored as an ABSOLUTE path (outside the project folder).
        Path absMap = elsewhere.resolve("external-root.ditamap");
        Files.writeString(absMap, "<map/>");
        ProjectProperties project = ditaProject(folder, absMap.toString());

        assertEquals(absMap.toFile(), DitaExplorerPanel.resolveMapToShow(project, null),
                "absolute default-root-map path resolves (not new File(folder, absPath))");
    }

    @Test
    void foreignPersistedMapIsNotShownWhenNoRootMap(@TempDir Path folder, @TempDir Path other) throws Exception {
        Path foreign = other.resolve("foreign.ditamap");
        Files.writeString(foreign, "<map/>");
        // DITA project, no root map, persisted map lives in a DIFFERENT project.
        assertNull(DitaExplorerPanel.resolveMapToShow(ditaProject(folder, null), foreign.toString()),
                "a map outside the project must not be restored");
    }

    @Test
    void nullWhenNotDitaOrNoMapAvailable(@TempDir Path folder) {
        ProjectProperties notDita = new ProjectProperties("x");
        notDita.setFolderPath(folder.toString());
        assertNull(DitaExplorerPanel.resolveMapToShow(notDita, null), "non-DITA project shows no map");

        assertNull(DitaExplorerPanel.resolveMapToShow(ditaProject(folder, null), null),
                "DITA project with no root map and nothing persisted → null");
        assertNull(DitaExplorerPanel.resolveMapToShow(null, null));
    }
}
