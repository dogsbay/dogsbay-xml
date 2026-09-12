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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P2: formatting-preserving map edits. */
class MapEditorTest {

    @TempDir Path dir;

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p.toFile();
    }

    /** Apply a single-file plan and return the resulting text. */
    private static String applied(MapEditPlan plan, File file) {
        return plan.changes().stream().filter(c -> c.file().equals(file))
                .map(MapEditPlan.FileChange::newText).findFirst().orElseThrow();
    }

    private static final String GUIDE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <topicref href="intro.dita" id="intro"/>
              <topichead navtitle="Setup">
                <topicref href="install.dita" id="install"/>
              </topichead>
            </map>
            """;

    @Test
    void setAttrAddsReplacesAndRemoves() throws Exception {
        File m = write("g.ditamap", GUIDE);
        MapModel model = MapModel.parse(m);

        // add a new attribute
        String t1 = applied(MapEditor.setAttr(model, "intro", "toc", "yes"), m);
        assertThat(t1).contains("href=\"intro.dita\" id=\"intro\" toc=\"yes\"");

        // replace an existing one
        MapModel m2 = MapModel.parse(write("g2.ditamap", t1));
        String t2 = applied(MapEditor.setAttr(m2, "intro", "toc", "no"), new File(dir.toFile(), "g2.ditamap"));
        assertThat(t2).contains("toc=\"no\"").doesNotContain("toc=\"yes\"");

        // remove it (empty value)
        MapModel m3 = MapModel.parse(write("g3.ditamap", t2));
        String t3 = applied(MapEditor.setAttr(m3, "intro", "toc", ""), new File(dir.toFile(), "g3.ditamap"));
        assertThat(t3).doesNotContain("toc=");
        // everything else preserved
        assertThat(t3).contains("<title>Guide</title>").contains("navtitle=\"Setup\"");
    }

    @Test
    void insertAddsTopicrefAtIndexAndAppends() throws Exception {
        File m = write("g.ditamap", GUIDE);
        MapModel model = MapModel.parse(m);

        // insert as first child of the root
        String t1 = applied(MapEditor.insert(model, "/", 0, "topicref",
                Map.of("href", "first.dita")), m);
        MapModel after = MapModel.parse(write("a1.ditamap", t1));
        assertThat(after.root().children()).extracting(MapRef::type)
                .containsExactly("topicref", "topicref", "topichead");
        assertThat(after.root().children().get(0).href()).isEqualTo("first.dita");

        // append under the topichead
        String t2 = applied(MapEditor.insert(model, "/2", 99, "topicref",
                Map.of("href", "extra.dita")), m);
        MapModel after2 = MapModel.parse(write("a2.ditamap", t2));
        assertThat(after2.find("/2").children()).hasSize(2);
        assertThat(after2.find("/2").children().get(1).href()).isEqualTo("extra.dita");
    }

    @Test
    void insertRejectsIllegalNesting() throws Exception {
        File m = write("g.ditamap", GUIDE);
        MapModel model = MapModel.parse(m);
        // can't nest under a leaf topicref's sibling that is a mapref — use a mapref parent
        File mm = write("mm.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title><mapref href="sub.ditamap" id="s"/></map>
            """);
        MapModel mmodel = MapModel.parse(mm);
        assertThatThrownBy(() -> MapEditor.insert(mmodel, "s", 0, "topicref",
                Map.of("href", "x.dita"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeDropsSubtreeAndLeavesNoBlankLine() throws Exception {
        File m = write("g.ditamap", GUIDE);
        MapModel model = MapModel.parse(m);
        String t = applied(MapEditor.remove(model, "/2", null), m); // remove the topichead
        assertThat(t).doesNotContain("navtitle=\"Setup\"").doesNotContain("install.dita");
        assertThat(t).contains("intro.dita");
        assertThat(t).doesNotContain("\n\n"); // no blank line left behind
        MapModel after = MapModel.parse(write("r.ditamap", t));
        assertThat(after.root().children()).hasSize(1);
    }

    @Test
    void moveToALaterIndexReordersDownward() throws Exception {
        // [A, B, C] — move A to final index 1 → [B, A, C]; move first to last → [B, C, A]
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <topicref href="a.dita" id="A"/>
              <topicref href="b.dita" id="B"/>
              <topicref href="c.dita" id="C"/>
            </map>
            """);
        MapModel model = MapModel.parse(m);
        String t1 = applied(MapEditor.move(model, "A", null, "/", 1), m);
        MapModel after = MapModel.parse(write("d1.ditamap", t1));
        assertThat(after.root().children()).extracting(MapRef::id)
                .containsExactly("B", "A", "C");

        String t2 = applied(MapEditor.move(model, "A", null, "/", 2), m);
        assertThat(MapModel.parse(write("d2.ditamap", t2)).root().children())
                .extracting(MapRef::id).containsExactly("B", "C", "A");
    }

    @Test
    void moveReordersWithinTheMapPreservingText() throws Exception {
        File m = write("g.ditamap", GUIDE);
        MapModel model = MapModel.parse(m);
        // move the topichead (/2) to index 0
        String t = applied(MapEditor.move(model, "/2", null, "/", 0), m);
        MapModel after = MapModel.parse(write("mv.ditamap", t));
        assertThat(after.root().children()).extracting(MapRef::type)
                .containsExactly("topichead", "topicref");
        // the moved subtree kept its child verbatim
        assertThat(t).contains("<topicref href=\"install.dita\" id=\"install\"/>");
    }

    @Test
    void crossMapMoveRebasesHref() throws Exception {
        File src = write("a/src.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>S</title><topicref href="topics/x.dita" id="x"/></map>
            """);
        File dest = write("b/dest.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>D</title></map>
            """);
        MapModel srcModel = MapModel.parse(src);
        MapModel destModel = MapModel.parse(dest);
        MapEditPlan plan = MapEditor.move(srcModel, "x", destModel, "/", 0);

        // source loses it; destination gains it with a rebased href (a/topics/x.dita
        // is ../a/topics/x.dita relative to b/)
        assertThat(applied(plan, src)).doesNotContain("topics/x.dita");
        assertThat(applied(plan, dest)).contains("../a/topics/x.dita");
    }

    @Test
    void removeWarnsOnInboundReferences() throws Exception {
        write("guide.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title><topicref href="a.dita" id="a"/></map>
            """);
        write("a.dita", "<concept id=\"a\"><title>A</title><conbody/></concept>");
        // b.dita references a.dita → removing a's topicref should warn
        write("b.dita", """
            <concept id="b"><title>B</title>
              <conbody><xref href="a.dita"/></conbody></concept>
            """);
        MapModel model = MapModel.parse(new File(dir.toFile(), "guide.ditamap"));
        MapEditPlan plan = MapEditor.remove(model, "a", dir.toFile());
        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("a.dita"));
    }
}
