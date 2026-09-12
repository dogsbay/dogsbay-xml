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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.undo.BlockEdit;

class TransactionTest {

    private AuthorDocument doc;
    private AuthorBlock concept;
    private AuthorBlock conbody;
    private List<AuthorDocumentEvent> events;

    @BeforeEach
    void setUp() {
        doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        concept = doc.createBlock("concept");
        doc.setRoot(concept);
        conbody = doc.createBlock("conbody");
        doc.attach(concept, 0, conbody);
        events = new ArrayList<>();
        doc.addListener(events::add);
    }

    @Test
    void insertValidChildAppliesImmediatelyAndFiresEvent() {
        Transaction tx = doc.begin("Insert paragraph");
        AuthorBlock p = tx.insertBlock("p", conbody, 0);

        assertThat(conbody.getChildren()).containsExactly(p);
        assertThat(p.getParent()).isSameAs(conbody);
        assertThat(doc.getBlock(p.getId())).isSameAs(p);
        assertThat(events).anyMatch(e ->
                e.kind() == AuthorDocumentEvent.Kind.STRUCTURE && e.block() == conbody);

        BlockEdit edit = tx.commit();
        assertThat(edit.isSignificant()).isTrue();
        assertThat(edit.getPresentationName()).isEqualTo("Insert paragraph");
    }

    @Test
    void insertInvalidChildIsRejectedAndDocumentUnchanged() {
        String before = concept.toStructureString();
        Transaction tx = doc.begin("bad insert");

        assertThatThrownBy(() -> tx.insertBlock("step", conbody, 0))
                .isInstanceOf(AuthorStructureException.class)
                .hasMessageContaining("step")
                .hasMessageContaining("conbody");

        assertThat(concept.toStructureString()).isEqualTo(before);
        assertThat(conbody.getChildren()).isEmpty();
    }

    @Test
    void movingToAnIndexOutOfRangeIsRejectedBeforeTheDetach() {
        Transaction setup = doc.begin("setup");
        AuthorBlock p1 = setup.insertBlock("p", conbody, 0);
        setup.insertBlock("p", conbody, 1);
        setup.commit();
        int children = conbody.getChildren().size();

        Transaction tx = doc.begin("move");
        assertThatThrownBy(() -> tx.moveBlock(p1, conbody, children + 5)).isInstanceOf(RuntimeException.class);
        assertThat(conbody.getChildren()).hasSize(children).contains(p1);
        assertThat(p1.getParent()).isSameAs(conbody);
        assertThat(doc.getBlock(p1.getId())).isSameAs(p1);
    }

