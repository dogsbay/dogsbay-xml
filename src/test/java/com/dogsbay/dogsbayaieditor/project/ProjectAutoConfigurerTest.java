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

    @Test
    void topicsWithNoMapYet_stillGetAConfig() throws Exception {
        // The state every project is in for its first few edits: a topic exists, the map does not. Waiting for a
        // map left the folder type "None", and with it every DITA command, for the rest of the session.
        write("topics/intro.dita", "<concept id=\"intro\"/>");

        ProjectProperties p = projectForTempDir();
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configure(p, null);

        assertTrue(r.isDita());
        assertTrue(r.wroteConfig());
        assertNull(r.rootMap(), "no map to find yet");
        assertEquals("DITA", p.getProjectType());
        String xml = Files.readString(dir.resolve(".dogsbay/config.xml"));
        assertTrue(xml.contains("<project-type>DITA</project-type>"), xml);
        assertFalse(xml.contains("default-root-map"), "no map named, rather than an empty one: " + xml);
    }

    @Test
    void aMaterializedConfigNamesItsLineEndings() throws Exception {
        // The formatter leaves a file's line endings alone unless the project names them, so a project that says
        // nothing never converts anything. A project the editor sets up says what it wants, in a file you can read.
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));

        ProjectAutoConfigurer.configure(projectForTempDir(), null);

        String xml = Files.readString(dir.resolve(".dogsbay/config.xml"));
        assertTrue(xml.contains("<format-style"), xml);
        assertTrue(xml.contains("newline=\"lf\""), "the default is declared, not assumed: " + xml);
        assertTrue(com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load(dir).isNewlineDeclared());
    }

    @Test
    void anExistingConfigThatNamesNoLineEndingsKeepsQuiet() throws Exception {
        // The other half of the rule: we declare the default for a project we are creating, never for one someone
        // else wrote. Adding newline="lf" here would turn every CRLF file in their project into a whole-file diff.
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        write(".dogsbay/config.xml",
                "<dogsbay-project><project-type>DITA</project-type>"
                + "<default-root-map>guide.ditamap</default-root-map>"
                + "<format-style indent=\"spaces\" size=\"2\"/></dogsbay-project>");

        ProjectAutoConfigurer.configure(projectForTempDir(), null);

        assertFalse(Files.readString(dir.resolve(".dogsbay/config.xml")).contains("newline="),
                "someone else's config must not start declaring line endings");
        assertFalse(com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load(dir).isNewlineDeclared());
    }

    @Test
    void anExplicitNoneIsAChoice_andIsLeftAlone() throws Exception {
        // An unset type reads as blank and is filled; None that someone selected is a decision, and filling
        // unset fields must not overrule it. (getProjectType() also answers "None" for a null, which is not the
        // unset case: an absent element reads as blank.)
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        ProjectProperties p = projectForTempDir();
        p.setProjectType(ProjectProperties.TYPE_NONE);

        ProjectAutoConfigurer.configure(p, null);

        assertEquals(ProjectProperties.TYPE_NONE, p.getProjectType());
    }

    // ── configureFolder: the first .dita makes the folder a project ──

    @Test
    void configureFolder_configuresTheProjectForThatFolder() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"/>");
        ProjectProperties p = projectForTempDir();
        com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties config = editorSettings();
        config.addProjectProperties(p);

        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configureFolder(config, dir.toFile());

        assertTrue(r.isDita());
        assertEquals("DITA", storedProject(config).getProjectType());
    }

    @Test
    void configureFolder_doesNothingForAFolderWithNoProject() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"/>");
        com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties config = editorSettings();

        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configureFolder(config, dir.toFile());

        assertFalse(r.isDita());
        assertFalse(dir.resolve(".dogsbay/config.xml").toFile().exists(),
                "a folder nobody opened is not ours to configure");
    }

    @Test
    void configureFolder_isCheapOnAProjectAlreadyConfigured() throws Exception {
        write("guide.ditamap", map("<topicref href=\"a.dita\"/>"));
        ProjectProperties p = projectForTempDir();
        p.setProjectType("DITA");
        com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties config = editorSettings();
        config.addProjectProperties(p);

        // Called on every file creation, so a configured project must cost nothing and write nothing.
        ProjectAutoConfigurer.Result r = ProjectAutoConfigurer.configureFolder(config, dir.toFile());

        assertFalse(r.changedAnything());
        assertFalse(dir.resolve(".dogsbay/config.xml").toFile().exists());
    }

    /** An editor settings document backed by a file in the temp dir: the configurer only reads the project list. */
    private com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties editorSettings() throws Exception {
        com.dogsbay.xml.XElement root = new com.dogsbay.xml.XElement("dogsbay");
        root.setText("\n");
        return new com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties(
                new com.dogsbay.xml.DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
    }

    /** The stored project, which is a clone of the one added — the settings document's copy. */
    private static ProjectProperties storedProject(
            com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties config) {
        return (ProjectProperties) config.getProjectProperties().elementAt(0);
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
