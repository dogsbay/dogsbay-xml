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

package com.dogsbay.dogsbayaieditor.ditaproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSetTest {

    @TempDir Path root;

    private void write(String rel, String content) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent() == null ? root : p.getParent());
        Files.writeString(p, content);
    }

    private static List<String> names(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).sorted().toList();
    }

    @BeforeEach
    void fixture() throws Exception {
        // guide.ditamap -> a.dita, b.dita ; a.dita conref-> c.dita ; orphan unreferenced
        write("guide.ditamap", """
            <?xml version="1.0"?>
            <map><topicref href="a.dita"/><topicref href="b.dita"/></map>
            """);
        write("a.dita", """
            <?xml version="1.0"?>
            <topic id="a"><body><p conref="c.dita#c/note"/></body></topic>
            """);
        write("b.dita", "<?xml version=\"1.0\"?>\n<topic id=\"b\"><body><p>hi</p></body></topic>");
        write("c.dita", "<?xml version=\"1.0\"?>\n<topic id=\"c\"><body><p id=\"note\">reused</p></body></topic>");
        write("orphan.dita", "<?xml version=\"1.0\"?>\n<topic id=\"o\"><body><p>alone</p></body></topic>");
        write("topics/d.dita", "<?xml version=\"1.0\"?>\n<topic id=\"d\"><body><p>nested</p></body></topic>");
    }

    @Test
    void crawlIncludesReuseTargetsAndExcludesOrphan() {
        List<Path> set = FileSet.crawlMap(root, root.resolve("guide.ditamap"));
        // map + both topicref targets + the conref'd reuse target; NOT the orphan
        assertThat(names(set)).containsExactly("a.dita", "b.dita", "c.dita", "guide.ditamap");
        assertThat(names(set)).doesNotContain("orphan.dita", "d.dita");
    }

    @Test
    void forDeliverableCrawlsTheDeliverableMap() {
        Deliverable d = new Deliverable("default", root.resolve("guide.ditamap"), null, null);
        assertThat(names(FileSet.forDeliverable(root, d)))
                .containsExactly("a.dita", "b.dita", "c.dita", "guide.ditamap");
    }

    @Test
    void globMatchesTopLevelOnly() throws Exception {
        assertThat(names(FileSet.fromGlob(root, "*.dita")))
                .containsExactly("a.dita", "b.dita", "c.dita", "orphan.dita");
    }

    @Test
    void recursiveGlobMatchesNested() throws Exception {
        assertThat(names(FileSet.fromGlob(root, "**/*.dita"))).contains("d.dita");
    }

    @Test
    void midPathDoubleStarMatchesDirectChildren() throws Exception {
        // 'topics/**/*.dita' must match topics/d.dita (directly under topics),
        // which Java's raw glob does NOT — the collapse fallback fixes it.
        assertThat(names(FileSet.fromGlob(root, "topics/**/*.dita"))).contains("d.dita");
    }

    @Test
    void underRootReturnsAllXmlish() throws Exception {
        // guide.ditamap + a/b/c/orphan + topics/d = 6 xml-ish files
        assertThat(FileSet.underRoot(root)).hasSize(6);
    }

    @Test
    void resolveDispatchesOnPrefix() throws Exception {
        assertThat(names(FileSet.resolve(root, "map:guide.ditamap")))
                .containsExactly("a.dita", "b.dita", "c.dita", "guide.ditamap");
        assertThat(names(FileSet.resolve(root, "glob:*.dita")))
                .containsExactly("a.dita", "b.dita", "c.dita", "orphan.dita");
        assertThat(FileSet.resolve(root, "root")).hasSize(6);
    }
}
