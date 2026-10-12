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

package com.dogsbay.dogsbayaieditor.links.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P1: positioned, round-trippable map model. */
class MapModelTest {

    @TempDir Path dir;

    private java.io.File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    @Test
    void buildsTreeWithAllAttributesAndOffsets() throws Exception {
        java.io.File m = write("guide.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <topichead navtitle="Intro">
                <topicref href="intro.dita" toc="yes" id="r-intro"/>
                <topicref href="setup.dita" scope="local" type="task"/>
              </topichead>
              <mapref href="other.ditamap"/>
            </map>
            """);

        MapModel model = MapModel.parse(m);
        assertThat(model.rootType()).isEqualTo("map");

        // top-level children: topichead, mapref (title is not a nav element)
        assertThat(model.root().children()).hasSize(2);
        MapRef head = model.root().children().get(0);
        assertThat(head.type()).isEqualTo("topichead");
        assertThat(head.navtitle()).isEqualTo("Intro");
        assertThat(head.children()).hasSize(2);

        MapRef intro = head.children().get(0);
        assertThat(intro.type()).isEqualTo("topicref");
        assertThat(intro.attrs()).containsEntry("href", "intro.dita")
                .containsEntry("toc", "yes").containsEntry("id", "r-intro");
        assertThat(intro.resolvedFile()).isEqualTo(
                dir.resolve("intro.dita").toFile());
        // selector is @id when present, else the child path
        assertThat(intro.selector()).isEqualTo("r-intro");
        assertThat(head.children().get(1).selector()).isEqualTo("/1/2"); // no id → path

        // offsets are real and bracket the element text
        assertThat(intro.startOffset()).isGreaterThanOrEqualTo(0);
        assertThat(intro.endOffset()).isGreaterThan(intro.startOffset());
        assertThat(model.text().substring(intro.startOffset(), intro.endOffset()))
                .contains("intro.dita");

        assertThat(model.root().children().get(1).type()).isEqualTo("mapref");
    }

    @Test
    void readsNavtitleFromTopicmeta() throws Exception {
        java.io.File m = write("m.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <topicref href="a.dita"><topicmeta><navtitle>Alpha</navtitle></topicmeta></topicref>
            </map>
            """);
        MapModel model = MapModel.parse(m);
        assertThat(model.root().children().get(0).navtitle()).isEqualTo("Alpha");
    }

    @Test
    void findsByIdAndChildPath() throws Exception {
        java.io.File m = write("m.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <topichead navtitle="A">
                <topicref href="x.dita" id="x"/>
              </topichead>
            </map>
            """);
        MapModel model = MapModel.parse(m);
        assertThat(model.find("x")).isNotNull();
        assertThat(model.find("x").href()).isEqualTo("x.dita");
        assertThat(model.find("/1/1")).isSameAs(model.find("x"));
        assertThat(model.find("/1").type()).isEqualTo("topichead");
        assertThat(model.find("nope")).isNull();
        assertThat(model.find("/9/9")).isNull();
    }

    @Test
    void parsesBookmapNavigation() throws Exception {
        java.io.File m = write("book.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <bookmap><booktitle><mainbooktitle>B</mainbooktitle></booktitle>
              <chapter href="ch1.dita"><topicref href="s1.dita"/></chapter>
              <appendix href="app.dita"/>
            </bookmap>
            """);
        MapModel model = MapModel.parse(m);
        assertThat(model.rootType()).isEqualTo("bookmap");
        assertThat(model.root().children()).extracting(MapRef::type)
                .containsExactly("chapter", "appendix");
        assertThat(model.root().children().get(0).children().get(0).type())
                .isEqualTo("topicref");
    }
}
