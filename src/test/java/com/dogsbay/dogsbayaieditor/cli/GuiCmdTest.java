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
package com.dogsbay.dogsbayaieditor.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.Main;
import com.dogsbay.dogsbayaieditor.terminal.TerminalSettings;
import com.dogsbay.dogsbayaieditor.commands.UiDriving;
import com.dogsbay.dogsbayaieditor.properties.StartupOverrides;
import com.dogsbay.xml.properties.SettingsHome;

import picocli.CommandLine;

class GuiCmdTest {

    private final List<String> launched = new ArrayList<>();

    @AfterEach
    void restore() {
        SettingsHome.useDefault();
        StartupOverrides.clearPending();
        GuiCmd.forgetWindow();
        Main.setOwnWindow(false);
        DogsBayAIEditor.setTitleOverride(null);
        TerminalSettings.useDefaultFont();
        UiDriving.allow(false);
    }

    /** Run the command as the command line would, with the window replaced by a note of what it was asked to open. */
    private int run(String... args) {
        GuiCmd cmd = new GuiCmd();
        cmd.setLauncher(file -> launched.add(String.valueOf(file)));
        return new CommandLine(cmd).execute(args);
    }

    @Test
    void withNoOptionsItOpensTheWindowAndChangesNothing(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("topic.dita"), "<topic/>");
        assertThat(run(file.toString())).isZero();
        assertThat(launched).containsExactly(file.toAbsolutePath().toString());
        assertThat(SettingsHome.isOverridden()).isFalse();
        assertThat(StartupOverrides.pending().isEmpty()).isTrue();
    }

    @Test
    void opensWithNoFileAtAll() {
        assertThat(run()).isZero();
        assertThat(launched).containsExactly("null");
    }

    @Test
    void keepsSettingsInTheFolderItIsGiven(@TempDir Path tmp) {
        Path settings = tmp.resolve("profile");
        assertThat(run("--settings", settings.toString())).isZero();
        assertThat(SettingsHome.isOverridden()).isTrue();
        assertThat(SettingsHome.root()).isEqualTo(settings.toAbsolutePath() + File.separator);
        assertThat(settings).exists(); // made for us, since a recording starts from nothing
    }

    @Test
    void setsTheFolderBeforeTheWindowOpens(@TempDir Path tmp) {
        Path settings = tmp.resolve("profile");
        GuiCmd cmd = new GuiCmd();
        List<String> rootWhenLaunched = new ArrayList<>();
        cmd.setLauncher(file -> rootWhenLaunched.add(SettingsHome.root()));
        new CommandLine(cmd).execute("--settings", settings.toString());
        // the ordering is the point: a setting read during startup must already come from here
        assertThat(rootWhenLaunched).containsExactly(settings.toAbsolutePath() + File.separator);
    }

    @Test
    void passesTheProjectAndTheQuietStartupOn(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectory(tmp.resolve("audacity"));
        assertThat(run("--project", project.toString(), "--no-welcome", "--no-session-restore")).isZero();
        StartupOverrides waiting = StartupOverrides.pending();
        assertThat(waiting.project()).isEqualTo(project.toAbsolutePath().toString());
        assertThat(waiting.welcome()).isFalse();
        assertThat(waiting.reopenSessionFiles()).isFalse();
    }

    @Test
    void leavesUnmentionedSettingsAlone(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectory(tmp.resolve("p"));
        assertThat(run("--project", project.toString())).isZero();
        StartupOverrides waiting = StartupOverrides.pending();
        // null, not false: a run that did not mention the welcome screen must not turn it off
        assertThat(waiting.welcome()).isNull();
        assertThat(waiting.reopenSessionFiles()).isNull();
    }

    @Test
    void asksForCleanXmlWhenTold(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectory(tmp.resolve("p"));
        assertThat(run("--project", project.toString(), "--no-change-marks")).isZero();
        // false, so an agent's edits land as plain DITA: no rev attribute, no previous-value markup
        assertThat(StartupOverrides.pending().markChanges()).isFalse();
    }

    @Test
    void leavesChangeMarkingAloneWhenNotMentioned(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectory(tmp.resolve("p"));
        assertThat(run("--project", project.toString())).isZero();
        // the default is the writer's: an agent's changes are marked for review unless someone says otherwise
        assertThat(StartupOverrides.pending().markChanges()).isNull();
    }

    @Test
    void isReachableAsASubcommandOfTheToolItself() {
        // the tests above build the command directly, which would still pass if it were never registered
        assertThat(DogsBayCli.commandLine().getSubcommands()).containsKey("gui");
    }

    @Test
    void saysAWindowWasStartedSoTheProcessIsNotEndedUnderIt(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("t.dita"), "<topic/>");
        assertThat(GuiCmd.startedWindow()).isFalse();
        assertThat(run(file.toString())).isZero();
        // every other subcommand has finished by the time it returns; this one leaves an editor running behind it
        assertThat(GuiCmd.startedWindow()).isTrue();
    }

    @Test
    void aConfiguredRunAsksForAWindowOfItsOwn(@TempDir Path tmp) {
        assertThat(run("--settings", tmp.resolve("profile").toString())).isZero();
        // otherwise the file goes to an editor already running, under the user's settings, and the options are lost
        assertThat(Main.isOwnWindow()).isTrue();
    }

    @Test
    void aPlainRunStillJoinsTheEditorAlreadyOpen(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("t.dita"), "<topic/>");
        assertThat(run(file.toString())).isZero();
        assertThat(Main.isOwnWindow()).isFalse();
    }

    @Test
    void keepsItsFlagsOutOfTheUsersSettingsUnlessItHasItsOwnFolder(@TempDir Path tmp) throws Exception {
        assertThat(run("--no-change-marks")).isZero();
        assertThat(StartupOverrides.pending().persists()).isFalse();

        assertThat(run("--settings", tmp.resolve("profile").toString(), "--no-change-marks")).isZero();
        assertThat(StartupOverrides.pending().persists()).isTrue();
    }

    @Test
    void aSecondRunWithoutASettingsFolderDoesNotInheritTheFirstOnes(@TempDir Path tmp) {
        assertThat(run("--settings", tmp.resolve("a").toString())).isZero();
        assertThat(SettingsHome.isOverridden()).isTrue();

        assertThat(run()).isZero();

        // the process is shared; the folder is not
        assertThat(SettingsHome.isOverridden()).isFalse();
    }

    @Test
    void takesAWindowSize() {
        assertThat(run("--window", "1920x1080")).isZero();
        assertThat(StartupOverrides.pending().window()).isEqualTo(new Dimension(1920, 1080));
        assertThat(Main.isOwnWindow()).isTrue(); // a size is a configured run like any other
    }

    @Test
    void refusesASizeThatIsNotOneAndOpensNoWindow() {
        for (String bad : new String[] { "1920", "1920x", "x1080", "0x1080", "1920x0", "-10x20", "wide x tall", "1920X1080" }) {
            GuiCmd cmd = new GuiCmd();
            cmd.setLauncher(file -> launched.add(String.valueOf(file)));
            assertThat(new CommandLine(cmd).execute("--window", bad)).as(bad).isEqualTo(1);
        }
        assertThat(launched).isEmpty();
        assertThat(StartupOverrides.pending()).isNull(); // refused before anything was decided
    }

    @Test
    void readsASizeWrittenWithSpaces() {
        assertThat(GuiCmd.parseSize(" 1280 x 720 ")).isEqualTo(new Dimension(1280, 720));
    }

    @Test
    void showsAFixedTitleInsteadOfTheOpenFile() {
        assertThat(run("--title", "Audacity User Guide")).isZero();
        assertThat(DogsBayAIEditor.titleOverride()).isEqualTo("Audacity User Guide");
        assertThat(Main.isOwnWindow()).isTrue();
    }

    @Test
    void withoutATitleNothingIsOverridden() {
        assertThat(run()).isZero();
        assertThat(DogsBayAIEditor.titleOverride()).isNull();
    }

    @Test
    void setsTheTerminalFont() {
        assertThat(run("--terminal-font", "18")).isZero();
        assertThat(TerminalSettings.family()).isEqualTo(TerminalSettings.DEFAULT_FAMILY); // size alone keeps the family
        assertThat(TerminalSettings.size()).isEqualTo(18);

        assertThat(run("--terminal-font", "DejaVu Sans Mono,20")).isZero();
        assertThat(TerminalSettings.family()).isEqualTo("DejaVu Sans Mono");
        assertThat(TerminalSettings.size()).isEqualTo(20);
    }

    @Test
    void refusesAFontThatIsNotOne() {
        for (String bad : new String[] { "big", "0", "-4", ",18", "Mono,", "Mono,big" }) {
            GuiCmd cmd = new GuiCmd();
            cmd.setLauncher(file -> launched.add(String.valueOf(file)));
            assertThat(new CommandLine(cmd).execute("--terminal-font", bad)).as(bad).isEqualTo(1);
        }
        assertThat(launched).isEmpty();
        assertThat(TerminalSettings.size()).isEqualTo(TerminalSettings.DEFAULT_SIZE);
        assertThat(StartupOverrides.pending()).isNull();
    }

    @Test
    void drivingIsOffUnlessAskedFor() {
        assertThat(run()).isZero();
        assertThat(UiDriving.isAllowed()).isFalse();

        assertThat(run("--allow-driving")).isZero();
        assertThat(UiDriving.isAllowed()).isTrue();
        assertThat(Main.isOwnWindow()).isTrue(); // an editor meant to be driven is not the user's own window
    }

    @Test
    void refusesAProjectThatIsNotAFolderAndDoesNotOpenAWindow(@TempDir Path tmp) throws Exception {
        Path notAFolder = Files.writeString(tmp.resolve("file.txt"), "x");
        assertThat(run("--project", notAFolder.toString())).isEqualTo(1);
        assertThat(launched).isEmpty();
        assertThat(StartupOverrides.pending()).isNull();
    }

    @Test
    void refusesAFileThatIsNotThere(@TempDir Path tmp) {
        assertThat(run(tmp.resolve("missing.dita").toString())).isEqualTo(1);
        assertThat(launched).isEmpty();
    }

    @Test
    void refusesASettingsPathThatIsAFileAndLeavesTheDefaultAlone(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("not-a-folder"), "x");
        assertThat(run("--settings", file.toString())).isEqualTo(1);
        assertThat(SettingsHome.isOverridden()).isFalse();
        assertThat(launched).isEmpty();
    }
}
