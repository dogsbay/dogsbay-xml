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

package com.dogsbay.dogsbayaieditor.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Opening a diff for a file that already has a diff tab used to select the tab
 * and stop there, so the tab went on showing the diff it was opened with
 * however many times the file changed underneath it. Reopening it now replaces
 * what it shows.
 */
class DiffViewRefreshTest {

    private static String textOf(DiffView view) {
        StringBuilder out = new StringBuilder();
        collect(view, out);
        return out.toString();
    }

    private static void collect(java.awt.Container from, StringBuilder into) {
        for (java.awt.Component c : from.getComponents()) {
            if (c instanceof JTextComponent text) {
                into.append(text.getText());
            }
            if (c instanceof java.awt.Container inner) {
                collect(inner, into);
            }
        }
    }

    @Test
    @DisplayName("setting content replaces what was there, rather than adding to it")
    void contentIsReplacedNotAppended() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();

        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            view.setContent("+ the shortdesc still carries status=\"new\"");
            view.setContent("+ the shortdesc is clean now");
            shown.set(textOf(view));
        });

        assertThat(shown.get()).contains("clean now");
        assertThat(shown.get()).doesNotContain("status=");
    }

    @Test
    @DisplayName("an empty diff says so rather than leaving the previous one up")
    void anEmptyDiffClearsTheOldOne() throws Exception {
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();

        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            view.setContent("+ a change that has since been committed");
            view.setContent("");
            shown.set(textOf(view));
        });

        assertThat(shown.get()).doesNotContain("since been committed");
        assertThat(shown.get()).contains("No diff content");
    }

    @Test
    @DisplayName("the tab name is stable, which is how an open diff is found again")
    void theTabNameIdentifiesTheFile() {
        String name = DiffViewManager.createUncommittedDiffTabName(new File("/repo/topics/a.dita"));

        // openDiffInTab looks the tab up by this name to refresh it in place.
        assertThat(name).isEqualTo("Diff: a.dita (uncommitted)");
        assertThat(DiffViewManager.createUncommittedDiffTabName(new File("/other/topics/a.dita")))
                .isEqualTo(name);
    }
}
