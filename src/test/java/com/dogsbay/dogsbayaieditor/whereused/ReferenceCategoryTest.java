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

package com.dogsbay.dogsbayaieditor.whereused;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.links.Reference;

/** Tests for the semantic reference classifier. */
class ReferenceCategoryTest {

    private static Reference ref(String element, String attribute) {
        return new Reference(null, 1, element, attribute, "value", null, null, "");
    }

    @Test
    void mapElements_inMaps() {
        assertEquals(ReferenceCategory.IN_MAPS,
                ReferenceCategory.classify(ref("topicref", "href")));
        assertEquals(ReferenceCategory.IN_MAPS,
                ReferenceCategory.classify(ref("mapref", "href")));
        assertEquals(ReferenceCategory.IN_MAPS,
                ReferenceCategory.classify(ref("chapter", "href")));
        assertEquals(ReferenceCategory.IN_MAPS,
                ReferenceCategory.classify(ref("keydef", "href")));
    }

    @Test
    void conrefFamily_contentReuse() {
        assertEquals(ReferenceCategory.CONTENT_REUSE,
                ReferenceCategory.classify(ref("p", "conref")));
        assertEquals(ReferenceCategory.CONTENT_REUSE,
                ReferenceCategory.classify(ref("note", "conkeyref")));
    }

    @Test
    void textKeyref_contentReuse() {
        assertEquals(ReferenceCategory.CONTENT_REUSE,
                ReferenceCategory.classify(ref("ph", "keyref")));
        assertEquals(ReferenceCategory.CONTENT_REUSE,
                ReferenceCategory.classify(ref("keyword", "keyref")));
    }

    @Test
    void xinclude_contentReuse() {
        assertEquals(ReferenceCategory.CONTENT_REUSE,
                ReferenceCategory.classify(ref("include", "href")));
    }

    @Test
    void xrefAndLink_links_regardlessOfHrefOrKeyref() {
        assertEquals(ReferenceCategory.LINKS,
                ReferenceCategory.classify(ref("xref", "href")));
        assertEquals(ReferenceCategory.LINKS,
                ReferenceCategory.classify(ref("xref", "keyref")));
        assertEquals(ReferenceCategory.LINKS,
                ReferenceCategory.classify(ref("link", "href")));
    }

    @Test
    void images() {
        assertEquals(ReferenceCategory.IMAGES,
                ReferenceCategory.classify(ref("image", "href")));
        assertEquals(ReferenceCategory.IMAGES,
                ReferenceCategory.classify(ref("image", "keyref")));
        assertEquals(ReferenceCategory.IMAGES,
                ReferenceCategory.classify(ref("object", "src")));
    }

    @Test
    void fallback_other() {
        assertEquals(ReferenceCategory.OTHER,
                ReferenceCategory.classify(ref("custom-thing", "href")));
    }
}
