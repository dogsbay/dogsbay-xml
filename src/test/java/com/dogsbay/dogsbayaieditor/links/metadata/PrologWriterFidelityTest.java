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

package com.dogsbay.dogsbayaieditor.links.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Change;
import com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Mode;

/**
 * The surgical write must change a file <em>only</em> by the inserted/changed metadata —
 * DOCTYPE, the DOCTYPE↔root blank line, unrelated elements, and the trailing newline stay
 * byte-identical, and inserted nodes are indented to match siblings.
 */
class PrologWriterFidelityTest {

    @TempDir Path dir;

    private File write(String name, String content) throws Exception {
        File f = dir.resolve(name).toFile();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void freshPrologInsertedWithoutTouchingDoctypeOrEof() throws Exception {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
                + "\n"
                + "<concept id=\"x\">\n"
                + "  <title>T</title>\n"
                + "  <conbody>\n"
                + "    <p>body</p>\n"
                + "  </conbody>\n"
                + "</concept>\n";
        File f = write("t.dita", in);

        PrologWriter.apply(f, List.of(new Change(MetadataField.CREATED, "2024-06-01", Mode.FILL)), false);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
                + "\n"
                + "<concept id=\"x\">\n"
                + "  <title>T</title>\n"
                + "  <prolog>\n"
                + "    <critdates>\n"
                + "      <created date=\"2024-06-01\"/>\n"
                + "    </critdates>\n"
                + "  </prolog>\n"
                + "  <conbody>\n"
                + "    <p>body</p>\n"
                + "  </conbody>\n"
                + "</concept>\n";
        assertEquals(expected, read(f));
    }

    @Test
    void insertIntoExistingPrologAddsOnlyTheNewLine() throws Exception {
        String in = "<concept id=\"x\">\n"
                + "  <title>T</title>\n"
                + "  <prolog>\n"
                + "    <critdates>\n"
                + "      <created date=\"2024-01-01\"/>\n"
                + "    </critdates>\n"
                + "  </prolog>\n"
                + "  <conbody/>\n"
                + "</concept>\n";
        File f = write("t.dita", in);

        PrologWriter.apply(f, List.of(new Change(MetadataField.REVISED, "2024-06-01", Mode.FILL)), false);

        String expected = "<concept id=\"x\">\n"
                + "  <title>T</title>\n"
                + "  <prolog>\n"
                + "    <critdates>\n"
                + "      <created date=\"2024-01-01\"/>\n"
                + "      <revised modified=\"2024-06-01\"/>\n"
                + "    </critdates>\n"
                + "  </prolog>\n"
                + "  <conbody/>\n"
                + "</concept>\n";
        assertEquals(expected, read(f));
    }

    @Test
    void setOnExistingChangesOnlyTheAttribute() throws Exception {
        String in = "<concept id=\"x\">\n"
                + "  <title>T</title>\n"
                + "  <prolog>\n"
                + "    <critdates>\n"
                + "      <created date=\"2024-01-01\"/>\n"
                + "    </critdates>\n"
                + "  </prolog>\n"
                + "</concept>\n";
        File f = write("t.dita", in);

        PrologWriter.apply(f, List.of(new Change(MetadataField.CREATED, "2099-12-31", Mode.SET)), false);

        assertEquals(in.replace("2024-01-01", "2099-12-31"), read(f));
    }

    @Test
    void multipleChangesSharingAWrapperInsertItOnce() throws Exception {
        String in = "<concept id=\"x\">\n  <title>T</title>\n  <conbody/>\n</concept>\n";
        File f = write("t.dita", in);

        // Two keyword appends share one freshly-created <metadata><keywords> wrapper.
        PrologWriter.apply(f, List.of(
                new Change(MetadataField.KEYWORD, "audio", Mode.APPEND),
                new Change(MetadataField.KEYWORD, "editing", Mode.APPEND)), false);

        String out = read(f);
        int keywordsBlocks = out.split("<keywords>", -1).length - 1;
        assertEquals(1, keywordsBlocks, "wrapper inserted once, not duplicated:\n" + out);
        assertTrue(out.contains("<keyword>audio</keyword>"), out);
        assertTrue(out.contains("<keyword>editing</keyword>"), out);
    }

    @Test
    void insertIntoSelfClosingParentNestsInside() throws Exception {
        String in = "<concept id=\"x\">\n  <title>T</title>\n  <prolog/>\n  <conbody/>\n</concept>\n";
        File f = write("t.dita", in);

        PrologWriter.apply(f, List.of(new Change(MetadataField.CREATED, "2024-06-01", Mode.FILL)), false);

        String out = read(f);
        // created must be INSIDE the prolog, not spliced after a still-empty <prolog/>.
        assertTrue(!out.contains("<prolog/>"), "self-closing prolog expanded:\n" + out);
        assertTrue(out.matches("(?s).*<prolog>\\s*<critdates>\\s*<created date=\"2024-06-01\"/>"
                + "\\s*</critdates>\\s*</prolog>.*"), "created nested inside prolog:\n" + out);
    }

    @Test
    void utf8BomIsPreserved() throws Exception {
        String body = "<concept id=\"x\">\n  <title>T</title>\n  <conbody/>\n</concept>\n";
        File f = dir.resolve("t.dita").toFile();
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] out = new byte[bom.length + body.getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body.getBytes(StandardCharsets.UTF_8), 0, out, bom.length, out.length - bom.length);
        Files.write(f.toPath(), out);

        PrologWriter.apply(f, List.of(new Change(MetadataField.CREATED, "2024-06-01", Mode.FILL)), false);

        byte[] result = Files.readAllBytes(f.toPath());
        assertTrue(result.length >= 3 && result[0] == (byte) 0xEF && result[1] == (byte) 0xBB
                && result[2] == (byte) 0xBF, "BOM preserved");
        assertTrue(read(f).contains("  <prolog>\n"), "prolog inserted after the BOM");
    }

    @Test
    void crlfFileStaysCrlf() throws Exception {
        String in = "<concept id=\"x\">\r\n"
                + "  <title>T</title>\r\n"
                + "  <conbody/>\r\n"
                + "</concept>\r\n";
        File f = write("t.dita", in);

        PrologWriter.apply(f, List.of(new Change(MetadataField.CREATED, "2024-06-01", Mode.FILL)), false);

        String out = read(f);
        assertTrue(out.contains("\r\n"), "CRLF preserved");
        assertTrue(!out.contains("\n\n") , "no lone-LF lines introduced");
        assertTrue(out.contains("  <prolog>\r\n"), "prolog inserted with CRLF + 2-space indent:\n" + out);
        assertTrue(out.endsWith("</concept>\r\n"), "trailing CRLF preserved");
    }
}