    @Test
    void failingOperationRollsBackEarlierOperationsInSameTransaction() {
        String before = concept.toStructureString();
        Transaction tx = doc.begin("multi");
        AuthorBlock p1 = tx.insertBlock("p", conbody, 0);
        tx.setText(p1, List.of(InlineRun.of("hello")));
        tx.insertBlock("ul", conbody, 1);

        // now an invalid op: li directly under conbody
        assertThatThrownBy(() -> tx.insertBlock("li", conbody, 2))
                .isInstanceOf(AuthorStructureException.class);

        // everything from this transaction is gone
        assertThat(concept.toStructureString()).isEqualTo(before);
        assertThat(doc.getBlock(p1.getId())).isNull();
        // transaction is dead
        assertThatThrownBy(() -> tx.insertBlock("p", conbody, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rollbackRevertsEverything() {
        String before = concept.toStructureString();
        Transaction tx = doc.begin("rolled back");
        AuthorBlock p = tx.insertBlock("p", conbody, 0);
        tx.setText(p, List.of(InlineRun.of("temp")));
        tx.rollback();

        assertThat(concept.toStructureString()).isEqualTo(before);
        assertThat(conbody.getChildren()).isEmpty();
    }

    @Test
    void removeAndUndoRestoresPositionAndIdIndex() {
        Transaction setup = doc.begin("setup");
        AuthorBlock p1 = setup.insertBlock("p", conbody, 0);
        AuthorBlock p2 = setup.insertBlock("p", conbody, 1);
        AuthorBlock p3 = setup.insertBlock("p", conbody, 2);
        setup.commit();

        Transaction tx = doc.begin("remove middle");
        tx.removeBlock(p2);
        BlockEdit edit = tx.commit();
        assertThat(conbody.getChildren()).containsExactly(p1, p3);
        assertThat(doc.getBlock(p2.getId())).isNull();

        edit.undo();
        assertThat(conbody.getChildren()).containsExactly(p1, p2, p3);
        assertThat(doc.getBlock(p2.getId())).isSameAs(p2);

        edit.redo();
        assertThat(conbody.getChildren()).containsExactly(p1, p3);
    }

    @Test
    void rootCannotBeRemovedOrMoved() {
        assertThatThrownBy(() -> doc.begin("x").removeBlock(concept))
                .isInstanceOf(AuthorStructureException.class);
        assertThatThrownBy(() -> doc.begin("x").moveBlock(concept, conbody, 0))
                .isInstanceOf(AuthorStructureException.class);
    }

    @Test
    void moveWithinSameParentForwardAndBack() {
        Transaction setup = doc.begin("setup");
        AuthorBlock a = setup.insertBlock("p", conbody, 0);
        AuthorBlock b = setup.insertBlock("p", conbody, 1);
        AuthorBlock c = setup.insertBlock("p", conbody, 2);
        setup.commit();

        Transaction tx = doc.begin("move a after c");
        tx.moveBlock(a, conbody, 3);
        BlockEdit edit = tx.commit();
        assertThat(conbody.getChildren()).containsExactly(b, c, a);

        edit.undo();
        assertThat(conbody.getChildren()).containsExactly(a, b, c);
        edit.redo();
        assertThat(conbody.getChildren()).containsExactly(b, c, a);
    }

    @Test
    void moveToInvalidParentIsRejected() {
        Transaction setup = doc.begin("setup");
        AuthorBlock p = setup.insertBlock("p", conbody, 0);
        AuthorBlock ul = setup.insertBlock("ul", conbody, 1);
        setup.commit();

        Transaction tx = doc.begin("bad move");
        assertThatThrownBy(() -> tx.moveBlock(p, ul, 0))
                .isInstanceOf(AuthorStructureException.class);
        assertThat(conbody.getChildren()).containsExactly(p, ul);
    }

    @Test
    void blockCannotBeMovedIntoItsOwnSubtree() {
        Transaction setup = doc.begin("setup");
        AuthorBlock ul = setup.insertBlock("ul", conbody, 0);
        AuthorBlock li = setup.insertBlock("li", ul, 0);
        AuthorBlock nested = setup.insertBlock("ul", li, 0);
        setup.commit();

        Transaction tx = doc.begin("cycle");
        assertThatThrownBy(() -> tx.moveBlock(ul, nested.getParent(), 0))
                .isInstanceOf(AuthorStructureException.class);
    }

    @Test
    void setAttributeSetAndRemoveUndoRedo() {
        Transaction setup = doc.begin("setup");
        AuthorBlock p = setup.insertBlock("p", conbody, 0);
        setup.commit();

        Transaction tx = doc.begin("set audience");
        tx.setAttribute(p, "audience", "admin");
        BlockEdit edit = tx.commit();
        assertThat(p.getAttribute("audience")).isEqualTo("admin");

        edit.undo();
        assertThat(p.getAttribute("audience")).isNull();
        edit.redo();
        assertThat(p.getAttribute("audience")).isEqualTo("admin");

        Transaction tx2 = doc.begin("clear audience");
        tx2.setAttribute(p, "audience", null);
        BlockEdit edit2 = tx2.commit();
        assertThat(p.getAttributes()).isEmpty();
        edit2.undo();
        assertThat(p.getAttribute("audience")).isEqualTo("admin");
    }

    @Test
    void setTextOnContainerIsRejected() {
        Transaction tx = doc.begin("bad text");
        assertThatThrownBy(() -> tx.setText(conbody, List.of(InlineRun.of("nope"))))
                .isInstanceOf(AuthorStructureException.class);
    }

    @Test
    void setTextUndoRestoresOldRuns() {
        Transaction setup = doc.begin("setup");
        AuthorBlock p = setup.insertBlock("p", conbody, 0);
        setup.setText(p, List.of(InlineRun.of("one")));
        setup.commit();

        Transaction tx = doc.begin("edit text");
        tx.setText(p, List.of(InlineRun.of("two "),
                InlineRun.styled("bold", InlineStyle.BOLD, InlineStyle.TRUE)));
        BlockEdit edit = tx.commit();
        assertThat(p.getPlainText()).isEqualTo("two bold");

        edit.undo();
        assertThat(p.getPlainText()).isEqualTo("one");
        edit.redo();
        assertThat(p.getPlainText()).isEqualTo("two bold");
        assertThat(p.getText().get(1).attrs()).containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);
    }

    @Test
    void rawBlockCanBeInsertedAnywhereForLosslessFallback() {
        Transaction tx = doc.begin("raw");
        AuthorBlock raw = doc.createRawBlock("<unknown-element a=\"1\"/>");
        tx.insertBlock(raw, conbody, 0);
        tx.commit();

        assertThat(conbody.getChildren()).containsExactly(raw);
        assertThat(raw.getRawXml()).isEqualTo("<unknown-element a=\"1\"/>");
        assertThat(raw.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
    }
}
