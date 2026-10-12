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

package com.dogsbay.dogsbayaieditor.links;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.links.ProfilingValueAtCaret.Located;

class ProfilingValueAtCaretTest {

    /** Locates with the caret at the {@code |} marker. */
    private Located at(String textWithCaret) {
        int offset = textWithCaret.indexOf('|');
        return ProfilingValueAtCaret.locate(textWithCaret.replace("|", ""), offset);
    }

    @Test
    void caretInMultiTokenValue_picksTokenAtCaret() {
        Located located = at("<p product=\"widget_v1 widg|et_v2\">x</p>");
        assertNotNull(located);
        assertEquals("product", located.attribute());
        assertEquals("widget_v2", located.token());
    }

    @Test
    void caretOnAttributeName_picksFirstToken() {
        Located located = at("<p prod|uct=\"widget_v1 widget_v2\">x</p>");
        assertNotNull(located);
        assertEquals("product", located.attribute());
        assertEquals("widget_v1", located.token());
    }

    @Test
    void allProfilingAttributesRecognized() {
        for (String attr : new String[] {"product", "audience", "platform",
                "otherprops", "props"}) {
            Located located = at("<ph " + attr + "=\"to|ken\">x</ph>");
            assertNotNull(located, attr);
            assertEquals(attr, located.attribute());
            assertEquals("token", located.token());
        }
    }

    @Test
    void ditavalRule_attributeFromAtt() {
        Located located = at("<prop att=\"product\" val=\"widget|_v1\" action=\"exclude\"/>");
        assertNotNull(located);
        assertEquals("product", located.attribute());
        assertEquals("widget_v1", located.token());
    }

    @Test
    void ditavalRule_withoutAtt_attributeNull() {
        Located located = at("<prop val=\"widget|_v1\" action=\"exclude\"/>");
        assertNotNull(located);
        assertNull(located.attribute(), "att-less rule: caller prompts");
        assertEquals("widget_v1", located.token());
    }

    @Test
    void nonProfilingAttributes_null() {
        assertNull(at("<xref href=\"intro.di|ta\">x</xref>"), "href is not profiling");
        assertNull(at("<p id=\"le|gal\">x</p>"), "id is not profiling");
        assertNull(at("<x val=\"a|\">x</x>"), "val only counts on <prop>");
    }

    @Test
    void caretOutsideAnyTag_null() {
        assertNull(at("<p product=\"a\">some te|xt</p>"));
        assertNull(at("te|xt before any tag <p product=\"a\"/>"));
        assertNull(ProfilingValueAtCaret.locate(null, 0));
    }
}
