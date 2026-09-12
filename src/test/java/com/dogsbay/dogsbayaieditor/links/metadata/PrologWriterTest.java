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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Change;
import com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Mode;

/** M4: field-preserving prolog writer. */
class PrologWriterTest {

    @TempDir Path dir;

    private java.io.File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private String read(java.io.File f) throws Exception {
        return Files.readString(f.toPath());
    }

    @Test
    void fillCreatesPrologAndContainerLeavingBodyIntact() throws Exception {
        java.io.File f = write("t.dita",
                "<concept id=\"c\"><title>C</title><conbody><p>Body</p></conbody></concept>");
        var applied = PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUDIENCE, "administrator", Mode.FILL)), false);

        assertThat(applied).hasSize(1);
        String out = read(f);
        assertThat(out).contains("<prolog>").contains("<metadata>")
            .contains("audience").contains("administrator");
        assertThat(out).contains("<conbody><p>Body</p></conbody>"); // body untouched
        // prolog precedes the body
        assertThat(out.indexOf("<prolog>")).isLessThan(out.indexOf("<conbody>"));
        MetadataExtractor.extract(f); // still parseable + extractable
        assertThat(MetadataExtractor.extract(f).get(MetadataField.AUDIENCE))
            .containsExactly("administrator");
    }

    @Test
    void fillIsNoOpWhenPresentAndPreservesUnknownContent() throws Exception {
        java.io.File f = write("t.dita", """
            <concept id="c"><title>C</title>
              <prolog>
                <author>Jane</author>
                <resourceid id="x"/>
                <metadata><audience type="user"/></metadata>
              </prolog>
              <conbody/></concept>
            """);
        String before = read(f);
        var applied = PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUDIENCE, "admin", Mode.FILL)), false);

        assertThat(applied).isEmpty();           // audience already present → no change
        assertThat(read(f)).isEqualTo(before);   // file untouched
    }

    @Test
    void setOverwritesAndPreservesSiblings() throws Exception {
        java.io.File f = write("t.dita", """
            <concept id="c"><title>C</title>
              <prolog><author>Jane</author>
                <metadata><audience type="user"/></metadata></prolog><conbody/></concept>
            """);
        PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUDIENCE, "administrator", Mode.SET)), false);
        String out = read(f);
        assertThat(out).contains("administrator").doesNotContain("type=\"user\"");
        assertThat(out).contains("<author>Jane</author>"); // sibling preserved
    }

    @Test
    void appendAddsKeywordRemoveDropsIt() throws Exception {
        java.io.File f = write("t.dita",
                "<concept id=\"c\"><title>C</title><conbody/></concept>");
        PrologWriter.apply(f, List.of(
                new Change(MetadataField.KEYWORD, "setup", Mode.APPEND),
                new Change(MetadataField.KEYWORD, "install", Mode.APPEND)), false);
        assertThat(MetadataExtractor.extract(f).get(MetadataField.KEYWORD))
            .containsExactly("setup", "install");

        PrologWriter.apply(f,
                List.of(new Change(MetadataField.KEYWORD, "setup", Mode.REMOVE)), false);
        assertThat(MetadataExtractor.extract(f).get(MetadataField.KEYWORD))
            .containsExactly("install");
    }

    @Test
    void dryRunReportsButDoesNotWrite() throws Exception {
        java.io.File f = write("t.dita",
                "<concept id=\"c\"><title>C</title><conbody/></concept>");
        String before = read(f);
        var applied = PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUDIENCE, "admin", Mode.FILL)), true);
        assertThat(applied).hasSize(1);          // would change
        assertThat(read(f)).isEqualTo(before);   // but didn't write
    }

    @Test
    void crlfNewlinesArePreserved() throws Exception {
        java.io.File f = write("t.dita",
                "<concept id=\"c\">\r\n<title>C</title>\r\n<conbody/>\r\n</concept>\r\n");
        PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUTHOR, "Jane", Mode.FILL)), false);
        String out = read(f);
        assertThat(out).contains("\r\n").contains("Jane");
        // every newline is CRLF — no bare LF crept in (the round-trip didn't strip \r)
        assertThat(out.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void lfFileStaysLf() throws Exception {
        java.io.File f = write("t.dita",
                "<concept id=\"c\">\n<title>C</title>\n<conbody/>\n</concept>\n");
        PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUTHOR, "Jane", Mode.FILL)), false);
        assertThat(read(f)).contains("Jane").doesNotContain("\r");
    }

    @Test
    void fillAttrFieldAddsToExistingElement() throws Exception {
        // a second attribute (job) must land on the existing <audience>, not no-op
        // and not create a duplicate element
        java.io.File f = write("t.dita", """
            <concept id="c"><title>C</title>
              <prolog><metadata><audience type="user"/></metadata></prolog><conbody/></concept>
            """);
        PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUDIENCE_JOB, "installing", Mode.FILL)), false);
        MetadataSnapshot s = MetadataExtractor.extract(f);
        assertThat(s.get(MetadataField.AUDIENCE)).containsExactly("user");       // kept
        assertThat(s.get(MetadataField.AUDIENCE_JOB)).containsExactly("installing"); // added
        assertThat(read(f).split("<audience", -1).length - 1).isEqualTo(1); // single element
    }

    @Test
    void writesMapMetadataIntoTopicmeta() throws Exception { // A6
        // a map gets <topicmeta> (never a <prolog>), so a required-keyword/author
        // policy that applies to maps can be satisfied by the tool.
        java.io.File f = write("m.dita", "<map><title>M</title><topicref href=\"t.dita\"/></map>");
        var applied = PrologWriter.apply(f,
                List.of(new Change(MetadataField.AUTHOR, "X", Mode.FILL)), false);
        assertThat(applied).isNotEmpty();
        String out = read(f);
        assertThat(out).contains("<topicmeta>").contains("X");
        assertThat(out).doesNotContain("<prolog>");
    }

    @Test
    void mapTopicmetaKeepsChildOrder() throws Exception { // A6/#5: strict topicmeta order
        java.io.File f = write("m.dita", "<map><title>M</title><topicref href=\"t.dita\"/></map>");
        // append a keyword first, then add an author — author must still land
        // BEFORE <keywords> in the topicmeta (its content model is ordered).
        PrologWriter.apply(f, List.of(
                new Change(MetadataField.KEYWORD, "k", Mode.APPEND),
                new Change(MetadataField.AUTHOR, "X", Mode.FILL)), false);
        String out = read(f);
        assertThat(out.indexOf("<author>")).isLessThan(out.indexOf("<keywords>"));
    }

    @Test
    void leavesDitabaseAndBookmapAlone() throws Exception {
        // a <dita> ditabase has no single metadata home; a <bookmap>'s <bookmeta>
        // has a different content model than <topicmeta>, so both are left alone.
        for (String xml : List.of(
                "<dita><topic id=\"a\"><title>A</title></topic></dita>",
                "<bookmap><title>B</title><chapter href=\"c.dita\"/></bookmap>")) {
            java.io.File f = write("x.dita", xml);
            String before = read(f);
            assertThat(PrologWriter.apply(f,
                    List.of(new Change(MetadataField.AUTHOR, "X", Mode.FILL)), false)).isEmpty();
            assertThat(read(f)).isEqualTo(before);
        }
    }
}
