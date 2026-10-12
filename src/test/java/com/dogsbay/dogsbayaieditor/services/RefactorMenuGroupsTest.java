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

package com.dogsbay.dogsbayaieditor.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The Refactor menu, read in order and in groups.
 *
 * <p>It was ten flat items with a single separator, so nothing said which commands belonged together and a new
 * one was appended wherever the last edit ended. The groups are by what each command changes — files and the
 * links into them, keys, reuse within a topic, conditions — and the report is last, below its own separator,
 * being the only item that changes nothing.
 *
 * <p>{@code MenuBuilder} needs a full application frame, so this reads the source, as {@link HelpMenuTest} does.
 */
class RefactorMenuGroupsTest {

    private static final Pattern ITEM_OR_SEPARATOR = Pattern.compile(
            "new JMenuItem\\(\"([^\"]+)\"|refactorMenu\\.addSeparator\\(\\)");

    /** The menu as a reader sees it, "-" for each separator. */
    private static List<String> refactorMenu() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/dogsbay/dogsbayaieditor/services/MenuBuilder.java"));
        int from = source.indexOf("private JMenu createRefactorMenu()");
        assertThat(from).describedAs("cannot find createRefactorMenu").isNotNegative();
        int to = source.indexOf("return refactorMenu;", from);

        List<String> lines = new ArrayList<>();
        Matcher m = ITEM_OR_SEPARATOR.matcher(source.substring(from, to));
        while (m.find()) {
            lines.add(m.group(1) == null ? "-" : m.group(1));
        }
        return lines;
    }

    @Test
    void theCommandsAreGroupedByWhatTheyChange() throws Exception {
        assertThat(refactorMenu()).containsExactly(
                // files, and the references into them
                "Rename/Move File with References...",
                "Retarget References...",
                "Split Topic by Sections...",
                "-",
                // keys, in the order you meet them
                "Create Key from Selected Text...",
                "Rename Key...",
                "Merge Duplicate Keydefs...",
                "-",
                // reuse within a topic
                "Extract Element to Conref...",
                "Rename Element Id...",
                "-",
                // attributes
                "Rename Profiling Value...",
                "Set Attribute...",
                "-",
                // the report
                "Find Broken References and Orphans...");
    }

    @Test
    void everyMnemonicIsDistinct() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/dogsbay/dogsbayaieditor/services/MenuBuilder.java"));
        int from = source.indexOf("private JMenu createRefactorMenu()");
        int to = source.indexOf("return refactorMenu;", from);

        List<String> keys = new ArrayList<>();
        Matcher m = Pattern.compile("new JMenuItem\\(\"[^\"]+\", '(.)'\\)")
                .matcher(source.substring(from, to));
        while (m.find()) {
            keys.add(m.group(1));
        }

        // Eleven items, eleven letters. Two items sharing one makes the first unreachable from the keyboard, which
        // nothing else here would catch — the rename that prompted these groups first took 'F', already taken
        // by Rename/Move File.
        assertThat(keys).hasSize(11).doesNotHaveDuplicates();
    }

    @Test
    void theReportIsNamedForWhatItFindsNotForProjectHealth() throws Exception {
        List<String> menu = refactorMenu();

        // "Project Health Report" is the larger pre-publish report — Project ▸ Check Project, and project-health
        // on the CLI. This command runs HealthCommand, the reuse subset. One name for two scopes sent readers
        // looking for the gate in the wrong menu.
        assertThat(menu).last().isEqualTo("Find Broken References and Orphans...");
        assertThat(menu).noneMatch(item -> item.contains("Project Health"));
    }
}
