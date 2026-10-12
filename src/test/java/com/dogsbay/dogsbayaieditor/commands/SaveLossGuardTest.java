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

package com.dogsbay.dogsbayaieditor.commands;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The agent-facing save contract: refuse rather than lose.
 *
 * <p>A person gets a dialog and can judge it ({@code SaveEncodingWarning}). An agent would be handed
 * success and report the task done, so this path refuses instead.
 */
class SaveLossGuardTest {

    private static DogsBayDocument openedOn(Path file, String content, String encoding) throws Exception {
        Files.write(file, content.getBytes(Charset.forName(encoding)));
        DogsBayDocument doc = new DogsBayDocument(file.toUri().toURL());
        doc.load();
        return doc;
    }

    @Test
    @DisplayName("a character in a comment the encoding cannot store refuses the save")
    void refusesWhenACommentWouldLoseACharacter(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");
        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><!-- note → --><p>ok</p></root>\n");

        assertThatThrownBy(() -> SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("Not saved")
                .hasMessageContaining("U+2192")
                .hasMessageContaining("comment")
                .hasMessageContaining("UTF-8")
                .extracting(e -> ((CommandException) e).getCode())
                .isEqualTo(CommandException.ErrorCode.ENCODING_LOSS);
    }

    @Test
    @DisplayName("the refusal happens before anything is written")
    void refusesBeforeWriting(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        String original = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n";
        DogsBayDocument doc = openedOn(file, original, "ISO-8859-1");
        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><?pi ≈ ?><p>ok</p></root>\n");

        assertThatThrownBy(() -> SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc))
                .isInstanceOf(CommandException.class);

        assertThat(Files.readString(file, Charset.forName("ISO-8859-1")))
                .as("refusing must leave the file alone, not half-write it")
                .isEqualTo(original);
    }

    @Test
    @DisplayName("everything that can be saved is allowed through")
    void allowsWhatCanBeSaved(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        // text and attributes become references; a CDATA section becomes escaped text; all kept
        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p outputclass=\"café →\">a → b</p>"
                + "<p><![CDATA[c → d]]></p></root>\n");

        assertThatCode(() -> SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a UTF-8 document is never refused")
    void utf8IsNeverRefused(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>plain</p></root>\n", "UTF-8");
        doc.setText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root><!-- → 🎉 --><p>ok</p></root>\n");

        assertThatCode(() -> SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("every save on the RPC path goes through the guard")
    void noUnguardedSaveOnTheRpcPath() throws Exception {
        Path executor = Path.of("src/main/java/com/dogsbay/dogsbayaieditor/commands/EditorExecutor.java");
        assertThat(Files.exists(executor)).as("sources available").isTrue();

        // One `doc.save()` is allowed: the one inside saveGuarded. Any other is a save an agent could
        // make that silently loses a character, which is the whole point of the guard.
        long saves;
        try (Stream<String> lines = Files.lines(executor)) {
            saves = lines
                    .map(String::stripLeading)
                    .filter(l -> l.contains(".save()") && !l.startsWith("//") && !l.startsWith("*"))
                    .count();
        }
        assertThat(saves)
                .as("EditorExecutor must save only through saveGuarded(doc)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a batch refuses before writing any of its documents")
    void aBatchRefusesBeforeWritingAny(@TempDir Path dir) throws Exception {
        // Review proved the source-grep version of this passed with the bug fully present: it
        // anchored on executeClose's `if (cmd.all())` and was satisfied by saveGuarded's own body.
        // This asks the filesystem instead.
        Path fileA = dir.resolve("a.xml");
        Path fileB = dir.resolve("b.xml");
        DogsBayDocument a = openedOn(fileA,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");
        DogsBayDocument b = openedOn(fileB,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        a.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>edited</p></root>\n");
        b.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><!-- \u2192 --><p>ok</p></root>\n");

        byte[] before = Files.readAllBytes(fileA);

        assertThatThrownBy(() -> SaveLossGuard.refuseIfAnyWouldLoseCharacters(List.of(a, b)))
                .isInstanceOf(CommandException.class)
                .extracting(e -> ((CommandException) e).getCode())
                .isEqualTo(CommandException.ErrorCode.ENCODING_LOSS);

        assertThat(Files.readAllBytes(fileA))
                .as("the document before the offender must not have been written")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("a batch with nothing to lose is allowed through")
    void aCleanBatchIsAllowed(@TempDir Path dir) throws Exception {
        DogsBayDocument a = openedOn(dir.resolve("a.xml"),
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>caf\u00e9</p></root>\n", "ISO-8859-1");

        assertThatCode(() -> SaveLossGuard.refuseIfAnyWouldLoseCharacters(List.of(a)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SaveLossGuard.refuseIfAnyWouldLoseCharacters(List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> SaveLossGuard.refuseIfAnyWouldLoseCharacters(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a refusal after an edit says the buffer and the file disagree")
    void aRefusalAfterAnEditSaysSo() throws Exception {
        // persist() runs after its caller has applied the change and closed the undo step, so a
        // refusal there leaves the buffer ahead of the file. Reported as "not saved" alone, a caller
        // reads it as "nothing happened".
        Path executor = Path.of("src/main/java/com/dogsbay/dogsbayaieditor/commands/EditorExecutor.java");
        String source = Files.readString(executor);
        int persist = source.indexOf("private void persist(DogsBayView view)");
        assertThat(persist).isGreaterThan(0);
        String body = source.substring(persist, source.indexOf("\n    }", persist));

        assertThat(body).contains("NOT on disk");
    }
}
