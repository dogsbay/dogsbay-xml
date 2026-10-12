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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One attribute, on one element, across a scope.
 *
 * <p>The gap this fills: {@code edit_structure} understands attributes but takes one document, and
 * {@code metadata_set} takes a scope but writes the prolog through a closed list of fields. Adding
 * {@code xml:lang} to thirty topics had no route that was both.
 */
class SetAttributeCommandTest {

    @TempDir Path dir;

    private final HeadlessExecutor executor = new HeadlessExecutor();

    private Path topic(String name, String root) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent() == null ? dir : file.getParent());
        Files.writeString(file, "<?xml version=\"1.0\"?>\n" + root + "\n  <title>T</title>\n</concept>\n");
        return file;
    }

    private BatchResultOf run(String select, String name, String value, boolean onlyIfAbsent, boolean dryRun)
            throws Exception {
        var result = executor.execute(new SetAttributeCommand(
                dir.toString(), null, select, name, value, onlyIfAbsent, dryRun));
        return new BatchResultOf(result.total(), result.failed(), result.findings().size());
    }

    /** total, to-change, findings — the three numbers a plan reports. */
    private record BatchResultOf(int total, int toChange, int findings) {}

    @Test
    void itSetsTheAttributeOnEveryTopic() throws Exception {
        Path a = topic("a.dita", "<concept id=\"a\">");
        Path b = topic("b.dita", "<concept id=\"b\">");

        var plan = run("/*", "xml:lang", "en", false, false);

        assertThat(plan.toChange()).isEqualTo(2);
        assertThat(Files.readString(a)).contains("xml:lang=\"en\"");
        assertThat(Files.readString(b)).contains("xml:lang=\"en\"");
    }

    @Test
    void aDryRunChangesNothing() throws Exception {
        Path a = topic("a.dita", "<concept id=\"a\">");
        String before = Files.readString(a);

        var plan = run("/*", "xml:lang", "en", false, true);

        assertThat(plan.toChange()).isEqualTo(1);
        assertThat(Files.readString(a)).isEqualTo(before);
    }

    @Test
    void onlyIfAbsentLeavesADeclaredLanguageAlone() throws Exception {
        // The reason the option exists: "add xml:lang where it is missing" must not overwrite the topic that
        // says de, which is the one file in the set where the answer is not en.
        Path german = topic("de.dita", "<concept id=\"d\" xml:lang=\"de\">");
        Path plain = topic("plain.dita", "<concept id=\"p\">");

        var plan = run("/*", "xml:lang", "en", true, false);

        assertThat(plan.toChange()).isEqualTo(1);
        assertThat(Files.readString(german)).contains("xml:lang=\"de\"");
        assertThat(Files.readString(plain)).contains("xml:lang=\"en\"");
    }

    @Test
    void withoutOnlyIfAbsentItReplaces() throws Exception {
        Path german = topic("de.dita", "<concept id=\"d\" xml:lang=\"de\">");

        var plan = run("/*", "xml:lang", "en", false, false);

        assertThat(plan.toChange()).isEqualTo(1);
        assertThat(Files.readString(german)).contains("xml:lang=\"en\"").doesNotContain("\"de\"");
    }

    @Test
    void aFileAlreadyAsAskedIsNotRewritten() throws Exception {
        Path a = topic("a.dita", "<concept id=\"a\" xml:lang=\"en\">");
        String before = Files.readString(a);

        var plan = run("/*", "xml:lang", "en", false, false);

        assertThat(plan.toChange()).isZero();
        assertThat(Files.readString(a)).isEqualTo(before);
    }

    @Test
    void theRestOfTheFileIsUntouched() throws Exception {
        // Why this runs through the structural editor rather than a parse and reserialize: a bulk change over
        // thirty files that reformatted them all would be unreviewable, whatever it got right.
        Path a = dir.resolve("a.dita");
        String before = "<?xml version=\"1.0\"?>\r\n<concept id=\"a\">\r\n"
                + "\t<title>T</title>\r\n\t<conbody>\r\n\t\t<p>one<b> two </b>three</p>\r\n"
                + "\t</conbody>\r\n</concept>\r\n";
        Files.writeString(a, before);

        run("/*", "xml:lang", "en", false, false);

        String after = Files.readString(a);
        assertThat(after).isEqualTo(before.replace("<concept id=\"a\">", "<concept id=\"a\" xml:lang=\"en\">"));
    }

    @Test
    void aFileTheSelectorDoesNotMatchIsSkipped() throws Exception {
        topic("a.dita", "<concept id=\"a\">");
        Files.writeString(dir.resolve("b.dita"),
                "<?xml version=\"1.0\"?>\n<concept id=\"b\">\n  <title>B</title>\n"
                + "  <conbody><p>x</p></conbody>\n</concept>\n");

        // "Every topic" is reasonably pointed at a folder where not every topic has a conbody: the ones that
        // do are changed and the rest are left, without failing the run.
        var plan = run("//conbody", "outputclass", "wide", false, false);

        assertThat(plan.toChange()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("b.dita"))).contains("outputclass=\"wide\"");
    }

    @Test
    void aSelectorNoFileCanSatisfyIsReportedRatherThanCountedAsDone() throws Exception {
        topic("a.dita", "<concept id=\"a\">");
        topic("b.dita", "<concept id=\"b\">");

        // Nothing matched anywhere. That is a selector that does not work — a typo, or an XPath matching two
        // elements — and answering "0 changed, all fine" would be answering a question nobody asked.
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(CommandException.class,
                () -> executor.execute(new SetAttributeCommand(
                        dir.toString(), null, "//conbody", "outputclass", "wide", false, true))))
                .hasMessageContaining("No file could be changed")
                .hasMessageContaining("matches 0 elements");
    }

    @Test
    void aFileKeepsTheEncodingItDeclares() throws Exception {
        // Read as UTF-8, this file either refuses to read or comes back with the wrong characters — and
        // writing it back would put UTF-8 bytes in a file that says it is Latin-1.
        Files.write(dir.resolve("latin.dita"),
                ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<concept id=\"l\">\n"
                        + "  <title>caf\u00e9</title>\n</concept>\n")
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        var plan = run("/*", "xml:lang", "fr", false, false);

        assertThat(plan.toChange()).isEqualTo(1);
        String written = Files.readString(dir.resolve("latin.dita"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(written).contains("xml:lang=\"fr\"").contains("caf\u00e9");
    }

    @Test
    void withNoScopeItKeepsToDitaDocuments() throws Exception {
        topic("a.dita", "<concept id=\"a\">");
        Files.writeString(dir.resolve("pom.xml"), "<?xml version=\"1.0\"?>\n<project><name>x</name></project>\n");
        String pom = Files.readString(dir.resolve("pom.xml"));

        // Unscoped, this walks the whole tree, and "every XML file" there includes a build file. Asked for a
        // scope we take it at its word; asked for nothing we take what the command is for.
        var plan = run("/*", "xml:lang", "en", false, false);

        assertThat(plan.toChange()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(pom);
    }

    @Test
    void anExplicitScopeIsTakenAtItsWord() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<?xml version=\"1.0\"?>\n<project><name>x</name></project>\n");

        var result = executor.execute(new SetAttributeCommand(
                dir.toString(), "glob:*.xml", "/*", "xml:lang", "en", false, false));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("pom.xml"))).contains("xml:lang=\"en\"");
    }

    @Test
    void anAttributeWithNoNameIsRefused() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(CommandException.class,
                () -> executor.execute(new SetAttributeCommand(dir.toString(), null, "/*", "  ", "en", false, true))))
                .hasMessageContaining("No attribute named");
    }
}
