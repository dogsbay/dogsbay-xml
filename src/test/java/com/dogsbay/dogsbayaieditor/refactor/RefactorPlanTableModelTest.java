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

package com.dogsbay.dogsbayaieditor.refactor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult.AttributeEditInfo;

class RefactorPlanTableModelTest {

    private final File root = new File("/projects/docs");

    @Test
    void exposesEditsAsRows() {
        List<AttributeEditInfo> edits = List.of(
            new AttributeEditInfo("/projects/docs/map.ditamap", 12, "href",
                "topics/intro.dita", "topics/overview.dita"),
            new AttributeEditInfo("/projects/docs/topics/task.dita", 30, "conref",
                "intro.dita#intro/note", "overview.dita#intro/note"));
        RefactorPlanTableModel model = new RefactorPlanTableModel(edits, root);

        assertEquals(2, model.getRowCount());
        assertEquals(5, model.getColumnCount());
        assertEquals("In File", model.getColumnName(0));
        assertEquals("map.ditamap", model.getValueAt(0, 0));
        assertEquals("/projects/docs/map.ditamap", model.absolutePath(0));
        assertEquals("12", model.getValueAt(0, 1));
        assertEquals("href", model.getValueAt(0, 2));
        assertEquals("topics/intro.dita", model.getValueAt(0, 3));
        assertEquals("topics/overview.dita", model.getValueAt(0, 4));
        assertEquals("topics/task.dita", model.getValueAt(1, 0));
    }

    @Test
    void showsPathsOutsideRootAsAbsolute() {
        RefactorPlanTableModel model = new RefactorPlanTableModel(List.of(), root);
        assertEquals("/elsewhere/other.dita", model.displayPath("/elsewhere/other.dita"));
    }

    @Test
    void handlesNullRoot() {
        RefactorPlanTableModel model = new RefactorPlanTableModel(List.of(), null);
        assertEquals("/projects/docs/map.ditamap", model.displayPath("/projects/docs/map.ditamap"));
    }

    @Test
    void blankLineForUnknownLineNumber() {
        RefactorPlanTableModel model = new RefactorPlanTableModel(List.of(
            new AttributeEditInfo("/projects/docs/a.dita", -1, "keyref", "old", "new")), root);
        assertEquals("", model.getValueAt(0, 1));
    }
}
