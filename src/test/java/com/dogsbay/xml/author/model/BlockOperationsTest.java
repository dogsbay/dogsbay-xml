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

package com.dogsbay.xml.author.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockOperationsTest {

    private AuthorDocument doc;
    private AuthorBlock concept;
    private AuthorBlock conbody;

    @BeforeEach
    void setUp() {
        doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        concept = doc.createBlock("concept");
        doc.setRoot(concept);
        conbody = doc.createBlock("conbody");
        doc.attach(concept, 0, conbody);
    }

    private AuthorBlock paragraph(String text) {
        Transaction tx = doc.begin("setup");
        AuthorBlock p = tx.insertBlock("p", conbody, conbody.getChildren().size());
        tx.setText(p, List.of(InlineRun.of(text)));
        tx.commit();
        return p;
    }

    @Test
    void splitKeepsBeforeAndCreatesSiblingWithAfter() {
        AuthorBlock p = paragraph("hello world");
        String before = concept.toStructureString();

        var result = BlockOperations.split(doc, p,
                List.of(InlineRun.of("hello ")), List.of(InlineRun.of("world")));

        assertThat(conbody.getChildren()).hasSize(2);
        assertThat(conbody.getChildren().get(0).getPlainText()).isEqualTo("hello ");
        assertThat(conbody.getChildren().get(1).getPlainText()).isEqualTo("world");
        assertThat(result.focus()).isSameAs(conbody.getChildren().get(1));

        result.edit().undo();
        assertThat(concept.toStructureString()).isEqualTo(before);
    }

    @Test
    void deleteBlockFocusesPreviousTextBlock() {
        AuthorBlock p1 = paragraph("one");
        AuthorBlock p2 = paragraph("");

        var result = BlockOperations.deleteBlock(doc, p2);
        assertThat(conbody.getChildren()).containsExactly(p1);
        assertThat(result.focus()).isSameAs(p1);
    }

    @Test
    void deleteBlockRefusesNonDeletableAndRoot() {
        Transaction tx = doc.begin("table");
        AuthorBlock table = tx.insertBlock("simpletable", conbody, 0);
        AuthorBlock row = tx.insertBlock("strow", table, 0);
        AuthorBlock cell = tx.insertBlock("stentry", row, 0);
        tx.commit();

        assertThat(BlockOperations.deleteBlock(doc, cell)).isNull();
        assertThat(BlockOperations.deleteBlock(doc, concept)).isNull();
    }

    @Test
    void moveAmongSiblingsAndEdges() {
        AuthorBlock a = paragraph("a");
        AuthorBlock b = paragraph("b");

        assertThat(BlockOperations.move(doc, a, -1)).isNull(); // already first
        var result = BlockOperations.move(doc, a, 1);
        assertThat(conbody.getChildren()).containsExactly(b, a);
        assertThat(BlockOperations.move(doc, a, 1)).isNull(); // now last

        result.edit().undo();
        assertThat(conbody.getChildren()).containsExactly(a, b);
    }

    @Test
    void indentCreatesNestedListAndOutdentRestores() {
        Transaction tx = doc.begin("list");
        AuthorBlock ul = tx.insertBlock("ul", conbody, 0);
        AuthorBlock li1 = tx.insertBlock("li", ul, 0);
        AuthorBlock li2 = tx.insertBlock("li", ul, 1);
        tx.commit();

        assertThat(BlockOperations.indentListItem(doc, li1)).isNull(); // first item

        var indent = BlockOperations.indentListItem(doc, li2);
        assertThat(indent).isNotNull();
        assertThat(ul.getChildren()).containsExactly(li1);
        AuthorBlock nested = li1.getChildren().get(0);
        assertThat(nested.getType().getName()).isEqualTo("ul");
        assertThat(nested.getChildren()).containsExactly(li2);

        var outdent = BlockOperations.outdentListItem(doc, li2);
        assertThat(outdent).isNotNull();
        assertThat(ul.getChildren()).containsExactly(li1, li2);
        // the now-empty nested list is removed
        assertThat(li1.getChildren()).isEmpty();
    }

    @Test
    void insertBlockCreatesRequiredDescendantsInOneUndoableEdit() {
        Transaction tx = doc.begin("task setup");
        AuthorBlock task = doc.createBlock("task");
        // build a task document instead
        doc.setRoot(task);
        AuthorBlock taskbody = doc.createBlock("taskbody");
        doc.attach(task, 0, taskbody);

        var result = BlockOperations.insertBlock(doc, "steps", taskbody, 0);
        AuthorBlock steps = taskbody.getChildren().get(0);
        AuthorBlock step = steps.getChildren().get(0);
        AuthorBlock cmd = step.getChildren().get(0);
        assertThat(steps.getType().getName()).isEqualTo("steps");
        assertThat(step.getType().getName()).isEqualTo("step");
        assertThat(cmd.getType().getName()).isEqualTo("cmd");
        assertThat(result.focus()).isSameAs(cmd);

        result.edit().undo();
        assertThat(taskbody.getChildren()).isEmpty();
    }

    @Test
    void tableRowAndColumnOperations() {
        Transaction tx = doc.begin("table");
        AuthorBlock table = tx.insertBlock("simpletable", conbody, 0);
        AuthorBlock head = tx.insertBlock("sthead", table, 0);
        tx.insertBlock("stentry", head, 0);
        tx.insertBlock("stentry", head, 1);
        AuthorBlock row1 = tx.insertBlock("strow", table, 1);
        tx.insertBlock("stentry", row1, 0);
        tx.insertBlock("stentry", row1, 1);
        tx.commit();

        // add a row: same width as the reference row
        var addRow = BlockOperations.addRowAfter(doc, row1);
        assertThat(table.getChildren()).hasSize(3);
        AuthorBlock row2 = table.getChildren().get(2);
        assertThat(row2.getChildren()).hasSize(2);

        // add a column after column 0: every row grows
        var addCol = BlockOperations.addColumnAfter(doc, row1.getChildren().get(0));
        assertThat(head.getChildren()).hasSize(3);
        assertThat(row1.getChildren()).hasSize(3);
        assertThat(row2.getChildren()).hasSize(3);

        // delete column 1 everywhere
        BlockOperations.deleteColumn(doc, row1.getChildren().get(1));
        assertThat(head.getChildren()).hasSize(2);
        assertThat(row1.getChildren()).hasSize(2);
        assertThat(row2.getChildren()).hasSize(2);

        // delete a row, but never the last one
        BlockOperations.deleteRow(doc, row2);
        assertThat(table.getChildren()).hasSize(2);
        assertThat(BlockOperations.deleteRow(doc, row1)).isNull();

        // header rows cannot be deleted via deleteRow
        assertThat(BlockOperations.deleteRow(doc, head)).isNull();
        assertThat(addRow).isNotNull();
        assertThat(addCol).isNotNull();
    }

    @Test
    void nextAndPreviousTextBlockWalkDocumentOrder() {
        AuthorBlock p1 = paragraph("one");
        AuthorBlock p2 = paragraph("two");

        assertThat(BlockOperations.nextTextBlock(doc, p1)).isSameAs(p2);
        assertThat(BlockOperations.previousTextBlock(doc, p2)).isSameAs(p1);
        assertThat(BlockOperations.nextTextBlock(doc, p2)).isNull();
        assertThat(BlockOperations.previousTextBlock(doc, p1)).isNull();
    }
}
