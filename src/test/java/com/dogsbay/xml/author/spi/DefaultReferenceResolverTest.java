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

package com.dogsbay.xml.author.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;

class DefaultReferenceResolverTest {

    private static final File CORPUS = new File("src/test/resources/author/corpus");
    private static final File TOPIC = new File(CORPUS, "topics/recording-your-first-track.dita");
    private static final File MAP = new File(CORPUS, "podcaster-guide.ditamap");

    private final DefaultReferenceResolver resolver =
            new DefaultReferenceResolver(TOPIC.toURI(), MAP);

    @Test
    void resolvesKeysThroughSubmaps() {
        // defined in keydefs-product.ditamap, pulled in via <mapref>
        assertThat(resolver.keyText("product-name")).isEqualTo("Audacity");
        assertThat(resolver.keyText("no-such-key")).isNull();
    }

    @Test
    void resolvesHrefRelativeToTheDocument() {
        URI uri = resolver.resolveHref("what-is-digital-audio.dita");
        assertThat(uri).isNotNull();
        assertThat(new File(uri)).exists();
    }

    @Test
    void resolvesConrefAcrossFiles() {
        Element step = resolver.resolveConref("../shared/common-steps.dita#common-steps/save-project");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("step");
        assertThat(step.getStringValue()).contains("Save");
    }

    @Test
    void unresolvableConrefReturnsNull() {
        assertThat(resolver.resolveConref("missing.dita#x/y")).isNull();
        assertThat(resolver.resolveConref("../shared/common-steps.dita#common-steps/nope")).isNull();
    }

    @Test
    void worksWithoutMapOrBase() {
        DefaultReferenceResolver bare = new DefaultReferenceResolver(null, null);
        assertThat(bare.keyText("anything")).isNull();
        assertThat(bare.resolveConref("a.dita#b")).isNull();
    }
}
