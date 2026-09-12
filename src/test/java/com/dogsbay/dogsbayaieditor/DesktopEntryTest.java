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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GNOME Shell does not take an application's icon off the window. For the
 * overview, the dash and alt-tab it matches the window to a desktop entry and
 * uses that entry's icon, so a perfectly good {@code _NET_WM_ICON} still shows
 * as a generic cog when nothing matches.
 *
 * <p>
 * The match is on WM_CLASS, which AWT derives from the main class by replacing
 * the dots with dashes. That derivation is not ours to change, so the desktop
 * entry has to name it — and this pins the name to the real main class, so
 * moving {@code Main} fails here rather than in someone's task switcher.
 */
class DesktopEntryTest {

    private static final Path ENTRY = Path.of("build.gradle.kts");

    /** What AWT will put in WM_CLASS for the class that starts the editor. */
    private static String expectedWmClass() {
        return Main.class.getName().replace('.', '-');
    }

    @Test
    @DisplayName("the desktop entry claims the WM_CLASS the editor actually gets")
    void theEntryMatchesTheMainClass() throws Exception {
        String build = Files.readString(ENTRY);

        assertThat(build)
                .as("StartupWMClass for " + Main.class.getName())
                .contains("val guiWmClass = \"" + expectedWmClass() + "\"");
    }

    @Test
    @DisplayName("the entry is written under the launcher's own name")
    void theEntryIsNamedAfterTheLauncher() throws Exception {
        String build = Files.readString(ENTRY);

        // jpackage looks an override up by launcher name; a different file name
        // is silently ignored and the default template wins.
        assertThat(build).contains("imageName = \"DogsBay-XML-Editor\"");
        assertThat(build).contains("resolve(\"DogsBay-XML-Editor.desktop\")");
    }

    @Test
    @DisplayName("the generated entry carries the icon and the WM class")
    void theGeneratedEntryIsComplete() throws Exception {
        Path generated = Path.of("build/jpackage-resources/DogsBay-XML-Editor.desktop");
        if (!Files.exists(generated)) {
            return;   // not generated in this build; the source assertions above stand
        }
        String entry = Files.readString(generated);

        assertThat(entry).contains("Icon=APPLICATION_ICON");
        assertThat(entry).contains("StartupWMClass=" + expectedWmClass());
        assertThat(entry).doesNotContain("NoDisplay=true");   // the GUI belongs on the menu
    }

    @Test
    @DisplayName("the app launcher cannot collide with the CLI launcher")
    void theTwoLaunchersDifferByMoreThanCase() throws Exception {
        String build = Files.readString(ENTRY);
        String image = between(build, "imageName = \"", "\"");
        String cli = between(build, "\"--add-launcher\", \"", "=");

        // They shared a name but for its case, so a case-insensitive filesystem
        // saw one path: jpackage wrote the app launcher and then failed creating
        // the CLI one. Linux builds fine and macOS and Windows do not.
        assertThat(image.toLowerCase(java.util.Locale.ROOT))
                .as("app image vs CLI launcher, ignoring case")
                .isNotEqualTo(cli.toLowerCase(java.util.Locale.ROOT));
    }

    private static String between(String text, String open, String close) {
        int from = text.indexOf(open);
        assertThat(from).as("found " + open).isNotNegative();
        from += open.length();
        return text.substring(from, text.indexOf(close, from));
    }
}
