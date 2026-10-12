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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Headless tests for project-by-file matching and root-map resolution. */
class ProjectRootMapResolverTest {

    private Path tempDir;

    @BeforeAll
    static void initDom4j() {
        System.setProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory");
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("rootmap-resolver-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private ProjectProperties project(String name, String folderPath, String rootMap) {
        ProjectProperties p = new ProjectProperties(name);
        p.setFolderPath(folderPath);
        if (rootMap != null) {
            p.setDefaultRootMap(rootMap);
        }
        return p;
    }

    @Test
    void projectContaining_matchesFileInsideFolder() throws IOException {
        File starter = tempDir.resolve("dita-starter").toFile();
        Files.createDirectories(starter.toPath().resolve("input/docs"));
        File doc = starter.toPath().resolve("input/docs/index.dita").toFile();
        Files.write(doc.toPath(), "<concept/>".getBytes(StandardCharsets.UTF_8));

        Vector projects = new Vector();
        projects.add(project("other", tempDir.resolve("elsewhere").toString(), null));
        projects.add(project("dita-starter", starter.getAbsolutePath(), null));

        ProjectProperties match = ProjectRootMapResolver.projectContaining(projects, doc);
        assertNotNull(match);
        assertEquals("dita-starter", match.getName());
    }

    @Test
    void projectContaining_nestedProjects_deepestWins() throws IOException {
        File outer = tempDir.resolve("outer").toFile();
        File inner = tempDir.resolve("outer/inner").toFile();
        Files.createDirectories(inner.toPath());
        File doc = inner.toPath().resolve("a.dita").toFile();
        Files.write(doc.toPath(), "<concept/>".getBytes(StandardCharsets.UTF_8));

        Vector projects = new Vector();
        projects.add(project("outer", outer.getAbsolutePath(), null));
        projects.add(project("inner", inner.getAbsolutePath(), null));

        assertEquals("inner",
                ProjectRootMapResolver.projectContaining(projects, doc).getName());
    }

    @Test
    void projectContaining_noMatch_andNulls() {
        Vector projects = new Vector();
        projects.add(project("p", tempDir.resolve("p").toString(), null));

        assertNull(ProjectRootMapResolver.projectContaining(projects,
                new File("/somewhere/else/x.dita")));
        assertNull(ProjectRootMapResolver.projectContaining(null, new File("/x")));
        assertNull(ProjectRootMapResolver.projectContaining(projects, null));
    }

    @Test
    void projectContaining_prefixIsPathAware() throws IOException {
        // /tmp/.../proj must NOT match a file in /tmp/.../proj-other
        File proj = tempDir.resolve("proj").toFile();
        File projOther = tempDir.resolve("proj-other").toFile();
        Files.createDirectories(proj.toPath());
        Files.createDirectories(projOther.toPath());
        File doc = projOther.toPath().resolve("a.dita").toFile();
        Files.write(doc.toPath(), "<concept/>".getBytes(StandardCharsets.UTF_8));

        Vector projects = new Vector();
        projects.add(project("proj", proj.getAbsolutePath(), null));

        assertNull(ProjectRootMapResolver.projectContaining(projects, doc),
                "proj must not claim proj-other's files");
    }

    @Test
    void rootMapFile_relativeResolvedAgainstFolder() throws IOException {
        File folder = tempDir.resolve("starter").toFile();
        Files.createDirectories(folder.toPath().resolve("input"));
        File map = folder.toPath().resolve("input/root.ditamap").toFile();
        Files.write(map.toPath(), "<map/>".getBytes(StandardCharsets.UTF_8));

        ProjectProperties p = project("starter", folder.getAbsolutePath(),
                "input/root.ditamap");
        assertEquals(map, ProjectRootMapResolver.rootMapFile(p));
    }

    @Test
    void rootMapFile_missingOrUnset_null() {
        ProjectProperties noMap = project("a", tempDir.toString(), null);
        assertNull(ProjectRootMapResolver.rootMapFile(noMap));

        ProjectProperties missing = project("b", tempDir.toString(), "nope.ditamap");
        assertNull(ProjectRootMapResolver.rootMapFile(missing));

        assertNull(ProjectRootMapResolver.rootMapFile(null));
    }
}
