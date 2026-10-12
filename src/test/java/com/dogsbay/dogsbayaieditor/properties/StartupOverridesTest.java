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

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.xml.XElement;

class StartupOverridesTest {

    @TempDir
    Path dir;

    @AfterEach
    void restore() {
        StartupOverrides.clearPending();
    }

    private ConfigurationProperties settings() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        return new ConfigurationProperties(new DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
    }

    @Test
    void cleanXmlReachesTheSettingThatDecidesIt() throws Exception {
        ConfigurationProperties settings = settings();
        assertThat(settings.isAgentProposalsEnabled()).isTrue(); // the writer's default: an agent's changes are marked

        new StartupOverrides(null, null, null, Boolean.FALSE, true).applyTo(settings);

        // which is what keeps rev and the previous-value markup out of a document an agent writes to
        assertThat(settings.isAgentProposalsEnabled()).isFalse();
    }

    @Test
    void appliesTheOthersToo() throws Exception {
        ConfigurationProperties settings = settings();
        Path project = Files.createDirectory(dir.resolve("audacity"));

        new StartupOverrides(project.toString(), Boolean.FALSE, Boolean.FALSE, null, true).applyTo(settings);

        assertThat(settings.isShowWelcomeOnStartup()).isFalse();
        assertThat(settings.isReopenSessionFiles()).isFalse();
        // the folder the editor actually opens, chosen from the registered projects by this value
        assertThat(settings.getLastOpenedFolder()).isEqualTo(project.toString());
    }

    @Test
    void registersAFolderTheEditorHasNeverHeardOfSoThereIsSomethingToOpen() throws Exception {
        ConfigurationProperties settings = settings();
        Path project = Files.createDirectory(dir.resolve("brand-new"));
        assertThat(settings.getProjectProperties()).isEmpty();

        new StartupOverrides(project.toString(), null, null, null, true).applyTo(settings);

        // without this the editor matches the folder against its registered projects, finds none, and opens nothing
        assertThat(settings.getProjectProperties()).hasSize(1);
        ProjectProperties registered = (ProjectProperties) settings.getProjectProperties().get(0);
        assertThat(registered.getFolderPath()).isEqualTo(project.toString());
        assertThat(settings.getLastOpenedFolder()).isEqualTo(project.toString());
    }

    @Test
    void reusesAProjectAlreadyRegisteredForThatFolder() throws Exception {
        ConfigurationProperties settings = settings();
        Path project = Files.createDirectory(dir.resolve("known"));
        ProjectProperties existing = new ProjectProperties("Audacity Guide");
        existing.setFolderPath(project.toString());
        settings.addProjectProperties(existing);

        new StartupOverrides(project.toString(), null, null, null, true).applyTo(settings);

        // the same project, not a second one under another name pointing at the same folder
        assertThat(settings.getProjectProperties()).hasSize(1);
    }

    @Test
    void turningSomethingOnIsCarriedThroughAsWellAsTurningItOff() throws Exception {
        ConfigurationProperties settings = settings();
        settings.setShowWelcomeOnStartup(false);
        settings.setAgentProposalsEnabled(false);

        new StartupOverrides(null, Boolean.TRUE, null, Boolean.TRUE, true).applyTo(settings);

        // the value is passed, not assumed: an override that could only ever turn things off would pass a weaker test
        assertThat(settings.isShowWelcomeOnStartup()).isTrue();
        assertThat(settings.isAgentProposalsEnabled()).isTrue();
    }

    @Test
    void withoutASettingsFolderOfItsOwnNothingItChangesCanReachTheFile() throws Exception {
        ConfigurationProperties settings = settings();

        new StartupOverrides(null, null, null, Boolean.FALSE, false).applyTo(settings);

        // read-only, so the editor saving on exit cannot leave this run's flag in the user's settings for good
        assertThat(settings.isReadOnly()).isTrue();
        assertThat(settings.isAgentProposalsEnabled()).isFalse(); // but it does apply to this run
    }

    @Test
    void changesNothingItWasNotAskedAbout() throws Exception {
        ConfigurationProperties settings = settings();
        settings.setAgentProposalsEnabled(true);
        settings.setShowWelcomeOnStartup(true);

        assertThat(new StartupOverrides(null, null, Boolean.FALSE, null, true).applyTo(settings)).isTrue();

        // the point: a run that mentioned only the session must leave the rest as the file has it
        assertThat(settings.isAgentProposalsEnabled()).isTrue();
        assertThat(settings.isShowWelcomeOnStartup()).isTrue();
        assertThat(settings.isReopenSessionFiles()).isFalse();
    }

    @Test
    @DisabledIf(value = "isHeadless", disabledReason = "the stored size is read back clamped to the screen, which needs a display")
    void aWindowSizeIsAppliedAndStopsTheWindowBeingMaximised() throws Exception {
        ConfigurationProperties settings = settings();
        settings.setWindowMaximised(true);

        new StartupOverrides(null, null, null, null, new Dimension(640, 480), true).applyTo(settings);

        // both halves matter: a maximised window would ignore the size entirely
        assertThat(settings.getDimension()).isEqualTo(new Dimension(640, 480));
        assertThat(settings.isWindowMaximised()).isFalse();
    }

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @Test
    void doesNothingWhenNothingWasAsked() throws Exception {
        ConfigurationProperties settings = settings();
        assertThat(StartupOverrides.none().isEmpty()).isTrue();
        assertThat(StartupOverrides.none().applyTo(settings)).isFalse();
    }

    @Test
    void applyingWhatIsPendingIsSafeWhenNothingIs() throws Exception {
        ConfigurationProperties settings = settings();
        assertThat(StartupOverrides.applyPending(settings)).isFalse();

        StartupOverrides.setPending(new StartupOverrides(null, null, null, Boolean.FALSE, true));
        assertThat(StartupOverrides.applyPending(settings)).isTrue();
        assertThat(settings.isAgentProposalsEnabled()).isFalse();
    }
}
