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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;

/** UI-level structural editing: Enter splits, Backspace deletes, focus follows. */
class StructuralEditingUiTest {

    private AuthorEditorPanel panel;
    private AuthorDocument doc;
    private AuthorBlock conbody;

    private void build() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DitaBlockAdapter adapter = new DitaBlockAdapter();
            doc = new AuthorDocument(adapter.getRegistry());
            AuthorBlock concept = doc.createBlock("concept");
            doc.setRoot(concept);
            conbody = doc.createBlock("conbody");
            doc.attach(concept, 0, conbody);
            panel = new AuthorEditorPanel(adapter);
            panel.setAuthorDocument(doc);
        });
    }

    private AuthorBlock addParagraph(String text) throws Exception {
        AuthorBlock[] p = new AuthorBlock[1];
        SwingUtilities.invokeAndWait(() -> {   // the model is edited on the event thread, as the editor does
            var tx = doc.begin("setup");
            p[0] = tx.insertBlock("p", conbody, conbody.getChildren().size());
            tx.setText(p[0], List.of(InlineRun.of(text)));
            tx.commit();
        });
        return p[0];
    }

    private TextBlockComponent textFor(AuthorBlock block) {
        if (!SwingUtilities.isEventDispatchThread()) {
            var found = new java.util.concurrent.atomic.AtomicReference<TextBlockComponent>();
            try {
                SwingUtilities.invokeAndWait(() -> found.set(textFor(block)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return found.get();
        }
        panel.flushPendingRebuilds();   // model edits redraw on the event queue; the view is wanted now
        return findText(panel, block);
    }

    private TextBlockComponent findText(java.awt.Container container, AuthorBlock block) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof TextBlockComponent text && text.getBlock() == block) {
                return text;
            }
            if (child instanceof java.awt.Container c) {
                TextBlockComponent found = findText(c, block);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // The system clipboard needs a display, which a CI runner does not have:
    // tagged so it runs under uiTest rather than failing the headless suite.
    @Tag("ui")
    @Test
    void aBlockCopiesAsXmlAndPastesBackAfterTheAnchor() throws Exception {
        build();
        AuthorBlock first = addParagraph("one");
        addParagraph("two");
        SwingUtilities.invokeAndWait(() -> {
            assertThat(panel.copyBlock(first)).isTrue();
            assertThat(clipboardText()).isEqualTo("<p>one</p>");
            assertThat(panel.pasteBlock(first)).isTrue();
        });
        assertThat(conbody.getChildren()).extracting(AuthorBlock::getPlainText)
                .containsExactly("one", "one", "two");

        SwingUtilities.invokeAndWait(() -> assertThat(panel.duplicateBlock(first)).isTrue());
        assertThat(conbody.getChildren()).extracting(AuthorBlock::getPlainText)
                .containsExactly("one", "one", "one", "two");

        SwingUtilities.invokeAndWait(() -> assertThat(panel.cutBlock(conbody.getChildren().get(0))).isTrue());
        assertThat(conbody.getChildren()).extracting(AuthorBlock::getPlainText)
                .containsExactly("one", "one", "two");
    }

    // The system clipboard needs a display, which a CI runner does not have:
    // tagged so it runs under uiTest rather than failing the headless suite.
    @Tag("ui")
    @Test
    void aRefusedCutLeavesTheClipboardAlone() throws Exception {
        build();
        AuthorBlock p = addParagraph("one");
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection("something the user copied"), null);
            assertThat(panel.cutBlock(doc.getRoot())).as("the topic root cannot be cut").isFalse();
            assertThat(clipboardText()).isEqualTo("something the user copied");
            assertThat(panel.cutBlock(p)).isTrue();
            assertThat(clipboardText()).isEqualTo("<p>one</p>");
        });
    }

    @Test
    void zoomingKeepsTheCaretWhereItWas() throws Exception {
        build();
        AuthorBlock p = addParagraph("one two");
        try {
            SwingUtilities.invokeAndWait(() -> {
                panel.focusBlock(p, 3);
                panel.zoom(+1);
            });
            SwingUtilities.invokeAndWait(() ->
                    assertThat(textFor(p).getCaretPosition()).as("the caret survives the rebuild").isEqualTo(3));
        } finally {
            SwingUtilities.invokeAndWait(() -> panel.zoom(0));   // the scale is shared by every pane
        }
    }

    // The system clipboard needs a display, which a CI runner does not have:
    // tagged so it runs under uiTest rather than failing the headless suite.
    @Tag("ui")
    @Test
    void pastingMarkupThatFitsNowhereIsRefused() throws Exception {
        build();
        AuthorBlock p = addParagraph("one");
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection("<title>no</title>"), null);
            assertThat(panel.pasteBlock(p)).isFalse();
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection("just text"), null);
            assertThat(panel.pasteBlock(p)).isFalse();
        });
        assertThat(conbody.getChildren()).hasSize(1);
    }

    @Test
    void aListConvertsBetweenBulletedAndNumbered() throws Exception {
        build();
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("list");
            AuthorBlock ul = tx.insertBlock("ul", conbody, 0);
            tx.setText(tx.insertBlock("li", ul, 0), List.of(InlineRun.of("item")));
            tx.commit();
        });
        AuthorBlock item = conbody.getChildren().get(0).getChildren().get(0);
        SwingUtilities.invokeAndWait(() -> assertThat(panel.convertList(item)).isTrue());
        assertThat(conbody.getChildren().get(0).getType().getName()).isEqualTo("ol");
        assertThat(textFor(item)).as("the moved item is drawn in the new list").isNotNull();
    }

    @Test
    void zoomingScalesTheBlockFontsAndKeepsTheContent() throws Exception {
        build();
        AuthorBlock p = addParagraph("one");
        float before = textFor(p).getFont().getSize2D();
        try {
            SwingUtilities.invokeAndWait(() -> panel.zoom(+1));
            assertThat(textFor(p).getFont().getSize2D()).isGreaterThan(before);
            assertThat(textFor(p).getText()).isEqualTo("one");
        } finally {
            SwingUtilities.invokeAndWait(() -> panel.zoom(0));
        }
        assertThat(textFor(p).getFont().getSize2D()).isEqualTo(before);
    }

    private static String clipboardText() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aStructuralEditRedrawsOnlyTheTouchedBlock() throws Exception {
        build();
        AuthorBlock first = addParagraph("one");
        AuthorBlock second = addParagraph("two");
        TextBlockComponent untouched = textFor(first);
        assertThat(untouched).isNotNull();
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("insert");
            AuthorBlock note = tx.insertBlock("note", conbody, 2);
            tx.insertBlock("p", note, 0);
            tx.commit();
        });
        assertThat(textFor(first)).as("the untouched paragraph keeps its component").isSameAs(untouched);
        assertThat(textFor(second)).isNotNull();
        assertThat(textFor(conbody.getChildren().get(2).getChildren().get(0))).as("the new block is drawn").isNotNull();
    }

    @Test
    void aRefusedMoveIsReportedInsteadOfThrown() throws Exception {
        build();
        addParagraph("one");
        // the concept's title must stay first: moving it below the body is refused by the model
        AuthorBlock[] title = new AuthorBlock[1];
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("title");
            title[0] = tx.insertBlock("title", doc.getRoot(), 0);
            tx.setText(title[0], List.of(InlineRun.of("T")));
            tx.commit();
        });
        SwingUtilities.invokeAndWait(() -> panel.moveBlock(textFor(title[0]), +1));
        assertThat(doc.getRoot().getChildren().get(0)).as("nothing moved, no exception").isSameAs(title[0]);
    }

    @Test
    void enterSplitsParagraphAtCaretIncludingStyles() throws Exception {
        build();
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("styled");
            AuthorBlock p = tx.insertBlock("p", conbody, 0);
            tx.setText(p, List.of(
                    InlineRun.of("plain "),
                    InlineRun.styled("boldtext", InlineStyle.BOLD, InlineStyle.TRUE)));
            tx.commit();

            TextBlockComponent text = textFor(p);
            text.setCaretPosition(10); // inside "boldtext"
            panel.enterPressed(text);

            assertThat(conbody.getChildren()).hasSize(2);
            AuthorBlock first = conbody.getChildren().get(0);
            AuthorBlock second = conbody.getChildren().get(1);
            assertThat(first.getPlainText()).isEqualTo("plain bold");
            assertThat(second.getPlainText()).isEqualTo("text");
            // the bold formatting survives on both sides of the split
            assertThat(first.getText().get(1).attrs())
                    .containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);
            assertThat(second.getText().get(0).attrs())
                    .containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);
        });
    }

    @Test
    void enterInTitleMovesFocusWithoutSplitting() throws Exception {
        build();
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("title");
            AuthorBlock concept = doc.getRoot();
            AuthorBlock title = tx.insertBlock("title", concept, 0);
            tx.setText(title, List.of(InlineRun.of("My Title")));
            AuthorBlock p = tx.insertBlock("p", conbody, 0);
            tx.setText(p, List.of(InlineRun.of("body")));
            tx.commit();

            TextBlockComponent titleText = textFor(title);
            panel.enterPressed(titleText);

            // no split happened
            assertThat(concept.getChildren().get(0).getType().getName()).isEqualTo("title");
            assertThat(concept.getChildren()).hasSize(2);
            assertThat(panel.getSelectedBlock()).isSameAs(p);
        });
    }

    @Test
    void backspaceOnEmptyParagraphDeletesIt() throws Exception {
        build();
        AuthorBlock keep = addParagraph("keep me");
        AuthorBlock victim = addParagraph("");
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = textFor(victim);
            text.setCaretPosition(0);
            panel.backspaceOnEmptyBlock(text);

            assertThat(conbody.getChildren()).hasSize(1);
            assertThat(conbody.getChildren().get(0).getId()).isEqualTo(keep.getId());
            assertThat(panel.getSelectedBlock().getId()).isEqualTo(keep.getId());
        });
    }

    @Test
    void insertViaPanelCreatesRequiredChildrenAndSelectsThem() throws Exception {
        build();
        SwingUtilities.invokeAndWait(() -> {
            panel.insertBlock("ul", conbody, 0);
            AuthorBlock ul = conbody.getChildren().get(0);
            assertThat(ul.getType().getName()).isEqualTo("ul");
            assertThat(ul.getChildren()).hasSize(1);
            assertThat(ul.getChildren().get(0).getType().getName()).isEqualTo("li");
            assertThat(panel.getSelectedBlock()).isSameAs(ul.getChildren().get(0));
        });
    }

    @Test
    void attributeEditsCommitAsUndoableTransactions() throws Exception {
        build();
        AuthorBlock p = addParagraph("x");
        List<javax.swing.undo.UndoableEdit> edits = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.addUndoableEditListener(e -> edits.add(e.getEdit()));
            panel.setBlockAttribute(p, "audience", "admin");
            assertThat(p.getAttribute("audience")).isEqualTo("admin");
            assertThat(edits).hasSize(1);
            edits.get(0).undo();
            assertThat(p.getAttribute("audience")).isNull();
        });
    }
}
