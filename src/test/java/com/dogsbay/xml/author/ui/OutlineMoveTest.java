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

package com.dogsbay.xml.author.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;

/** The outline drag-and-drop contract: validated, undoable block moves. */
class OutlineMoveTest {

    @Test
    void moveBlockToReordersAndReparentsWithValidation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(AdapterTestSupport.parse("""
                    <concept id="c"><title>T</title><conbody>
                    <p>one</p>
                    <p>two</p>
                    <section><title>S</title></section>
                    </conbody></concept>"""));
            AuthorDocument doc = panel.getAuthorDocument();
            AuthorBlock conbody = doc.getRoot().getChildren().get(1);
            AuthorBlock p1 = conbody.getChildren().get(0);
            AuthorBlock p2 = conbody.getChildren().get(1);
            AuthorBlock section = conbody.getChildren().get(2);

            List<javax.swing.undo.UndoableEdit> edits = new java.util.ArrayList<>();
            panel.addUndoableEditListener(e -> edits.add(e.getEdit()));

            // reorder within conbody
            assertThat(panel.moveBlockTo(p1, conbody, 2)).isTrue();
            assertThat(conbody.getChildren().get(0).getId()).isEqualTo(p2.getId());

            // reparent into the section (p is valid there)
            assertThat(panel.moveBlockTo(p2, section, 1)).isTrue();
            assertThat(section.getChildren()).extracting(b -> b.getType().getName())
                    .containsExactly("title", "p");

            // invalid drops are rejected: p into ul-less place is fine, but
            // section into itself / title position violations are refused
            assertThat(panel.moveBlockTo(section, section, 0)).isFalse();
            assertThat(panel.moveBlockTo(doc.getRoot(), section, 0)).isFalse();
            AuthorBlock title = doc.getRoot().getChildren().get(0);
            assertThat(panel.moveBlockTo(title, section.getChildren().get(1), 0)).isFalse();

            // both successful moves are undoable
            assertThat(edits).hasSize(2);
            edits.get(1).undo();
            assertThat(section.getChildren()).hasSize(1);
        });
    }
}
