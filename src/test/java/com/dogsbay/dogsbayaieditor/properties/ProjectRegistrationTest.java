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
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * Registering a project stores a <em>clone</em> of its element, so the object
 * handed in is left detached from the settings document. A caller that
 * configured a project after registering it — which is exactly what opening a
 * folder does — wrote its type, root map and framework to nothing, and the
 * project came back as "None" for all three.
 */
class ProjectRegistrationTest {

    @TempDir Path dir;

    private ConfigurationProperties settings() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        return new ConfigurationProperties(new DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
    }

    private ProjectProperties storedProject(ConfigurationProperties settings, String name) {
        Vector projects = settings.getProjectProperties();
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties p = (ProjectProperties) projects.elementAt(i);
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    @Test
    @DisplayName("what registering hands back is the project in the settings, not a detached copy")
    void registeringHandsBackTheStoredProject() throws Exception {
        ConfigurationProperties settings = settings();
        ProjectProperties fresh = new ProjectProperties("audacity-demo");
        fresh.setFolderPath("/tmp/audacity-demo");

        ProjectProperties stored = settings.addProjectProperties(fresh);
        // What the auto-configurer does straight after the folder is registered.
        stored.setProjectType("DITA");
        stored.setDefaultRootMap("audacity-guide.ditamap");
        stored.setFrameworkName("DITA-OT 4.3.5");

        ProjectProperties readBack = storedProject(settings, "audacity-demo");
        assertThat(readBack).isNotNull();
        assertThat(readBack.getProjectType()).isEqualTo("DITA");
        assertThat(readBack.getDefaultRootMap()).isEqualTo("audacity-guide.ditamap");
        assertThat(readBack.getFrameworkName()).isEqualTo("DITA-OT 4.3.5");
    }

    @Test
    @DisplayName("the object handed in stays detached, which is why the return value matters")
    void theObjectHandedInIsNotTheStoredOne() throws Exception {
        ConfigurationProperties settings = settings();
        ProjectProperties fresh = new ProjectProperties("audacity-demo");
        fresh.setFolderPath("/tmp/audacity-demo");

        settings.addProjectProperties(fresh);
        fresh.setProjectType("DITA");   // the old bug, written to nothing

        assertThat(storedProject(settings, "audacity-demo").getProjectType()).isNullOrEmpty();
    }

    @Test
    @DisplayName("registering a second project hands back that one, not the first")
    void aSecondProjectIsItsOwn() throws Exception {
        ConfigurationProperties settings = settings();
        ProjectProperties first = new ProjectProperties("one");
        first.setFolderPath("/tmp/one");
        settings.addProjectProperties(first);

        ProjectProperties second = new ProjectProperties("two");
        second.setFolderPath("/tmp/two");
        ProjectProperties stored = settings.addProjectProperties(second);
        stored.setProjectType("DITA");

        assertThat(stored.getName()).isEqualTo("two");
        assertThat(storedProject(settings, "two").getProjectType()).isEqualTo("DITA");
        assertThat(storedProject(settings, "one").getProjectType()).isNullOrEmpty();
    }
}
