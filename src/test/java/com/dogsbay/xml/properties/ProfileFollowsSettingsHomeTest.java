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
package com.dogsbay.xml.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.acp.AgentInstaller;
import com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry;
import com.dogsbay.dogsbayaieditor.ipc.DiscoveryFile;

/**
 * A run given a settings folder of its own touches nothing of the user's.
 *
 * <p>The settings file was only part of it. The note saying an editor is running, the register of running previews and the
 * installed agents all used to build their own path from the home directory, so a "clean" run still shared them: a recording
 * could stop the user's preview, or install an agent into their profile, and nobody would see why.
 */
class ProfileFollowsSettingsHomeTest {

    @AfterEach
    void restore() {
        SettingsHome.useDefault();
    }

    @Test
    void byDefaultEverythingSitsUnderTheDogsbayFolderInTheUserHome() {
        Path home = Path.of(System.getProperty("user.home"), ".dogsbay");
        assertThat(SettingsHome.dir()).isEqualTo(home);
        assertThat(DiscoveryFile.getDiscoveryDir()).isEqualTo(home);
        assertThat(PreviewRegistry.dir()).isEqualTo(home.resolve("previews"));
        assertThat(AgentInstaller.home()).isEqualTo(home.resolve("agents"));
    }

    @Test
    void aRunWithItsOwnFolderKeepsAllOfItThere(@TempDir Path profile) {
        SettingsHome.setRoot(profile.toString());

        assertThat(SettingsHome.dir()).isEqualTo(profile);
        // the editor's own note about how to reach it: two editors writing the user's would confuse later commands
        assertThat(DiscoveryFile.getDiscoveryDir()).isEqualTo(profile);
        // previews are claimed in this register, so sharing it lets one run stop another's preview
        assertThat(PreviewRegistry.dir()).isEqualTo(profile.resolve("previews"));
        // and an agent installed by a scripted run should not appear in the user's editor
        assertThat(AgentInstaller.home()).isEqualTo(profile.resolve("agents"));
    }

    @Test
    void anExplicitOverrideStillWinsOverTheSettingsFolder(@TempDir Path profile, @TempDir Path elsewhere) {
        SettingsHome.setRoot(profile.toString());
        System.setProperty("dogsbay.previews.dir", elsewhere.toString());
        try {
            // the per-thing properties exist for tests and sandboxes, and they stay the last word
            assertThat(PreviewRegistry.dir()).isEqualTo(elsewhere);
        } finally {
            System.clearProperty("dogsbay.previews.dir");
        }
        assertThat(PreviewRegistry.dir()).isEqualTo(profile.resolve("previews"));
    }
}
