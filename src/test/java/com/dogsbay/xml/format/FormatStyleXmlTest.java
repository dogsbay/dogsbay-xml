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

package com.dogsbay.xml.format;

import org.dom4j.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a saved {@code <format-style>} contains.
 *
 * <p>A settings file is something people read, diff and record in tutorials, so a new option must not
 * appear in one until it is asked for.
 */
class FormatStyleXmlTest {

    @Test
    @DisplayName("a default style writes no sort-attributes at all")
    void defaultStyleOmitsSortAttributes() {
        Element el = FormatStyleXml.toElement(FormatStyle.defaults());

        assertThat(el.attribute("sort-attributes"))
                .as("a project that never asked for sorting keeps the file it had before")
                .isNull();
    }

    @Test
    @DisplayName("asking for sorting writes it")
    void sortingStyleWritesSortAttributes() {
        Element el = FormatStyleXml.toElement(FormatStyle.defaults().withSortAttributes(true));

        assertThat(el.attributeValue("sort-attributes")).isEqualTo("true");
    }

    @Test
    @DisplayName("the attributes that predate the option are all still written")
    void theOlderAttributesAreUnchanged() {
        Element el = FormatStyleXml.toElement(FormatStyle.defaults());

        // These ten were always written, and a tutorial's recorded settings file expects them.
        assertThat(el.attributes().stream().map(a -> a.getName()).toList())
                .containsExactlyInAnyOrder("indent", "size", "max-line-width", "preserve-mixed",
                        "newline", "final-newline", "preserve-text-breaks", "preserve-blank-lines",
                        "text-continuation", "trim-whitespace");
    }

    @Test
    @DisplayName("a style survives being written and read back, sorting or not")
    void roundTripsEitherWay() {
        for (boolean sort : new boolean[] {false, true}) {
            FormatStyle style = FormatStyle.defaults().withSortAttributes(sort);

            FormatStyle back = FormatStyleXml.parse(FormatStyleXml.toElement(style));

            assertThat(back.sortAttributes()).as("sort=" + sort).isEqualTo(sort);
            assertThat(back).isEqualTo(style);
        }
    }
}
