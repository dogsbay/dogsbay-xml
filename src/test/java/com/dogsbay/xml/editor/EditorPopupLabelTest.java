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

package com.dogsbay.xml.editor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the context-menu label helpers that keep the right-click popup from
 * blowing out to the window width when a large/prose selection is right-clicked.
 */
public class EditorPopupLabelTest {

    @Test
    @DisplayName("a single-line reference is openable, including one with spaces")
    public void openableReferences() {
        assertTrue(Editor.isOpenableReference("images/diagram.png"));
        assertTrue(Editor.isOpenableReference("http://example.com/a/b.dita"));
        assertTrue(Editor.isOpenableReference("  ../shared/intro.dita  "));   // trimmed
        assertTrue(Editor.isOpenableReference("my topic.dita"));              // space is fine
    }

    @Test
    @DisplayName("multi-line / markup / oversized selections are NOT openable")
    public void nonOpenableSelections() {
        // the original giant-menu bug: a multi-line paragraph selection
        assertFalse(Editor.isOpenableReference(
                "Digital audio is sound stored as numbers.\nKnowing the basics helps."));
        assertFalse(Editor.isOpenableReference("<p>Digital audio</p>"));      // markup
        assertFalse(Editor.isOpenableReference("line one\nline two"));         // newline
        assertFalse(Editor.isOpenableReference("   "));                        // blank
        assertFalse(Editor.isOpenableReference(null));
        assertFalse(Editor.isOpenableReference("x".repeat(1025)));            // over cap
    }

    @Test
    @DisplayName("labels are capped with an ellipsis when too long")
    public void ellipsizeCaps() {
        String shortStr = "file:/home/me/a.dita";
        assertEquals(shortStr, Editor.ellipsizeLabel(shortStr, 60));           // untouched

        String capped = Editor.ellipsizeLabel("x".repeat(200), 60);
        assertEquals(60, capped.length());
        assertTrue(capped.endsWith("…"));

        assertEquals("", Editor.ellipsizeLabel(null, 60));
    }
}
