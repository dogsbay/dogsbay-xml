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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;

/** Which project the editor reopens, and that what it remembers cannot disagree with itself. */
class LastProjectTest {

    @TempDir Path dir;

    private static ProjectProperties project(String name, String folder) {
        ProjectProperties p = new ProjectProperties(name);
        p.setFolderPath(folder);
        return p;
    }

    private final ProjectProperties real = project("dita-ot-docs", "/home/w/github/dita-ot-docs");
    private final ProjectProperties scratch = project("scratch-otdocs", "/home/w/scratch-otdocs");
    private final List<ProjectProperties> projects = List.of(real, scratch);

    @Test
    void reopensTheFolderThatWasOpen() {
        assertThat(LastProject.choose(projects, "/home/w/scratch-otdocs", "scratch-otdocs", null)).isSameAs(scratch);
        assertThat(LastProject.choose(projects, "/home/w/scratch-otdocs/", null, null)).isSameAs(scratch);
    }

    @Test
    void fallsBackToTheNameWhenTheFolderIsNotAProject() {
        assertThat(LastProject.choose(projects, "/somewhere/else", "scratch-otdocs", null)).isSameAs(scratch);
        assertThat(LastProject.choose(projects, null, null, null)).isNull();
    }

    @Test
    void settlesSettingsThatDisagreeByWhereTheWriterWasWorking() {
        // What an older version left behind: the folder from Open Recent or an agent's
        // open_project, the name and the open documents from the project actually in use.
        assertThat(LastProject.choose(projects, "/home/w/github/dita-ot-docs", "scratch-otdocs",
                "/home/w/scratch-otdocs/topics/using-dita-command.dita")).isSameAs(scratch);
        // Without a document to go by, the folder still decides.
        assertThat(LastProject.choose(projects, "/home/w/github/dita-ot-docs", "scratch-otdocs", null)).isSameAs(real);
    }

    @Test
    void readsTheActiveDocumentAsTheSettingsStoreIt() {
        // Session documents are stored as a URL's path: percent-encoded, /C:/ on Windows (review, 2026-09-18)
        ProjectProperties spaced = project("my docs", "/home/w/My Docs");
        assertThat(LastProject.choose(List.of(real, spaced), "/home/w/github/dita-ot-docs", "my docs",
                "/home/w/My%20Docs/topics/a.dita")).isSameAs(spaced);
        assertThat(LastProject.normal("/C:/Users/w/a.dita")).isNotNull();
        assertThat(LastProject.normal("")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rememberingAProjectRecordsItsFolderAndNameTogether() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        ConfigurationProperties settings = new ConfigurationProperties(
                new DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
        settings.addProjectProperties(project("scratch-otdocs", dir.resolve("scratch").toString()));
        settings.addProjectProperties(project("dita-ot-docs", dir.resolve("real").toString()));

        settings.rememberOpenProject(dir.resolve("scratch").toString());
        settings.rememberOpenProject(dir.resolve("real").toString());
        assertThat(settings.getLastOpenedFolder()).isEqualTo(dir.resolve("real").toString());
        assertThat(settings.getLastProjectName()).as("the name moves with the folder").isEqualTo("dita-ot-docs");

        settings.rememberOpenProject(dir.resolve("unregistered").toString());
        assertThat(settings.getLastProjectName()).as("no stale name from the previous project").isEmpty();
        Vector<String> recent = settings.getLastOpenedProjects();
        assertThat(recent).contains(dir.resolve("unregistered").toString());
    }
}
