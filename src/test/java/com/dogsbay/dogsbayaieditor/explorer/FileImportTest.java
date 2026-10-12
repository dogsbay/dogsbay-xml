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

package com.dogsbay.dogsbayaieditor.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.tree.DefaultTreeModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bringing files in from outside the project.
 *
 * <p>Reported 2026-09-29: three tutorial stages hand the reader a file and the only way in was Reveal in
 * System and a file manager — a trip out of the editor to do something the project tree is looking at.
 */
class FileImportTest {

    @TempDir Path project;
    @TempDir Path elsewhere;

    private DefaultTreeModel modelFor(FileSystemNode node) {
        return new DefaultTreeModel(node);
    }

    private FileSystemNode folder(Path dir) {
        return new FileSystemNode(dir.toFile());
    }

    @Test
    void filesAreCopiedIn() throws Exception {
        Path source = Files.writeString(elsewhere.resolve("intro.dita"), "<concept id=\"i\"/>");
        FileSystemNode node = folder(project);

        List<File> copied = FileImport.copyInto(null, List.of(source.toFile()), node, modelFor(node), null);

        assertThat(copied).singleElement().satisfies(f -> assertThat(f.getName()).isEqualTo("intro.dita"));
        assertThat(project.resolve("intro.dita")).exists();
        assertThat(source).exists(); // copied, not moved: the folder it came from is not ours to empty
    }

    @Test
    void aNameAlreadyTakenGetsAnotherRatherThanOverwriting() throws Exception {
        Files.writeString(project.resolve("intro.dita"), "<concept id=\"mine\"/>");
        Path source = Files.writeString(elsewhere.resolve("intro.dita"), "<concept id=\"theirs\"/>");
        FileSystemNode node = folder(project);

        FileImport.copyInto(null, List.of(source.toFile()), node, modelFor(node), null);

        assertThat(Files.readString(project.resolve("intro.dita"))).contains("mine");
        assertThat(project.toFile().list()).hasSize(2);
    }

    @Test
    void aFileDroppedOnAFileGoesToItsFolder() throws Exception {
        Path topics = Files.createDirectory(project.resolve("topics"));
        Path sibling = Files.writeString(topics.resolve("existing.dita"), "<concept id=\"e\"/>");
        Path source = Files.writeString(elsewhere.resolve("new.dita"), "<concept id=\"n\"/>");
        // Dropping onto a file means "here, beside this one" — the alternative is refusing a drop that
        // landed a few pixels off.
        FileSystemNode onto = new FileSystemNode(sibling.toFile());
        FileSystemNode root = folder(project);
        root.add(onto);

        FileImport.copyInto(null, List.of(source.toFile()), onto, modelFor(root), null);

        assertThat(topics.resolve("new.dita")).exists();
    }

    @Test
    void aFolderIsCopiedWithWhatIsInIt() throws Exception {
        Path images = Files.createDirectory(elsewhere.resolve("images"));
        Files.writeString(images.resolve("one.png"), "not really a png");
        FileSystemNode node = folder(project);

        FileImport.copyInto(null, List.of(images.toFile()), node, modelFor(node), null);

        assertThat(project.resolve("images/one.png")).exists();
    }

    @Test
    void droppingAFileOnItsOwnFolderDoesNothing() throws Exception {
        Path already = Files.writeString(project.resolve("here.dita"), "<concept id=\"h\"/>");
        FileSystemNode node = folder(project);

        List<File> copied = FileImport.copyInto(null, List.of(already.toFile()), node, modelFor(node), null);

        // Not "here (1).dita": a drop onto the folder something is already in is a slip, not a request.
        assertThat(copied).isEmpty();
        assertThat(project.toFile().list()).containsExactly("here.dita");
    }

    @Test
    void aLinkIsNotFollowed() throws Exception {
        // A link points at someone else's file. Copying what it points at can mean copying a home directory;
        // copying the link would leave the project depending on this machine.
        Path real = Files.createDirectory(elsewhere.resolve("real"));
        Files.writeString(real.resolve("a.txt"), "content");
        Path link = elsewhere.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException unsupported) {
            return; // a platform without links has nothing to test here
        }
        FileSystemNode node = folder(project);

        List<File> copied = FileImport.copyInto(null, List.of(link.toFile()), node, modelFor(node), null);

        assertThat(copied).isEmpty();
        assertThat(project.resolve("link")).doesNotExist();
    }

    @Test
    void aFileThatIsNotThereIsSkipped() {
        FileSystemNode node = folder(project);

        List<File> copied = FileImport.copyInto(null,
                List.of(elsewhere.resolve("gone.dita").toFile()), node, modelFor(node), null);

        assertThat(copied).isEmpty();
    }
}
