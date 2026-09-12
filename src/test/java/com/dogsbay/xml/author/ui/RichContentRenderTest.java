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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.spi.DefaultReferenceResolver;

/** Keyref resolution, conref locking and code highlighting in the rendered view. */
class RichContentRenderTest {

    private static final File CORPUS = new File("src/test/resources/author/corpus");

    @Test
    void resolvedKeyrefsRenderAsKeyTextAndStayAtomic() throws Exception {
        File topic = new File(CORPUS, "topics/what-is-audacity.dita");
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setReferenceResolver(new DefaultReferenceResolver(
                    topic.toURI(), new File(CORPUS, "podcaster-guide.ditamap")));
            panel.setContent(AdapterTestSupport.parseResource(
                    "/author/corpus/topics/what-is-audacity.dita").getRootElement());

            List<TextBlockComponent> texts = findAll(panel);
            // the empty <keyword keyref="product-name"/> renders as "Audacity"
            assertThat(texts).anyMatch(t -> t.getText().contains("What is Audacity?"));
            // but the model still holds the zero-length reference run
            TextBlockComponent title = texts.stream()
                    .filter(t -> t.getText().contains("What is Audacity?")).findFirst().orElseThrow();
            assertThat(title.extractRuns()).anyMatch(r ->
                    r.text().isEmpty() && "product-name".equals(
                            r.attrs().get(com.dogsbay.xml.author.model.InlineStyle.KEYREF)));
        });
    }

    @Test
    void conrefBlocksRenderLockedWithResolvedContent() throws Exception {
        File topic = new File(CORPUS, "topics/recording-your-first-track.dita");
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setReferenceResolver(new DefaultReferenceResolver(topic.toURI(), null));
            panel.setContent(AdapterTestSupport.parseResource(
                    "/author/corpus/topics/recording-your-first-track.dita").getRootElement());

            // the conref'd step renders a locked label with the referenced text,
            // and no editable text component exists for it
            List<JLabel> labels = findLabels(panel);
            assertThat(labels).anyMatch(l -> l.getText() != null
                    && l.getText().contains("⧉") && l.getText().contains("Save"));
        });
    }

    @Test
    void codeHighlightingIsVisualOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(AdapterTestSupport.parse(
                    "<concept id='c'><title>T</title><conbody>"
                            + "<codeblock># comment line\nx = \"a string\"</codeblock>"
                            + "</conbody></concept>"));
            TextBlockComponent code = findAll(panel).stream()
                    .filter(t -> "codeblock".equals(t.getBlock().getType().getName()))
                    .findFirst().orElseThrow();
            // extraction is untouched by the visual highlighting
            assertThat(code.extractRuns()).hasSize(1);
            assertThat(code.extractRuns().get(0).text())
                    .isEqualTo("# comment line\nx = \"a string\"");
            assertThat(code.extractRuns().get(0).attrs()).isEmpty();
        });
    }

    private List<TextBlockComponent> findAll(java.awt.Container container) {
        List<TextBlockComponent> out = new ArrayList<>();
        collect(container, TextBlockComponent.class, out);
        return out;
    }

    private List<JLabel> findLabels(java.awt.Container container) {
        List<JLabel> out = new ArrayList<>();
        collect(container, JLabel.class, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T extends java.awt.Component> void collect(java.awt.Container container,
            Class<T> type, List<T> out) {
        for (java.awt.Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                out.add((T) child);
            }
            if (child instanceof java.awt.Container c) {
                collect(c, type, out);
            }
        }
    }
}
