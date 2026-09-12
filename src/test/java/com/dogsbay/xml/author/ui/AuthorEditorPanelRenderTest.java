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

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;

/**
 * Renderer smoke tests: build the panel for a real topic (no window needed —
 * Swing lightweights have no peers until shown) and verify the component tree
 * mirrors the block tree.
 */
class AuthorEditorPanelRenderTest {

    private static org.dom4j.Element parseResource(String path) throws Exception {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var in = AuthorEditorPanelRenderTest.class.getResourceAsStream(path)) {
            Document doc = reader.read(in);
            return doc.getRootElement();
        }
    }

    @Test
    void rendersEveryBlockOfACorpusTopic() throws Exception {
        var root = parseResource("/author/corpus/recording-your-first-track.dita");
        AtomicReference<AuthorEditorPanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(root);
            ref.set(panel);
        });
        AuthorEditorPanel panel = ref.get();
        AuthorDocument doc = panel.getAuthorDocument();
        assertThat(doc).isNotNull();
        assertThat(doc.getRoot().getType().getName()).isEqualTo("task");

        int blockCount = countBlocks(doc.getRoot());
        assertThat(panel.getRenderedBlockCount()).isEqualTo(blockCount);
        // every block id has a live component in the hierarchy
        assertThat(panel.getBlockComponent(doc.getRoot().getId())).isNotNull();

        // the title text is rendered in a text pane
        List<TextBlockComponent> textPanes = findAll(panel, TextBlockComponent.class);
        assertThat(textPanes)
                .anyMatch(tp -> tp.getText().contains("Recording Your First Track"));
        // keyref placeholders are visible
        assertThat(textPanes)
                .anyMatch(tp -> tp.getText().contains("⟨product-name⟩"));
    }

    @Test
    void selectionUpdatesBreadcrumbAndHighlight() throws Exception {
        var root = parseResource("/author/corpus/what-is-audacity.dita");
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(root);
            AuthorDocument doc = panel.getAuthorDocument();
            AuthorBlock firstP = doc.getRoot().getChildren().get(1).getChildren().get(0);

            List<AuthorBlock> notified = new ArrayList<>();
            panel.addSelectionListener(notified::add);
            panel.setSelectedBlock(firstP);

            assertThat(panel.getSelectedBlock()).isSameAs(firstP);
            assertThat(notified).containsExactly(firstP);
            JComponent view = panel.getBlockComponent(firstP.getId());
            assertThat(view.getClientProperty("author.selected"))
                    .isEqualTo(Boolean.TRUE); // selection highlight active
        });
    }

    @Test
    void rebuildSurvivesDocumentReplacement() throws Exception {
        var rootA = parseResource("/author/corpus/what-is-audacity.dita");
        var rootB = parseResource("/author/corpus/trimming-audio.dita");
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(rootA);
            int countA = panel.getRenderedBlockCount();
            panel.setContent(rootB);
            assertThat(panel.getRenderedBlockCount()).isPositive();
            panel.setContent(null);
            assertThat(panel.getRenderedBlockCount()).isZero();
            assertThat(countA).isPositive();
        });
    }

    private int countBlocks(AuthorBlock block) {
        int n = 1;
        // children of conref'd blocks are not rendered (referenced content shows instead)
        if (block.getAttribute("conref") != null) {
            return n;
        }
        for (AuthorBlock child : block.getChildren()) {
            n += countBlocks(child);
        }
        return n;
    }

    private <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> void collect(Container container, Class<T> type, List<T> out) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                out.add((T) child);
            }
            if (child instanceof Container c) {
                collect(c, type, out);
            }
        }
    }
}
