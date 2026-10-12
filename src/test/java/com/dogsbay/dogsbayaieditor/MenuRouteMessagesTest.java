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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * A dialog that tells you where to go has to name something that is there.
 *
 * <p>Thirteen messages said "Open a project folder first (File → Open Folder)" while the File menu has always
 * read <em>Open Project Folder…</em>. Nothing caught it, because the route is a string in one file and the label
 * is a string in another — a reader following the instruction finds no such command, which is worse than a
 * message that says nothing about how to fix the problem.
 *
 * <p>This reads the sources: {@code MenuBuilder} and the actions need a full application frame, so there is no
 * cheap way to ask a live menu what its items are called.
 */
class MenuRouteMessagesTest {

    /** "File → Something" or "File > Something" inside a string literal: a message, not a comment. */
    private static final Pattern ROUTE =
            Pattern.compile("\"[^\"\\n]*File\\s*(?:→|->|>)\\s*([A-Z][A-Za-z ]*[A-Za-z])");

    /** Where a route's first segment is defined: the action's own NAME. */
    private static final List<String> FILE_MENU_LABELS = List.of(
            "Open Project Folder", "New", "Open", "Save", "Save As", "Save All", "Preferences",
            "Close", "Close All", "Exit", "New Project", "Open Recent", "Open Sample Project");

    private static List<Path> mainSources() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void everyFileMenuRouteNamesAnItemTheFileMenuHas() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path source : mainSources()) {
            Matcher m = ROUTE.matcher(Files.readString(source));
            while (m.find()) {
                String named = m.group(1).trim();
                if (!FILE_MENU_LABELS.contains(named)) {
                    wrong.add(source.getFileName() + ": \"File → " + named + "\"");
                }
            }
        }
        assertThat(wrong)
                .describedAs("a message names a File menu item that does not exist; either fix the message or "
                        + "add the new label to FILE_MENU_LABELS")
                .isEmpty();
    }

    @Test
    void theFolderCommandIsStillCalledWhatTheMessagesSayItIs() throws IOException {
        String action = Files.readString(
                Path.of("src/main/java/com/dogsbay/dogsbayaieditor/OpenFolderAction.java"));

        // The label the thirteen messages, the Welcome panel button and the empty File Explorer all quote.
        assertThat(action).contains("super(\"Open Project Folder...\")");
    }

    @Test
    void theButtonsThatOpenAFolderAreCalledTheSameThing() throws IOException {
        // Same action, same words: the Welcome panel and the empty-state File Explorer both fire
        // getOpenFolderAction, and a button reading "Open Folder…" beside a menu reading "Open Project Folder…"
        // reads as two different commands.
        for (String path : new String[] {
                "src/main/java/com/dogsbay/dogsbayaieditor/WelcomePanel.java",
                "src/main/java/com/dogsbay/dogsbayaieditor/explorer/FileExplorerPanel.java" }) {
            String source = Files.readString(Path.of(path));
            assertThat(source)
                    .describedAs("%s", path)
                    .contains("\"Open Project Folder…\"")
                    .doesNotContain("\"Open Folder…\"");
        }
    }
}
