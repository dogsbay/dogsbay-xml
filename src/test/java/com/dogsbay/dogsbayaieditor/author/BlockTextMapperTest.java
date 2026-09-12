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
package com.dogsbay.dogsbayaieditor.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockOperations;

/** Switching views keeps the writer's place: a block and its element name each other. */
class BlockTextMapperTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    private static final String TOPIC = """
            <concept id="c"><title>T</title><conbody>
              <p>first</p>
              <p>second with <b>bold</b> and <xref href="x.dita">a link</xref></p>
              <ul><li>alpha</li><li>beta</li></ul>
              <p>third</p>
            </conbody></concept>""";

    private AuthorBlock blockAt(AuthorDocument doc, String type, int nth) {
        int seen = 0;
        for (AuthorBlock b : BlockOperations.flatten(doc.getRoot())) {
            if (type.equals(b.getType().getName()) && seen++ == nth) {
                return b;
            }
        }
        throw new IllegalArgumentException("no " + type + " #" + nth);
    }

    @Test
    void aBlockAndItsElementFindEachOther() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        Element root = AdapterTestSupport.parse(TOPIC);

        AuthorBlock third = blockAt(doc, "p", 2);
        assertThat(third.getPlainText()).isEqualTo("third");
        List<BlockTextMapper.Step> path = BlockTextMapper.pathOf(third);
        assertThat(path).containsExactly(new BlockTextMapper.Step("conbody", 1),
                new BlockTextMapper.Step("p", 3));

        Element element = BlockTextMapper.resolve(root, path);
        assertThat(element.getName()).isEqualTo("p");
        assertThat(element.getText()).isEqualTo("third");

        // and back again
        assertThat(BlockTextMapper.pathOf(element)).isEqualTo(path);
        assertThat(BlockTextMapper.resolve(doc.getRoot(), BlockTextMapper.pathOf(element))).isSameAs(third);
    }

    @Test
    void inlineElementsDoNotShiftTheCount() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        Element root = AdapterTestSupport.parse(TOPIC);

        // the second paragraph holds <b> and <xref> elements that are runs, not blocks:
        // counting positions would put the list one step out
        AuthorBlock second = blockAt(doc, "li", 1);
        assertThat(second.getPlainText()).isEqualTo("beta");
        Element element = BlockTextMapper.resolve(root, BlockTextMapper.pathOf(second));
        assertThat(element.getName()).isEqualTo("li");
        assertThat(element.getText()).isEqualTo("beta");
    }

    @Test
    void aPathIntoAnInlineElementLandsOnItsBlock() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        Element root = AdapterTestSupport.parse(TOPIC);

        // the caret sits inside <b> in the XML view; the block model has no such block
        Element bold = (Element) root.selectSingleNode("//b");
        AuthorBlock landed = BlockTextMapper.resolve(doc.getRoot(), BlockTextMapper.pathOf(bold));
        assertThat(landed.getType().getName()).isEqualTo("p");
        assertThat(landed.getPlainText()).startsWith("second with");
    }

    @Test
    void theRootAndUnknownPathsAreSafe() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        assertThat(BlockTextMapper.pathOf(doc.getRoot())).isEmpty();
        assertThat(BlockTextMapper.resolve(doc.getRoot(), List.of())).isSameAs(doc.getRoot());
        assertThat(BlockTextMapper.resolve(doc.getRoot(),
                List.of(new BlockTextMapper.Step("nowhere", 1)))).isSameAs(doc.getRoot());
        assertThat(BlockTextMapper.pathOf((AuthorBlock) null)).isNull();
        assertThat(BlockTextMapper.pathOf((Element) null)).isNull();
    }

    @Test
    void aRawChipIsFoundByTheMarkupItHolds() {
        String withChip = "<concept id='c'><title>T</title><conbody><p>one</p>"
                + "<foreign-thing><x/></foreign-thing><p>two</p></conbody></concept>";
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(withChip));
        Element root = AdapterTestSupport.parse(withChip);

        AuthorBlock chip = doc.getRoot().getChildren().get(1).getChildren().get(1);
        assertThat(chip.getType().getCategory())
                .isEqualTo(com.dogsbay.xml.author.model.BlockType.Category.RAW);
        assertThat(BlockTextMapper.nameOf(chip)).isEqualTo("foreign-thing");

        Element element = BlockTextMapper.resolve(root, BlockTextMapper.pathOf(chip));
        assertThat(element.getName()).isEqualTo("foreign-thing");

        // the paragraph after the chip still lines up in both directions
        AuthorBlock after = blockAt(doc, "p", 1);
        assertThat(BlockTextMapper.resolve(root, BlockTextMapper.pathOf(after)).getText()).isEqualTo("two");
    }

    @Test
    void aCaretBetweenBlocksDoesNotDragTheViewToTheTop() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        AuthorBlock root = doc.getRoot();
        AuthorBlock body = root.getChildren().get(1);
        AuthorBlock deep = blockAt(doc, "li", 1);

        // the caret sits in the whitespace between paragraphs: the path names the body
        List<BlockTextMapper.Step> bodyPath = BlockTextMapper.pathOf(body);
        assertThat(BlockTextMapper.targetFor(root, bodyPath, deep))
                .as("the view is already inside the body and keeps its place").isNull();
        assertThat(BlockTextMapper.targetFor(root, bodyPath, null))
                .as("with no selection to keep, the body is still a move").isSameAs(body);

        // a real block is always followed, including from a selection inside it
        AuthorBlock third = blockAt(doc, "p", 2);
        assertThat(BlockTextMapper.targetFor(root, BlockTextMapper.pathOf(third), deep)).isSameAs(third);
        assertThat(BlockTextMapper.targetFor(root, BlockTextMapper.pathOf(third), third)).isSameAs(third);
    }

    @Test
    void theRootAndAnEmptyPathAreNeverATarget() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(TOPIC));
        AuthorBlock root = doc.getRoot();
        assertThat(BlockTextMapper.targetFor(root, List.of(), null)).isNull();
        assertThat(BlockTextMapper.targetFor(root, null, null)).isNull();
        assertThat(BlockTextMapper.targetFor(null, BlockTextMapper.pathOf(blockAt(doc, "p", 0)), null)).isNull();
        // a path that resolves nowhere stops at the root, which is not a place to go
        assertThat(BlockTextMapper.targetFor(root, List.of(new BlockTextMapper.Step("nowhere", 1)), null)).isNull();
    }

    @Test
    void aBlockAmongInlineSiblingsIsCountedByName() {
        // the note holds <b> as a run and <p> as a block: its element children are
        // b, p and its block children are just p, so counting positions would put
        // the paragraph onto the bold text
        String xml = "<concept id='c'><title>T</title><conbody>"
                + "<note>careful <b>very</b> so<p>child</p></note></conbody></concept>";
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(xml));
        Element root = AdapterTestSupport.parse(xml);

        AuthorBlock note = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(note.getType().getName()).isEqualTo("note");
        AuthorBlock child = note.getChildren().get(0);
        assertThat(child.getType().getName()).isEqualTo("p");

        Element element = BlockTextMapper.resolve(root, BlockTextMapper.pathOf(child));
        assertThat(element.getName()).as("not the <b> that precedes it").isEqualTo("p");
        assertThat(element.getText()).isEqualTo("child");
    }

    @Test
    void anElementUsedBothInlineAndAsABlockStopsThePathAboveIt() {
        // draft-comment is legal both inside text and as a block, so the two trees
        // number the occurrences differently: naming one would land on the wrong node
        String xml = "<concept id='c'><title>T</title><conbody>"
                + "<note>careful <draft-comment>inline remark</draft-comment> here"
                + "<p>body</p><draft-comment>block remark</draft-comment></note></conbody></concept>";
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(xml));
        Element root = AdapterTestSupport.parse(xml);

        AuthorBlock note = doc.getRoot().getChildren().get(1).getChildren().get(0);
        AuthorBlock comment = note.getChildren().stream()
                .filter(b -> "draft-comment".equals(b.getType().getName())).findFirst().orElseThrow();
        assertThat(comment.getPlainText()).isEqualTo("block remark");

        List<BlockTextMapper.Step> path = BlockTextMapper.pathOf(comment);
        assertThat(path).extracting(BlockTextMapper.Step::name).containsExactly("conbody", "note");
        assertThat(BlockTextMapper.resolve(root, path).getName())
                .as("the note, not the inline remark at the top of it").isEqualTo("note");

        // the same from the XML side
        Element inlineRemark = (Element) root.selectNodes("//draft-comment").get(0);
        assertThat(BlockTextMapper.resolve(doc.getRoot(), BlockTextMapper.pathOf(inlineRemark)))
                .isSameAs(note);
    }

    @Test
    void aNamespacedChipKeepsItsPrefixOnBothSides() {
        String xml = "<concept id='c'><title>T</title><conbody><p>one</p>"
                + "<svg:svg xmlns:svg='urn:svg'>A</svg:svg><svg>B</svg></conbody></concept>";
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(xml));
        Element root = AdapterTestSupport.parse(xml);
        AuthorBlock body = doc.getRoot().getChildren().get(1);

        AuthorBlock prefixed = body.getChildren().get(1);
        AuthorBlock plain = body.getChildren().get(2);
        assertThat(BlockTextMapper.nameOf(prefixed)).isEqualTo("svg:svg");
        assertThat(BlockTextMapper.nameOf(plain)).isEqualTo("svg");

        assertThat(BlockTextMapper.resolve(root, BlockTextMapper.pathOf(prefixed)).getText()).isEqualTo("A");
        assertThat(BlockTextMapper.resolve(root, BlockTextMapper.pathOf(plain)).getText())
                .as("the unprefixed one must not resolve onto its prefixed sibling").isEqualTo("B");
    }

    @Test
    void aPathThatReachesNothingIsAFailureNotTheRoot() {
        Element root = AdapterTestSupport.parse(TOPIC);
        assertThat(BlockTextMapper.resolveOrNull(root, List.of(new BlockTextMapper.Step("nowhere", 1))))
                .as("the caret must not be sent to the top of the file").isNull();
        assertThat(BlockTextMapper.resolveOrNull(root, List.of())).isNull();
        assertThat(BlockTextMapper.resolveOrNull(root, null)).isNull();
        assertThat(BlockTextMapper.resolveOrNull(null, List.of())).isNull();
        // a path that reaches part of the way is a neighbourhood, and is kept
        assertThat(BlockTextMapper.resolveOrNull(root, List.of(new BlockTextMapper.Step("conbody", 1),
                new BlockTextMapper.Step("nowhere", 1))).getName()).isEqualTo("conbody");
    }
}
