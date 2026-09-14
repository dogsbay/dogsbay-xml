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

package com.dogsbay.dogsbayaieditor.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationMessagesTest {

    @Test
    void aDtdContentModelLosesItsEnumeration() {
        String full = "The content of element type \"context\" must match "
                + "\"(dita-ot-placeholder|p|ul|ol|sl|dl|note|lq|pre|codeblock|table|simpletable)*\".";

        assertThat(ValidationMessages.compact(full))
                .isEqualTo("The content of element type \"context\" does not match its content model.");
    }

    @Test
    void anXsdExpectedListIsDropped() {
        String full = "cvc-complex-type.2.4.a: Invalid content was found starting with element 'x'. "
                + "One of '{\"\":p, \"\":ul, \"\":ol}' is expected.";

        assertThat(ValidationMessages.compact(full))
                .isEqualTo("cvc-complex-type.2.4.a: Invalid content was found starting with element 'x'.");
    }

    @Test
    void otherMessagesAreUntouched() {
        assertThat(ValidationMessages.compact("Element type \"x\" must be declared."))
                .isEqualTo("Element type \"x\" must be declared.");
        assertThat(ValidationMessages.compact(null)).isNull();
    }

    @Test
    void xercesWordingIsWhatThisShortens(@TempDir Path dir) throws Exception {
        // Keeps the pattern honest against the bundled parser's wording, not a guess at it.
        Path file = Files.writeString(dir.resolve("t.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE doc [<!ELEMENT doc (a, b)><!ELEMENT a EMPTY><!ELEMENT b EMPTY>]>
                <doc><b/><a/></doc>
                """);
        java.util.List<String> messages = new java.util.ArrayList<>();
        var factory = new org.apache.xerces.jaxp.SAXParserFactoryImpl();
        factory.setValidating(true);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void error(org.xml.sax.SAXParseException e) {
                messages.add(e.getMessage());
            }
        });

        reader.parse(file.toUri().toString());

        assertThat(messages).anySatisfy(m -> assertThat(ValidationMessages.compact(m))
                .isEqualTo("The content of element type \"doc\" does not match its content model."));
    }
}
