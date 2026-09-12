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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.author.testkit.GoldenImages;

/**
 * Tier 2 (in-process GUI render + golden image) example for the comprehensive
 * test suite — see {@code plans/comprehensive-ui-test-suite.md}.
 *
 * <p>Renders real corpus topics offscreen (no display) via {@link GoldenImages}
 * and asserts <em>structural</em>, host-stable properties: content was drawn,
 * rendering is idempotent, and distinct inputs produce distinct output. It also
 * exercises the golden-comparison code path against a throwaway baseline so the
 * mechanism stays covered without committing host-specific PNGs.
 */
class AuthorRenderGoldenTest {

    private static final int W = 900;
    private static final int H = 1100;
    private static final Color BG = Color.WHITE;

    private static Element parse(String resource) throws Exception {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var in = AuthorRenderGoldenTest.class.getResourceAsStream(resource)) {
            Document doc = reader.read(in);
            return doc.getRootElement();
        }
    }

    private static BufferedImage renderTopic(String resource) throws Exception {
        Element root = parse(resource);
        AtomicReference<AuthorEditorPanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(root);
            ref.set(panel);
        });
        return GoldenImages.render(ref.get(), W, H);
    }

    @Test
    void rendersContentOffscreen() throws Exception {
        BufferedImage image = renderTopic("/author/corpus/recording-your-first-track.dita");

        assertThat(image.getWidth()).isEqualTo(W);
        assertThat(image.getHeight()).isEqualTo(H);
        assertThat(GoldenImages.isBlank(image)).as("topic should paint content").isFalse();
        // a populated topic paints a meaningful amount of non-background pixels
        assertThat(GoldenImages.contentPixelRatio(image, BG))
                .as("rendered content density")
                .isGreaterThan(0.01);
    }

    @Test
    void renderingIsIdempotent() throws Exception {
        BufferedImage a = renderTopic("/author/corpus/what-is-audacity.dita");
        BufferedImage b = renderTopic("/author/corpus/what-is-audacity.dita");
        // same topic, same size → essentially identical paint
        assertThat(GoldenImages.diffRatio(a, b))
                .as("repeated render of the same topic must be stable")
                .isLessThan(0.001);
    }

    @Test
    void distinctTopicsRenderDistinctly() throws Exception {
        BufferedImage a = renderTopic("/author/corpus/what-is-audacity.dita");
        BufferedImage b = renderTopic("/author/corpus/trimming-audio.dita");
        assertThat(GoldenImages.diffRatio(a, b))
                .as("different topics must produce visibly different output")
                .isGreaterThan(0.02);
    }

    @Test
    void goldenComparisonRoundTrips(@TempDir Path tempDir) throws Exception {
        BufferedImage image = renderTopic("/author/corpus/what-is-audacity.dita");
        Path golden = tempDir.resolve("author/what-is-audacity.png");

        // first call has no baseline → writes it and returns 0.0
        double bootstrap = GoldenImages.assertMatchesGolden(image, golden, 0.02);
        assertThat(bootstrap).isZero();

        // second call compares against the freshly written baseline → matches
        double ratio = GoldenImages.assertMatchesGolden(image, golden, 0.02);
        assertThat(ratio).isLessThan(0.02);
    }
}
