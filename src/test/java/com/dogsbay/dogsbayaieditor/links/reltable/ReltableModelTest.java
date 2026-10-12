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

package com.dogsbay.dogsbayaieditor.links.reltable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P1: reltable parse + projector. */
class ReltableModelTest {

    @TempDir Path dir;

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private static final String MAP = """
        <?xml version="1.0" encoding="UTF-8"?>
        <map>
          <title>Guide</title>
          <reltable>
            <relheader>
              <relcolspec type="concept"/>
              <relcolspec type="task"/>
              <relcolspec type="reference"/>
            </relheader>
            <relrow>
              <relcell><topicref href="c.dita"/></relcell>
              <relcell><topicref href="t.dita"/></relcell>
              <relcell><topicref href="r.dita"/></relcell>
            </relrow>
            <relrow>
              <relcell/>
              <relcell><topicref href="t2.dita"/></relcell>
              <relcell><topicref href="r.dita"/></relcell>
            </relrow>
          </reltable>
        </map>
        """;

    @Test
    void parsesColumnsRowsCellsAndTargets() throws Exception {
        File m = write("g.ditamap", MAP);
        ReltableModel model = ReltableModel.parse(m);
        assertThat(model.reltables()).hasSize(1);
        Reltable rt = model.reltables().get(0);
        assertThat(rt.columns()).extracting(Reltable.Column::type)
                .containsExactly("concept", "task", "reference");
        assertThat(rt.rows()).hasSize(2);
        assertThat(rt.rows().get(0).cells()).hasSize(3);
        Reltable.Target c = rt.rows().get(0).cells().get(0).targets().get(0);
        assertThat(c.href()).isEqualTo("c.dita");
        assertThat(c.resolvedFile()).isEqualTo(dir.resolve("c.dita").toFile());
        // offsets bracket the reltable
        assertThat(model.text().substring(rt.startOffset(), rt.endOffset()))
                .startsWith("<reltable").endsWith("</reltable>");
    }

    @Test
    void projectorGeneratesBidirectionalLinksPerRow() throws Exception {
        File m = write("g.ditamap", MAP);
        var links = RelatedLinksProjector.project(ReltableModel.parse(m));
        File c = dir.resolve("c.dita").toFile();
        File t = dir.resolve("t.dita").toFile();
        File r = dir.resolve("r.dita").toFile();
        // row 1: c, t, r all interlink (normal, bidirectional)
        assertThat(links).anyMatch(l -> l.source().equals(c) && l.target().equals(t));
        assertThat(links).anyMatch(l -> l.source().equals(t) && l.target().equals(c));
        assertThat(links).anyMatch(l -> l.source().equals(c) && l.target().equals(r));
        // never links a topic to itself
        assertThat(links).noneMatch(l -> l.source().equals(l.target()));
        // row 2 has an empty first cell → t2 still links to r
        File t2 = dir.resolve("t2.dita").toFile();
        assertThat(links).anyMatch(l -> l.source().equals(t2) && l.target().equals(r));
    }

    @Test
    void dedupIsPerTableNotAcrossTables() throws Exception {
        // two reltables each relate a<->b; the link must appear once PER table, not be
        // suppressed in the second by the first.
        File m = write("d.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <reltable><relrow>
                <relcell><topicref href="a.dita"/></relcell>
                <relcell><topicref href="b.dita"/></relcell>
              </relrow></reltable>
              <reltable><relrow>
                <relcell><topicref href="a.dita"/></relcell>
                <relcell><topicref href="b.dita"/></relcell>
              </relrow></reltable>
            </map>
            """);
        var links = RelatedLinksProjector.project(ReltableModel.parse(m));
        File a = dir.resolve("a.dita").toFile();
        File b = dir.resolve("b.dita").toFile();
        assertThat(links).filteredOn(l -> l.source().equals(a) && l.target().equals(b))
                .hasSize(2); // one per table
    }

    @Test
    void linkingSourceonlyAndTargetonlyAreDirectional() throws Exception {
        File m = write("d.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <reltable>
                <relrow>
                  <relcell linking="sourceonly"><topicref href="a.dita"/></relcell>
                  <relcell linking="targetonly"><topicref href="b.dita"/></relcell>
                </relrow>
              </reltable>
            </map>
            """);
        var links = RelatedLinksProjector.project(ReltableModel.parse(m));
        File a = dir.resolve("a.dita").toFile();
        File b = dir.resolve("b.dita").toFile();
        assertThat(links).anyMatch(l -> l.source().equals(a) && l.target().equals(b));
        assertThat(links).noneMatch(l -> l.source().equals(b)); // b is targetonly
    }
}
