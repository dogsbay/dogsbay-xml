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
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.adapter.DitaBlockAdapter;

/** CALS tables get the row and column operations simple tables had; lists can be left. */
class TableAndListOperationsTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    private AuthorDocument calsTable() {
        return adapter.importDocument(AdapterTestSupport.parse("""
            <reference id="r"><title>T</title><refbody>
            <table><tgroup cols="2"><colspec colname="c1"/><colspec colname="c2"/>
              <thead><row><entry>H1</entry><entry>H2</entry></row></thead>
              <tbody><row><entry>a1</entry><entry>a2</entry></row><row><entry>b1</entry><entry>b2</entry></row></tbody>
            </tgroup></table></refbody></reference>"""));
    }

    private static AuthorBlock find(AuthorBlock b, String type, int nth) {
        int[] seen = {0};
        return findRec(b, type, nth, seen);
    }

    private static AuthorBlock findRec(AuthorBlock b, String type, int nth, int[] seen) {
        if (b.getType().getName().equals(type) && seen[0]++ == nth) {
            return b;
        }
        for (AuthorBlock c : b.getChildren()) {
            AuthorBlock f = findRec(c, type, nth, seen);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Test
    void calsRowsAndColumnsCanBeAddedAndRemovedWithColsKeptInStep() {
        AuthorDocument doc = calsTable();
        AuthorBlock tgroup = find(doc.getRoot(), "tgroup", 0);
        AuthorBlock firstBodyRow = find(doc.getRoot(), "tbody", 0).getChildren().get(0);

        BlockOperations.addRowAfter(doc, firstBodyRow);
        assertThat(find(doc.getRoot(), "tbody", 0).getChildren()).hasSize(3);
        assertThat(find(doc.getRoot(), "tbody", 0).getChildren().get(1).getChildren()).hasSize(2);

        AuthorBlock cell = firstBodyRow.getChildren().get(0);
        BlockOperations.addColumnAfter(doc, cell);
        assertThat(tgroup.getAttributes()).containsEntry("cols", "3");
        assertThat(tgroup.getChildren().stream().filter(c -> "colspec".equals(c.getType().getName()))).hasSize(3);
        assertThat(find(doc.getRoot(), "thead", 0).getChildren().get(0).getChildren()).as("header grows too").hasSize(3);

        BlockOperations.deleteColumn(doc, firstBodyRow.getChildren().get(1));
        assertThat(tgroup.getAttributes()).containsEntry("cols", "2");
        assertThat(find(doc.getRoot(), "thead", 0).getChildren().get(0).getChildren()).hasSize(2);

        assertThat(BlockOperations.deleteRow(doc, find(doc.getRoot(), "thead", 0).getChildren().get(0)))
                .as("header rows are not deleted").isNull();
        BlockOperations.deleteRow(doc, find(doc.getRoot(), "tbody", 0).getChildren().get(2));
        BlockOperations.deleteRow(doc, find(doc.getRoot(), "tbody", 0).getChildren().get(1));
        assertThat(BlockOperations.deleteRow(doc, find(doc.getRoot(), "tbody", 0).getChildren().get(0)))
                .as("the last body row stays").isNull();
    }

    @Test
    void insertingBelowAHeaderRowGoesIntoTheBody() {
        AuthorDocument doc = calsTable();
        AuthorBlock headerRow = find(doc.getRoot(), "thead", 0).getChildren().get(0);
        var result = BlockOperations.addRowAfter(doc, headerRow);
        assertThat(find(doc.getRoot(), "thead", 0).getChildren()).hasSize(1);
        assertThat(find(doc.getRoot(), "tbody", 0).getChildren()).hasSize(3);
        assertThat(result.focus().getParent()).isSameAs(find(doc.getRoot(), "tbody", 0).getChildren().get(0));
    }

    @Test
    void aNewColspecNeverReusesAnExistingName() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
            <reference id="r"><title>T</title><refbody>
            <table><tgroup cols="2"><colspec colname="col1"/><colspec colname="col3"/>
              <tbody><row><entry>a1</entry><entry>a2</entry></row></tbody>
            </tgroup></table></refbody></reference>"""));
        AuthorBlock cell = find(doc.getRoot(), "row", 0).getChildren().get(1);
        BlockOperations.addColumnAfter(doc, cell);
        assertThat(find(doc.getRoot(), "tgroup", 0).getChildren().stream()
                .filter(c -> "colspec".equals(c.getType().getName())).map(c -> c.getAttribute("colname")))
                .containsExactly("col1", "col3", "col4");
    }

    @Test
    void columnOperationsRefuseATableWithSpans() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
            <reference id="r"><title>T</title><refbody>
            <table><tgroup cols="2"><colspec colname="c1"/><colspec colname="c2"/>
              <tbody><row><entry namest="c1" nameend="c2">wide</entry></row>
              <row><entry>a</entry><entry>b</entry></row></tbody>
            </tgroup></table></refbody></reference>"""));
        AuthorBlock cell = find(doc.getRoot(), "row", 1).getChildren().get(0);
        assertThat(BlockOperations.addColumnAfter(doc, cell)).isNull();
        assertThat(BlockOperations.deleteColumn(doc, cell)).isNull();
        assertThat(BlockOperations.addRowAfter(doc, cell.getParent())).as("rows are still fine").isNotNull();
    }

    @Test
    void tableAndListOperationsStayOutOfGenericXml() {
        var generic = new com.dogsbay.xml.author.adapter.GenericXmlAdapter();
        AuthorDocument doc = generic.importDocument(AdapterTestSupport.parse(
                "<doc><ul><li>one</li><li></li></ul><row><entry>a</entry></row></doc>"));
        AuthorBlock row = find(doc.getRoot(), "row", 0);
        AuthorBlock li = find(doc.getRoot(), "li", 1);
        assertThat(BlockOperations.addRowAfter(doc, row)).isNull();
        assertThat(BlockOperations.addColumnAfter(doc, row.getChildren().get(0))).isNull();
        assertThat(BlockOperations.exitList(doc, li)).isNull();
    }

    @Test
    void deletingTheOnlyEmptyListItemRemovesTheList() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p>before</p><ul><li></li></ul></conbody></concept>"));
        AuthorBlock li = find(doc.getRoot(), "li", 0);
        var result = BlockOperations.deleteEmptyBlock(doc, li);
        assertThat(find(doc.getRoot(), "ul", 0)).isNull();
        assertThat(result.focus().getType().getName()).isEqualTo("p");
    }

    @Test
    void aPastedCopyDoesNotClaimAnIdTheDocumentAlreadyUses() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><section id='setup'><p id='first'>one</p></section>"
                + "</conbody></concept>"));
        AuthorBlock conbody = doc.getRoot().getChildren().get(1);
        AuthorBlock section = conbody.getChildren().get(0);
        AuthorBlock copy = adapter.importFragment(doc, adapter.exportFragment(section), conbody);
        BlockOperations.pasteBlock(doc, section, copy);

        assertThat(conbody.getChildren()).hasSize(2);
        assertThat(copy.getAttribute("id")).as("the duplicate must not claim the anchor").isNull();
        assertThat(copy.getChildren().get(0).getAttribute("id")).isNull();
        assertThat(section.getAttribute("id")).as("the original keeps its id").isEqualTo("setup");

        // an id that is free here (a paste from another topic) is kept
        AuthorBlock fresh = adapter.importFragment(doc,
                AdapterTestSupport.parse("<p id='elsewhere'>from another topic</p>"), conbody);
        BlockOperations.pasteBlock(doc, section, fresh);
        assertThat(fresh.getAttribute("id")).isEqualTo("elsewhere");
    }

    @Test
    void aBulletedListBecomesNumberedAndBack() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><ul id='l' outputclass='x'><li>one</li><li>two</li></ul>"
                + "<p>after</p></conbody></concept>"));
        AuthorBlock conbody = doc.getRoot().getChildren().get(1);
        AuthorBlock item = find(doc.getRoot(), "li", 1);
        var result = BlockOperations.convertList(doc, item);
        AuthorBlock ol = conbody.getChildren().get(0);
        assertThat(ol.getType().getName()).isEqualTo("ol");
        assertThat(ol.getAttributes()).containsEntry("id", "l").containsEntry("outputclass", "x");
        assertThat(ol.getChildren()).extracting(AuthorBlock::getPlainText).containsExactly("one", "two");
        assertThat(ol.getChildren().get(1)).as("items move, they are not copied").isSameAs(item);
        assertThat(result.focus().getPlainText()).isEqualTo("one");
        result.edit().undo();
        assertThat(conbody.getChildren().get(0).getType().getName()).isEqualTo("ul");
        assertThat(find(doc.getRoot(), "ul", 0).getChildren()).hasSize(2);
        BlockOperations.convertList(doc, find(doc.getRoot(), "ul", 0));
        assertThat(conbody.getChildren().get(0).getType().getName()).isEqualTo("ol");
        assertThat(BlockOperations.convertList(doc, conbody.getChildren().get(1))).as("not in a list").isNull();
    }

    @Test
    void aPastedBlockGoesAfterTheAnchorOrInsideIt() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p>one</p><ul><li>item</li></ul></conbody></concept>"));
        AuthorBlock conbody = doc.getRoot().getChildren().get(1);
        AuthorBlock p = conbody.getChildren().get(0);
        AuthorBlock note = adapter.importFragment(doc, AdapterTestSupport.parse("<note><p>careful</p></note>"), conbody);
        var result = BlockOperations.pasteBlock(doc, p, note);
        assertThat(conbody.getChildren()).extracting(b -> b.getType().getName()).containsExactly("p", "note", "ul");
        assertThat(result.focus()).as("the note's own text slot takes the caret").isSameAs(note);

        AuthorBlock ul = conbody.getChildren().get(2);
        AuthorBlock li = adapter.importFragment(doc, AdapterTestSupport.parse("<li>more</li>"), ul);
        BlockOperations.pasteBlock(doc, ul, li);   // an li cannot follow the list: it goes inside
        assertThat(ul.getChildren()).extracting(AuthorBlock::getPlainText).containsExactly("item", "more");

        AuthorBlock stray = adapter.importFragment(doc, AdapterTestSupport.parse("<title>no</title>"), conbody);
        assertThat(BlockOperations.pasteBlock(doc, p, stray)).as("a title fits nowhere near a paragraph").isNull();
        AuthorBlock raw = adapter.importFragment(doc, AdapterTestSupport.parse("<foreign>?</foreign>"), conbody);
        assertThat(BlockOperations.pasteBlock(doc, p, raw)).as("unknown markup pastes as a raw chip").isNotNull();
        assertThat(conbody.getChildren().get(1).getType().getName()).isEqualTo(BlockType.RAW_NAME);
    }

    @Test
    void leavingAListPutsAParagraphAfterIt() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><ul><li>one</li><li></li></ul><p>after</p></conbody></concept>"));
        AuthorBlock ul = find(doc.getRoot(), "ul", 0);
        AuthorBlock empty = ul.getChildren().get(1);
        var result = BlockOperations.exitList(doc, empty);
        AuthorBlock conbody = ul.getParent();
        assertThat(conbody.getChildren()).extracting(b -> b.getType().getName()).containsExactly("ul", "p", "p");
        assertThat(ul.getChildren()).hasSize(1);
        assertThat(result.focus()).isSameAs(conbody.getChildren().get(1));
        assertThat(result.focus().getText()).isEqualTo(List.of());
    }
}
