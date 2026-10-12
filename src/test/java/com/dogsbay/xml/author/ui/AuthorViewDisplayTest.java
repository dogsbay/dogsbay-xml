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

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.model.AuthorBlock;

/**
 * Display-driven editing tests: a real, realized frame with key events
 * dispatched through the component's normal key-binding pipeline. Direct
 * dispatch (not java.awt.Robot) keeps the tests deterministic under window
 * managers that deny focus to new windows (Wayland). Run via
 * {@code ./gradlew uiTest} with a display, or {@code xvfb-run} in CI.
 */
@Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "needs a display")
class AuthorViewDisplayTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    private JFrame frame;
    private AuthorEditorPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel = new AuthorEditorPanel();
            panel.setContent(AdapterTestSupport.parse("""
                    <concept id="ui-test">
                      <title>UI Test</title>
                      <conbody>
                        <p>first paragraph</p>
                        <p>second paragraph</p>
                      </conbody>
                    </concept>"""));
            frame = new JFrame("author-ui-test");
            frame.setContentPane(panel);
            frame.setSize(700, 500);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    private AuthorBlock firstParagraph() {
        return panel.getAuthorDocument().getRoot().getChildren().get(1).getChildren().get(0);
    }

    private TextBlockComponent focusParagraph(int caret) throws Exception {
        AtomicReference<TextBlockComponent> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = findText(panel);
            ref.set(text);
            text.requestFocusInWindow();
            text.setCaretPosition(caret);
        });
        return ref.get();
    }

    private TextBlockComponent findText(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof TextBlockComponent text
                    && text.getBlock() == firstParagraph()) {
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

    /** Sends a key through the component's normal processing pipeline. */
    private void pressKey(TextBlockComponent target, int keyCode, char keyChar) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            long now = System.currentTimeMillis();
            target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0,
                    keyCode, keyChar == KeyEvent.CHAR_UNDEFINED ? KeyEvent.CHAR_UNDEFINED : keyChar));
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_TYPED, now, 0,
                        KeyEvent.VK_UNDEFINED, keyChar));
            }
            target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0,
                    keyCode, keyChar));
        });
    }

    @Test
    void typedKeysReachTheModelThroughTheCommitPipeline() throws Exception {
        TextBlockComponent text = focusParagraph(0);
        pressKey(text, KeyEvent.VK_X, 'x');

        SwingUtilities.invokeAndWait(text::commitPending);
        assertThat(firstParagraph().getPlainText()).isEqualTo("xfirst paragraph");
    }

    @Test
    void enterKeySplitsTheFocusedParagraph() throws Exception {
        TextBlockComponent text = focusParagraph(5); // inside "first"
        pressKey(text, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);

        SwingUtilities.invokeAndWait(() -> {
            AuthorBlock conbody = panel.getAuthorDocument().getRoot().getChildren().get(1);
            assertThat(conbody.getChildren()).hasSize(3);
            assertThat(conbody.getChildren().get(0).getPlainText()).isEqualTo("first");
            assertThat(conbody.getChildren().get(1).getPlainText()).isEqualTo(" paragraph");
        });
    }

    @Test
    void backspaceOnEmptiedBlockRemovesIt() throws Exception {
        TextBlockComponent text = focusParagraph(0);
        SwingUtilities.invokeAndWait(() -> {
            try {
                text.getStyledDocument().remove(0, text.getStyledDocument().getLength());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        pressKey(text, KeyEvent.VK_BACK_SPACE, '\b');

        SwingUtilities.invokeAndWait(() -> {
            AuthorBlock conbody = panel.getAuthorDocument().getRoot().getChildren().get(1);
            assertThat(conbody.getChildren()).hasSize(1);
            assertThat(conbody.getChildren().get(0).getPlainText()).isEqualTo("second paragraph");
        });
    }

    @Test
    void aSpannedCellNamesItsSpanInTheBlockHeader() {
        var adapter = new com.dogsbay.xml.author.adapter.DitaBlockAdapter();
        var doc = adapter.importDocument(AdapterTestSupport.parse("""
            <reference id="r"><title>T</title><refbody><table><tgroup cols="3">
              <colspec colname="c1"/><colspec colname="c2"/><colspec colname="c3"/>
              <tbody><row><entry namest="c1" nameend="c3" morerows="2">wide</entry></row>
                <row><entry>a</entry><entry>b</entry><entry>c</entry></row></tbody>
            </tgroup></table></refbody></reference>"""));
        AuthorBlock spanned = com.dogsbay.xml.author.model.BlockOperations.flatten(doc.getRoot()).stream()
                .filter(b -> b.getAttribute("namest") != null).findFirst().orElseThrow();
        AuthorBlock plain = com.dogsbay.xml.author.model.BlockOperations.flatten(doc.getRoot()).stream()
                .filter(b -> "entry".equals(b.getType().getName()) && b.getAttribute("namest") == null)
                .findFirst().orElseThrow();
        assertThat(AuthorEditorPanel.spanSuffix(spanned)).isEqualTo(" \u00b7 cols c1-c3 \u00b7 +2 rows");
        assertThat(AuthorEditorPanel.spanSuffix(plain)).isEmpty();
    }
}
