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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Detection-logic tests for {@link ProjectAutoConfigurer} (the parts that don't
 * need a live GUI ConfigurationProperties).
 */
class ProjectAutoConfigurerTest {

    @TempDir Path dir;

    private File write(String rel, String content) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent() != null ? p.getParent() : dir);
        Files.writeString(p, content);
        return p.toFile();
    }

    private static String map(String body) {
        return "<?xml version=\"1.0\"?><!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">"
                + "<map>" + body + "</map>";
    }

    @Test
    void singleMap_isTheRoot() throws Exception {
        File only = write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        assertEquals(only.getCanonicalPath(),
                ProjectAutoConfigurer.detectRootMap(dir.toFile()).getCanonicalPath());
    }

    @Test
    void multiMap_unreferencedMapWins() throws Exception {
        // root.ditamap references sub.ditamap → sub is not a root.
        File root = write("root.ditamap", map("<mapref href=\"sub.ditamap\"/>"));
        write("sub.ditamap", map("<topicref href=\"t.dita\"/>"));
        assertEquals(root.getCanonicalPath(),
                ProjectAutoConfigurer.detectRootMap(dir.toFile()).getCanonicalPath());
    }

    @Test
    void multiMap_topicrefWithFormatDitamap_countsAsReference() throws Exception {
        File root = write("main.ditamap", map("<topicref href=\"child.ditamap\" format=\"ditamap\"/>"));
        write("child.ditamap", map("<topicref href=\"t.dita\"/>"));
        assertEquals(root.getCanonicalPath(),
                ProjectAutoConfigurer.detectRootMap(dir.toFile()).getCanonicalPath());
    }

    @Test
    void projectJson_inputMapWins_overHeuristic() throws Exception {
        // Two unreferenced maps, but project.json names one explicitly.
        write("alpha.ditamap", map("<topicref href=\"a.dita\"/>"));
        File chosen = write("beta.ditamap", map("<topicref href=\"b.dita\"/>"));
        write("project.json",
                "{ \"deliverables\": [ { \"name\": \"full\", \"context\": { \"input\": \"beta.ditamap\" } } ] }");
        assertEquals(chosen.getCanonicalPath(),
                ProjectAutoConfigurer.detectRootMap(dir.toFile()).getCanonicalPath());
    }

    @Test
    void skipsOutputDirs() throws Exception {
        File real = write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        write("out/full/guide.ditamap", map("<topicref href=\"a.dita\"/>")); // build output — ignored
        assertEquals(real.getCanonicalPath(),
                ProjectAutoConfigurer.detectRootMap(dir.toFile()).getCanonicalPath());
    }

    @Test
    void nonDitaFolder_isNotDita() throws Exception {
        write("readme.md", "# hello");
        write("notes.txt", "stuff");
        assertFalse(ProjectAutoConfigurer.looksLikeDita(dir.toFile()));
        assertNull(ProjectAutoConfigurer.detectRootMap(dir.toFile()));
    }

    @Test
    void ditaTopicsButNoMap_isDita_butNoRoot() throws Exception {
        write("topics/intro.dita", "<concept id=\"x\"/>");
        assertTrue(ProjectAutoConfigurer.looksLikeDita(dir.toFile()));
        assertNull(ProjectAutoConfigurer.detectRootMap(dir.toFile()));
    }

    // ── config-centric end-to-end (null editor config; framework comes from .dogsbay) ──

    private ProjectProperties projectForTempDir() {
        ProjectProperties p = new ProjectProperties("test-project");
        p.setFolderPath(dir.toFile().getAbsolutePath());
        return p;
    }

    @Test
    void committedConfig_isHonored_andNotRewritten() throws Exception {
        write("audacity-guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        write(".dogsbay/config.xml",
                "<dogsbay-project><project-type>DITA</project-type>"
                + "<default-root-map>audacity-guide.ditamap</default-root-map>"
                + "<framework>DITA-OT 4.3.5</framework></dogsbay-project>");
        long before = dir.resolve(".dogsbay/config.xml").toFile().lastModified();

        ProjectProperties p = projectForTempDir();
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configure(p, null);

        assertTrue(r.isDita());
        assertTrue(r.fromConfig());
        assertFalse(r.wroteConfig());
        assertEquals("DITA", p.getProjectType());
        assertEquals("audacity-guide.ditamap", p.getDefaultRootMap());
        assertEquals("DITA-OT 4.3.5", p.getFrameworkName());
        assertEquals(before, dir.resolve(".dogsbay/config.xml").toFile().lastModified(),
                "existing committed config must not be rewritten");
    }

    @Test
    void noConfig_detects_syncs_andMaterializesConfig() throws Exception {
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        assertFalse(dir.resolve(".dogsbay/config.xml").toFile().exists());

        ProjectProperties p = projectForTempDir();
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configure(p, null);

        assertTrue(r.isDita());
        assertFalse(r.fromConfig());
        assertTrue(r.wroteConfig());
        assertEquals("guide.ditamap", p.getDefaultRootMap());
        // A portable .dogsbay/config.xml was materialized with the detected map.
        File created = dir.resolve(".dogsbay/config.xml").toFile();
        assertTrue(created.isFile());
        String xml = Files.readString(created.toPath());
        assertTrue(xml.contains("guide.ditamap"), xml);
        assertTrue(xml.contains("DITA"), xml);
    }

    @Test
    void nonDitaFolder_leavesProjectUntouched() throws Exception {
        write("readme.md", "# hi");
        ProjectProperties p = projectForTempDir();
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configure(p, null);
        assertFalse(r.isDita());
        assertNotEquals("DITA", p.getProjectType());
        assertFalse(dir.resolve(".dogsbay/config.xml").toFile().exists());
    }

    @Test
    void materializeIsReportedAsChanged_evenWhenStatusWouldBeWriteOnly() throws Exception {
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        ProjectProperties p = projectForTempDir();
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configure(p, null);
        assertTrue(r.wroteConfig());
        // changedAnything() must include a write, so the "(saved .dogsbay/config.xml)"
        // status branch is reachable even if no runtime field changed.
        assertTrue(r.changedAnything());
    }

    // ── syncFromConfig: cheap path (no walk, no write) ──

    @Test
    void syncFromConfig_honorsCommittedConfig_withoutWriting() throws Exception {
        write(".dogsbay/config.xml",
                "<dogsbay-project><project-type>DITA</project-type>"
                + "<default-root-map>audacity-guide.ditamap</default-root-map>"
                + "<framework>DITA-OT 4.3.5</framework></dogsbay-project>");
        ProjectProperties p = projectForTempDir();

        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.syncFromConfig(p);

        assertTrue(r.isDita());
        assertTrue(r.mutated());
        assertEquals("DITA", p.getProjectType());
        assertEquals("audacity-guide.ditamap", p.getDefaultRootMap());
        assertEquals("DITA-OT 4.3.5", p.getFrameworkName());
    }

    @Test
    void syncFromConfig_noCommittedConfig_doesNothing_noDetectNoWrite() throws Exception {
        // Maps present, but NO .dogsbay/config.xml — the cheap path must not detect
        // or materialize anything (that's reserved for configure()).
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        ProjectProperties p = projectForTempDir();

        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.syncFromConfig(p);

        assertFalse(r.isDita());
        assertNotEquals("DITA", p.getProjectType());
        assertTrue(p.getDefaultRootMap() == null || p.getDefaultRootMap().isEmpty());
        assertFalse(dir.resolve(".dogsbay/config.xml").toFile().exists());
    }

    @Test
    void syncFromConfig_isIdempotent_secondCallNoOp() throws Exception {
        write(".dogsbay/config.xml",
                "<dogsbay-project><project-type>DITA</project-type>"
                + "<default-root-map>m.ditamap</default-root-map></dogsbay-project>");
        ProjectProperties p = projectForTempDir();

        assertTrue(ProjectAutoConfigurer.syncFromConfig(p).mutated());
        // Second activation: fields already set → no mutation (retry-safe, no churn).
        assertFalse(ProjectAutoConfigurer.syncFromConfig(p).mutated());
    }
}
