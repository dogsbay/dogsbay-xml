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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for when a validation result row shows the file it came from.
 *
 * <p>The Errors pane lists one document, so repeating its name on every row is
 * noise and is suppressed. Project Validation lists many, and inheriting that
 * suppression made rows appear to lose their filename as you clicked through
 * them — the row you clicked opened its file, and every row from that file then
 * went unlabelled.
 */
class ErrorPaneFileNameTest {

    private static final String SYSTEM_ID =
            "file:/home/u/audacity-demo/topics/exporting-audio.dita";

    // ── The single-document pane's suppression rule ─────────────────────

    @Test
    void hidesTheNameOfTheActiveDocument() {
        assertFalse(ErrorPane.showsFileName(SYSTEM_ID, "exporting-audio.dita"),
                "every row belongs to the active document, so the name is noise");
    }

    @Test
    void showsTheNameOfAnyOtherFile() {
        assertTrue(ErrorPane.showsFileName(SYSTEM_ID, "burning-an-audio-cd.dita"));
    }

    @Test
    void showsTheNameWhenNoDocumentIsOpen() {
        assertTrue(ErrorPane.showsFileName(SYSTEM_ID, null),
                "nothing to be redundant with");
    }

    /**
     * Documents a known limitation of the basename comparison rather than
     * endorsing it: a same-named file in another directory is also suppressed.
     * Harmless for a pane listing one document; it is exactly why the
     * project-wide pane does not inherit this rule.
     */
    @Test
    void basenameComparisonAlsoHidesSameNamedFilesElsewhere() {
        assertFalse(ErrorPane.showsFileName(
                        "file:/home/u/archive/exporting-audio.dita", "exporting-audio.dita"),
                "known limitation of comparing by basename");
    }

    // ── The project-wide pane opts out ──────────────────────────────────

    /**
     * The real assertion: the project pane's answer must not depend on which file
     * is open. A reflective "does it declare an override" check passed even when
     * the body delegated straight back to super, which fully reinstates the bug.
     */
    @Test
    void projectPaneAlwaysShowsTheFileName() {
        // ErrorPane's constructor never dereferences the editor (updatePreferences
        // is a no-op), so a null host is enough to exercise the policy headlessly.
        ProjectErrorPane pane = new ProjectErrorPane(null);

        assertTrue(pane.showsFileName(SYSTEM_ID),
                "project results span many files — the name is the only way to tell "
                        + "which row belongs to which");
    }

    @Test
    void projectPaneDivergesFromTheSingleDocumentRule() {
        ProjectErrorPane pane = new ProjectErrorPane(null);

        // The same input the base rule suppresses must still be shown here.
        assertFalse(ErrorPane.showsFileName(SYSTEM_ID, "exporting-audio.dita"),
                "base rule hides it");
        assertTrue(pane.showsFileName(SYSTEM_ID),
                "project pane must not inherit that suppression");
    }

    // ── Copy To Clipboard mirrors the row ───────────────────────────────

    @Test
    void clipboardLineCarriesTheFileAndDropsTheColumnSentinel() {
        com.dogsbay.xml.XMLError schematron = new com.dogsbay.xml.XMLError(
                SYSTEM_ID, 4, -1, com.dogsbay.xml.XMLError.ERROR, "Every topic needs a shortdesc.");
        assertEquals("Ln 4 [" + SYSTEM_ID + "] - Every topic needs a shortdesc.",
                ErrorPane.clipboardLine(schematron, true));
        assertEquals("Ln 4 - Every topic needs a shortdesc.",
                ErrorPane.clipboardLine(schematron, false), "active document: name suppressed like the row");

        com.dogsbay.xml.XMLError parse = new com.dogsbay.xml.XMLError(
                SYSTEM_ID, 12, 7, com.dogsbay.xml.XMLError.ERROR, "Element type \"p\" must be terminated.");
        assertEquals("Ln 12 Col 7 [" + SYSTEM_ID + "] - Element type \"p\" must be terminated.",
                ErrorPane.clipboardLine(parse, true));
    }
}
