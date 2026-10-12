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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.undo.UndoableEdit;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;

/**
 * Editing-path tests: view edits flow into the model as undoable
 * transactions, placeholders survive, formatting toggles round-trip, and the
 * exported XML reflects edits.
 */
class TextBlockEditingTest {

    private AuthorEditorPanel panel;
    private AuthorDocument doc;
    private AuthorBlock paragraph;
    private List<UndoableEdit> postedEdits;

    private void buildPanel(List<InlineRun> runs) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DitaBlockAdapter adapter = new DitaBlockAdapter();
            doc = new AuthorDocument(adapter.getRegistry());
            AuthorBlock concept = doc.createBlock("concept");
            doc.setRoot(concept);
            AuthorBlock conbody = doc.createBlock("conbody");
            doc.attach(concept, 0, conbody);
            paragraph = doc.createBlock("p");
            paragraph.setText(runs);
            doc.attach(conbody, 0, paragraph);

            panel = new AuthorEditorPanel(adapter);
            panel.setAuthorDocument(doc);
            postedEdits = new ArrayList<>();
            panel.addUndoableEditListener(e -> postedEdits.add(e.getEdit()));
        });
    }

    private TextBlockComponent findText(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof TextBlockComponent text && text.getBlock() == paragraph) {
                return text;
            }
            if (child instanceof java.awt.Container c) {
                TextBlockComponent found = findText(c);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void refreshThenExtractIsIdentity() throws Exception {
        List<InlineRun> runs = List.of(
                InlineRun.of("plain "),
                InlineRun.styled("bold", InlineStyle.BOLD, InlineStyle.TRUE),
                InlineRun.of(" then "),
                new InlineRun("", Map.of(InlineStyle.KEYREF, "product",
                        InlineStyle.DITA_INLINE, "keyword")),
                InlineRun.of(" end"));
        buildPanel(runs);
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            assertThat(text.extractRuns()).isEqualTo(runs);
            assertThat(text.hasPendingEdits()).isFalse();
        });
    }

    @Test
    void typedTextCommitsAsUndoableTransaction() throws Exception {
        buildPanel(List.of(InlineRun.of("hello world")));
        SwingUtilities.invokeAndWait(() -> {
            try {
                TextBlockComponent text = findText(panel);
                // simulate typing at the end
                text.getStyledDocument().insertString(5, " brave", null);
                assertThat(text.hasPendingEdits()).isTrue();
                text.commitPending();

                assertThat(paragraph.getPlainText()).isEqualTo("hello brave world");
                assertThat(postedEdits).hasSize(1);

                // undo restores the model and the view refreshes from it
                postedEdits.get(0).undo();
                assertThat(paragraph.getPlainText()).isEqualTo("hello world");
                assertThat(text.getText()).isEqualTo("hello world");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void typingInsidePlaceholderDoesNotExtendTheReference() throws Exception {
        buildPanel(List.of(
                InlineRun.of("see "),
                new InlineRun("", Map.of(InlineStyle.HREF, "guide.dita")),
                InlineRun.of(" now")));
        SwingUtilities.invokeAndWait(() -> {
            try {
                TextBlockComponent text = findText(panel);
                String rendered = text.getText();
                assertThat(rendered).isEqualTo("see ⟨guide.dita⟩ now");

                // type in the middle of the placeholder; the inserted text must
                // come out plain, with the reference run still intact
                int mid = rendered.indexOf("guide");
                text.getStyledDocument().insertString(mid, "XX", null);
                text.commitPending();

                List<InlineRun> runs = paragraph.getText();
                assertThat(runs).anyMatch(r ->
                        r.text().isEmpty() && "guide.dita".equals(r.attrs().get(InlineStyle.HREF)));
                assertThat(runs).anyMatch(r -> r.text().contains("XX") && r.attrs().isEmpty());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void toggleStyleAppliesAndRemovesFormatting() throws Exception {
        buildPanel(List.of(InlineRun.of("make me bold")));
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.select(5, 7); // "me"
            text.toggleStyle(InlineStyle.BOLD, InlineStyle.TRUE);

            assertThat(paragraph.getText()).containsExactly(
                    InlineRun.of("make "),
                    InlineRun.styled("me", InlineStyle.BOLD, InlineStyle.TRUE),
                    InlineRun.of(" bold"));
            assertThat(postedEdits).hasSize(1);

            // toggling again removes it and merges the runs back together
            text.select(5, 7);
            text.toggleStyle(InlineStyle.BOLD, InlineStyle.TRUE);
            assertThat(paragraph.getText()).containsExactly(InlineRun.of("make me bold"));
        });
    }

    @Test
    void clickingInsideASpanSelectsIt() throws Exception {
        buildPanel(List.of(
                InlineRun.of("see "),
                InlineRun.styled("the manual", InlineStyle.DITA_INLINE, "term"),
                InlineRun.of(" please")));
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.selectSpanAt(7); // inside "the manual"
            assertThat(text.getSelectedText()).isEqualTo("the manual");
            // plain text: no selection
            text.select(1, 1);
            text.selectSpanAt(1);
            assertThat(text.getSelectedText()).isNull();
        });
    }

    @Test
    void clearFormattingStripsStylesButKeepsReferences() throws Exception {
        buildPanel(List.of(
                InlineRun.styled("styled", InlineStyle.BOLD, InlineStyle.TRUE),
                InlineRun.of(" and "),
                new InlineRun("a link", Map.of(InlineStyle.HREF, "x.dita",
                        InlineStyle.BOLD, InlineStyle.TRUE))));
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.selectAll();
            text.clearFormatting();

            List<InlineRun> runs = paragraph.getText();
            // bold gone everywhere
            assertThat(runs).noneMatch(r -> InlineStyle.TRUE.equals(r.attrs().get(InlineStyle.BOLD)));
            // the link target survives
            assertThat(runs).anyMatch(r -> "x.dita".equals(r.attrs().get(InlineStyle.HREF)));
            assertThat(paragraph.getPlainText()).isEqualTo("styled and a link");
        });
    }

    @Test
    void editedContentAppearsInExportedXml() throws Exception {
        buildPanel(List.of(InlineRun.of("original")));
        AtomicReference<String> xml = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                TextBlockComponent text = findText(panel);
                text.getStyledDocument().remove(0, text.getStyledDocument().getLength());
                text.getStyledDocument().insertString(0, "rewritten", null);
                // exportContent() flushes pending edits itself
                xml.set(panel.exportContent().asXML());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(xml.get()).contains("<p>rewritten</p>");
    }

    @Test
    void modelChangesFromOutsideRefreshTheView() throws Exception {
        buildPanel(List.of(InlineRun.of("before")));
        SwingUtilities.invokeAndWait(() -> {
            var tx = doc.begin("external");
            tx.setText(paragraph, List.of(InlineRun.of("after")));
            tx.commit();
            assertThat(findText(panel).getText()).isEqualTo("after");
        });
    }

    @Test
    void applyLinkOverASelectionKeepsItsReferenceThroughTheSanitizer() throws Exception {
        buildPanel(List.of(InlineRun.of("see the guide now")));
        AtomicReference<List<InlineRun>> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.select(8, 13);   // "guide"
            text.applyLink("guide.dita", null, null);
            text.commitPending();
            after.set(paragraph.getText());
        });
        assertThat(after.get()).extracting(InlineRun::text).containsExactly("see the ", "guide", " now");
        assertThat(after.get().get(1).attrs()).containsEntry(InlineStyle.HREF, "guide.dita");

        // pasting a link run keeps it a link: programmatic inserts bypass the typing sanitizer
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.setCaretPosition(text.getDocument().getLength());
            text.insertRuns(List.of(new InlineRun(" more", Map.of(InlineStyle.HREF, "more.dita"))));
            text.commitPending();
            after.set(paragraph.getText());
        });
        assertThat(after.get().get(after.get().size() - 1).attrs()).containsEntry(InlineStyle.HREF, "more.dita");
    }

    @Test
    void togglingAnInlineElementAwayDropsItsOwnAttributesButClearFormattingKeepsAKeyref() throws Exception {
        buildPanel(List.of(InlineRun.of("a "),
                new InlineRun("term", Map.of(InlineStyle.DITA_INLINE, "keyword", "keyword:keyref", "prod",
                        "keyword:outputclass", "x")),
                InlineRun.of(" b")));
        AtomicReference<List<InlineRun>> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.select(2, 6);
            text.clearFormatting();
            after.set(paragraph.getText());
        });
        assertThat(after.get().get(1).attrs()).containsEntry(InlineStyle.KEYREF, "prod")
                .doesNotContainKey("keyword:outputclass").doesNotContainKey(InlineStyle.DITA_INLINE);

        buildPanel(List.of(new InlineRun("term", Map.of(InlineStyle.DITA_INLINE, "keyword", "keyword:outputclass", "x"))));
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.select(0, 4);
            text.toggleStyle(InlineStyle.DITA_INLINE, "term");   // replaces keyword: its attributes go
            after.set(paragraph.getText());
        });
        assertThat(after.get().get(0).attrs()).containsEntry(InlineStyle.DITA_INLINE, "term")
                .doesNotContainKey("keyword:outputclass");
    }

    @Test
    void copyAndPasteKeepInlineMarkupAndFoldPastedLineBreaks() throws Exception {
        buildPanel(List.of(InlineRun.of("plain "), new InlineRun("bold", Map.of(InlineStyle.BOLD, InlineStyle.TRUE)),
                InlineRun.of(" tail")));
        AtomicReference<List<InlineRun>> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.select(6, 10);   // "bold"
            java.awt.datatransfer.Transferable t = new TextBlockComponent.RunsTransferable(
                    text.extractRunsRange(6, 10), text.getSelectedText());
            text.setCaretPosition(text.getDocument().getLength());
            text.getTransferHandler().importData(new javax.swing.TransferHandler.TransferSupport(text, t));
            text.commitPending();
            after.set(paragraph.getText());
        });
        assertThat(after.get()).extracting(InlineRun::text).containsExactly("plain ", "bold", " tail", "bold");
        assertThat(after.get().get(3).attrs()).containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);

        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            text.setCaretPosition(text.getDocument().getLength());
            text.getTransferHandler().importData(new javax.swing.TransferHandler.TransferSupport(text,
                    new java.awt.datatransfer.StringSelection(" two\nlines")));
            text.commitPending();
            after.set(paragraph.getText());
        });
        // the paste inherits the caret's run styling, as in a word processor; its line break is folded
        assertThat(after.get().get(after.get().size() - 1).text()).endsWith(" two lines").doesNotContain("\n");
    }
}
