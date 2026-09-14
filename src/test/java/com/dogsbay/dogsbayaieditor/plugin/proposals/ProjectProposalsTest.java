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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.plugin.proposals.ProjectProposals.FileProposals;

class ProjectProposalsTest {

    private static final String INSERT = "<topic id=\"t\"><body><p status=\"new\" rev=\"ai:a\">added</p></body></topic>";
    private static final String PLAIN = "<topic id=\"t\"><body><p>nothing</p></body></topic>";

    @TempDir Path temp;

    private Path root() throws Exception {
        return Files.createDirectories(temp.resolve("proj"));
    }

    private static Path write(Path root, String rel, String text) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        return p;
    }

    private static List<String> names(Path root, List<FileProposals> found) {
        Path r = ProjectProposals.key(root);
        return found.stream().map(fp -> r.relativize(fp.file()).toString().replace('\\', '/')).toList();
    }

    @Test
    void findsEveryFileWithProposalsInPathOrder() throws Exception {
        Path root = root();
        write(root, "topics/b.dita", INSERT);
        write(root, "topics/a.dita", INSERT);
        write(root, "topics/plain.dita", PLAIN);
        write(root, "guide.ditamap", INSERT.replace("topic", "map"));
        write(root, "notes.txt", INSERT);

        List<FileProposals> found = ProjectProposals.scan(root, Map.of());

        assertThat(names(root, found)).containsExactly("guide.ditamap", "topics/a.dita", "topics/b.dita");
        assertThat(found.get(1).proposals()).hasSize(1);
    }

    @Test
    void skipsHiddenAndBuildFoldersButNotAHiddenRoot() throws Exception {
        Path root = Files.createDirectories(temp.resolve(".hidden/proj"));
        write(root, ".git/x.xml", INSERT);
        write(root, "out/html/t.dita", INSERT);
        write(root, "node_modules/p/t.xml", INSERT);
        write(root, "t.dita", INSERT);

        assertThat(names(root, ProjectProposals.scan(root, Map.of()))).containsExactly("t.dita");
        assertThat(ProjectProposals.inScope(root, root.resolve("t.dita"))).isTrue();
        assertThat(ProjectProposals.inScope(root, root.resolve("out/html/t.dita"))).isFalse();
        assertThat(ProjectProposals.inScope(root, root.resolve("t.txt"))).isFalse();
        assertThat(ProjectProposals.inScope(root, temp.resolve("elsewhere.dita"))).isFalse();
        assertThat(ProjectProposals.inScope(null, root.resolve("t.dita"))).isFalse();
    }

    @Test
    void walksASymlinkedRoot() throws Exception {
        Path real = root();
        write(real, "t.dita", INSERT);
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (Exception e) {
            assumeTrue(false, "symbolic links are not available here");
        }

        List<FileProposals> found = ProjectProposals.scan(link, Map.of());

        assertThat(found).extracting(FileProposals::file).containsExactly(ProjectProposals.key(link.resolve("t.dita")));
        assertThat(ProjectProposals.key(link.resolve("t.dita"))).isEqualTo(ProjectProposals.key(real.resolve("t.dita")));
    }

    @Test
    void prefersTheOpenBufferOverDisk() throws Exception {
        Path root = root();
        Path decided = write(root, "decided.dita", INSERT);
        Path unsaved = write(root, "unsaved.dita", PLAIN);

        List<FileProposals> found = ProjectProposals.scan(root, Map.of(
                ProjectProposals.key(decided), PLAIN,
                ProjectProposals.key(unsaved), INSERT));

        assertThat(found).extracting(FileProposals::file).containsExactly(ProjectProposals.key(unsaved));
    }

    @Test
    void readsTheDeclaredEncodingAndDropsAByteOrderMark() throws Exception {
        Path root = root();
        String latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<topic id=\"t\"><body><p status=\"new\" rev=\"ai:a\">café</p></body></topic>";
        Path latinFile = root.resolve("latin.dita");
        Files.write(latinFile, latin.getBytes(StandardCharsets.ISO_8859_1));
        Path bomFile = root.resolve("bom.dita");
        byte[] body = INSERT.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(bomFile, withBom);

        assertThat(ProjectProposals.read(latinFile)).isEqualTo(latin);
        assertThat(ProjectProposals.read(bomFile)).isEqualTo(INSERT);
        List<FileProposals> found = ProjectProposals.scan(root, Map.of());
        assertThat(found).hasSize(2);
        FileProposals bom = found.get(0);
        assertThat(bom.proposals().get(0).start())
                .as("offsets match the text without the mark").isEqualTo(ProjectProposals.of(bomFile, INSERT)
                        .proposals().get(0).start());
    }

    @Test
    void replacesOrRemovesOneFileKeepingOrder() throws Exception {
        Path root = root();
        Path a = root.resolve("a.dita");
        Path b = root.resolve("b.dita");
        Path c = root.resolve("c.dita");
        FileProposals fa = ProjectProposals.of(a, INSERT);
        FileProposals fb = ProjectProposals.of(b, INSERT);
        FileProposals fc = ProjectProposals.of(c, INSERT);
        List<FileProposals> files = List.of(fa, fc);

        assertThat(ProjectProposals.replace(files, b, fb)).containsExactly(fa, fb, fc);
        assertThat(ProjectProposals.replace(List.of(fa, fb), c, fc)).as("at the end").containsExactly(fa, fb, fc);
        assertThat(ProjectProposals.replace(List.of(), a, fa)).containsExactly(fa);
        assertThat(ProjectProposals.replace(files, a, null)).containsExactly(fc);
        assertThat(ProjectProposals.of(a, PLAIN)).isNull();
        assertThat(ProjectProposals.scan(root.resolve("missing"), Map.of())).isEmpty();
        assertThat(ProjectProposals.scan(null, Map.of())).isEmpty();
    }
}
