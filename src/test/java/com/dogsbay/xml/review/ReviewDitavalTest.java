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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class ReviewDitavalTest {

    @Test
    void writesWellFormedFilterExcludingStruckText(@TempDir Path dir) throws Exception {
        Path f = ReviewDitaval.write(dir.resolve("work"));
        assertThat(f).hasFileName(ReviewDitaval.FILE_NAME).exists();
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f.toFile());
        Element prop = (Element) doc.getElementsByTagName("prop").item(0);
        assertThat(prop.getAttribute("att")).isEqualTo("otherprops");
        assertThat(prop.getAttribute("val")).isEqualTo(Marks.DELETED);
        assertThat(prop.getAttribute("action")).isEqualTo("exclude");
    }

    @Test
    void rewriteIsIdempotent(@TempDir Path dir) throws Exception {
        ReviewDitaval.write(dir);
        Path f = ReviewDitaval.write(dir);
        assertThat(Files.readString(f)).isEqualTo(ReviewDitaval.CONTENT);
    }
}
