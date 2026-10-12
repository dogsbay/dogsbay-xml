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
import org.w3c.dom.Document;

class LineNumberedDomTest {

    @TempDir Path dir;

    @Test
    void stampsEachElementStartLine() throws Exception {
        Path f = dir.resolve("d.xml");
        Files.writeString(f, "<root>\n  <a/>\n  <b>\n    <c/>\n  </b>\n</root>");

        Document doc = LineNumberedDom.parse(f.toFile());

        assertThat(LineNumberedDom.lineOf(doc.getDocumentElement())).isEqualTo(1);
        assertThat(LineNumberedDom.lineOf(doc.getElementsByTagName("a").item(0))).isEqualTo(2);
        assertThat(LineNumberedDom.lineOf(doc.getElementsByTagName("b").item(0))).isEqualTo(3);
        assertThat(LineNumberedDom.lineOf(doc.getElementsByTagName("c").item(0))).isEqualTo(4);
    }

    @Test
    void lineOfNullNodeIsMinusOne() {
        assertThat(LineNumberedDom.lineOf(null)).isEqualTo(-1);
    }
}
