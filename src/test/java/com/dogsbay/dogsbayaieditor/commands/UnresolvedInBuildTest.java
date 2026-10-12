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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * Key references a deliverable's own build could not resolve.
 *
 * <p>The health report resolves keys against the project's root map, where a key is usually defined. A
 * deliverable builds a narrower scope, and a key missing from <em>that</em> scope is a link that vanishes from
 * that deliverable and no other — so health passes and the reader finds out by not finding the link. Three
 * defects reached a tutorial exactly this way. DITA-OT says so per deliverable, as an INFO note nobody saw.
 */
class UnresolvedInBuildTest {

    @TempDir Path project;

    private DeliverableBuild buildSaying(String name, String message) {
        return new DeliverableBuild(name, "html5", project.resolve("out").toString(), true,
                List.of(DitaOtMessage.parse(Level.INFO, message)));
    }

    /** The message DITA-OT really emits: an absolute file: URI, with line and column. */
    private String note(String code, String key, String file, int line) {
        return java.net.URI.create("file:" + project.resolve(file).toUri().getRawPath()) + ":" + line + ":12: ["
                + code + "][INFO] Unable to find key definition for key reference '" + key
                + "'. Using the @href attribute as fallback if it exists.";
    }

    private void topic(String name, String body) throws Exception {
        Path file = project.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "<?xml version=\"1.0\"?>\n<concept id=\"t\">\n  <title>T</title>\n  <conbody>\n"
                + body + "  </conbody>\n</concept>\n");
    }

    @Test
    void aKeyMissingFromThisDeliverablesScopeIsReported() throws Exception {
        // In a subfolder, which is where topics live. DITA-OT names the file as an absolute file: URI, and
        // reading that as a plain path loses every topic that is not in the project's top folder.
        topic("topics/exporting.dita", "    <p>See <xref keyref=\"formats/choosing\">formats</xref>.</p>\n");

        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("install-variants",
                note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5))), project);

        assertThat(found).singleElement().satisfies(ref -> {
            assertThat(ref.key()).isEqualTo("formats/choosing");
            assertThat(ref.deliverable()).isEqualTo("install-variants");
            assertThat(ref.file()).isEqualTo("topics" + java.io.File.separator + "exporting.dita");
            assertThat(ref.line()).isEqualTo(5);
        });
    }

    @Test
    void theScopedTwinOfTheNoteCountsToo() throws Exception {
        // Inside a named @keyscope DITA-OT says DOTJ048I instead, with the same meaning and the same fallback
        // sentence. Taking only DOTJ047I would miss every scoped deliverable — one of the three defects.
        topic("topics/intro.dita", "    <p>See <xref keyref=\"glossary/term\">term</xref>.</p>\n");

        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("collection",
                note("DOTJ048I", "glossary/term", "topics/intro.dita", 5))), project);

        assertThat(found).singleElement().satisfies(ref -> assertThat(ref.key()).isEqualTo("glossary/term"));
    }

    @Test
    void anExternalHrefCountsAsAFallback() throws Exception {
        topic("topics/pubs.dita",
                "    <p><xref keyref=\"pubs/site\" href=\"https://example.org\" scope=\"external\">site</xref></p>\n");

        // The reference index does not record an external href — rightly, it is not a project file — so the
        // line's own text has to be read, or a link that works is reported as missing.
        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("web",
                note("DOTJ047I", "pubs/site", "topics/pubs.dita", 5))), project);

        assertThat(found).isEmpty();
    }

    @Test
    void aNoteWithNoLineIsNotGuessedAt() throws Exception {
        topic("topics/intro.dita", "    <p>See <xref keyref=\"a/b\">term</xref>.</p>\n");

        // No line means no way to ask whether the element has a fallback, and saying "there is none" would be
        // asserting what was never checked.
        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("web",
                "[DOTJ047I] Unable to find key definition for key reference 'a/b'.")), project);

        assertThat(found).isEmpty();
    }

    @Test
    void aBuildThatFailedIsNotReadForThese() throws Exception {
        topic("topics/exporting.dita", "    <p>See <xref keyref=\"formats/choosing\">formats</xref>.</p>\n");
        var failed = new DeliverableBuild("install-variants", "html5", "/out", false,
                List.of(DitaOtMessage.parse(Level.INFO,
                        note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5))));

        // A build that stopped may never have reached the key stage, so neither the presence nor the absence
        // of these notes says anything about it.
        assertThat(HeadlessExecutor.unresolvedKeyReferences(List.of(failed), project)).isEmpty();
    }

    @Test
    void anHrefFallbackIsNotReported() throws Exception {
        topic("topics/exporting.dita",
                "    <p>See <xref keyref=\"formats/choosing\" href=\"formats.dita\">formats</xref>.</p>\n");

        // The link still works: DITA falls back to the href when the key resolves to nothing. This is the test
        // that keeps the warning from firing on every deliverable that deliberately narrows its scope.
        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("install-variants",
                note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5))), project);

        assertThat(found).isEmpty();
    }

    @Test
    void eachDeliverableSpeaksForItself() throws Exception {
        topic("topics/exporting.dita", "    <p>See <xref keyref=\"formats/choosing\">formats</xref>.</p>\n");
        String said = note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5);

        var found = HeadlessExecutor.unresolvedKeyReferences(
                List.of(buildSaying("install-variants", said), buildSaying("book", said)), project);

        // The same reference, missing from two scopes, is two findings: a reader fixes them per deliverable.
        assertThat(found).hasSize(2);
        assertThat(found).extracting(r -> r.deliverable())
                .containsExactlyInAnyOrder("install-variants", "book");
    }

    @Test
    void theSameNoteTwiceInOneBuildIsOneFinding() throws Exception {
        topic("topics/exporting.dita", "    <p>See <xref keyref=\"formats/choosing\">formats</xref>.</p>\n");
        String said = note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5);

        var found = HeadlessExecutor.unresolvedKeyReferences(
                List.of(new DeliverableBuild("install-variants", "html5", "/out", true,
                        List.of(DitaOtMessage.parse(Level.INFO, said),
                                DitaOtMessage.parse(Level.INFO, said)))), project);

        assertThat(found).hasSize(1);
    }

    @Test
    void aMessageWithNoCodeAtAllIsSurvivable() throws Exception {
        // Set.of(...).contains(null) throws rather than answering false, and a message with no code is
        // ordinary: every FOP layout warning in a PDF build has none. This crashed `check` on any project
        // with a PDF deliverable (reported 2026-09-29).
        topic("topics/exporting.dita", "    <p>Text.</p>\n");
        var build = new DeliverableBuild("book-pdf", "pdf", "/out", true,
                List.of(DitaOtMessage.parse(Level.WARN,
                        "Content overflows the available area in block-container")));

        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(build), project);

        assertThat(found).isEmpty();
    }

    @Test
    void aCodedNoteIsStillFoundAmongUncodedOnes() throws Exception {
        topic("topics/exporting.dita", "    <p>See <xref keyref=\"formats/choosing\">formats</xref>.</p>\n");
        var build = new DeliverableBuild("install-variants", "html5", "/out", true,
                List.of(DitaOtMessage.parse(Level.WARN, "a warning with no code"),
                        DitaOtMessage.parse(Level.INFO,
                                note("DOTJ047I", "formats/choosing", "topics/exporting.dita", 5)),
                        DitaOtMessage.parse(Level.WARN, "another with no code")));

        // The uncoded ones must be stepped over, not stopped at.
        assertThat(HeadlessExecutor.unresolvedKeyReferences(List.of(build), project))
                .singleElement().satisfies(ref -> assertThat(ref.key()).isEqualTo("formats/choosing"));
    }

    @Test
    void otherNotesAreLeftAlone() throws Exception {
        topic("topics/exporting.dita", "    <p>Text.</p>\n");

        var found = HeadlessExecutor.unresolvedKeyReferences(List.of(buildSaying("install-variants",
                "[DOTJ031I] No rule for 'audience=podcaster' was found in the DITAVAL file. "
                        + "topics/exporting.dita:5")), project);

        assertThat(found).isEmpty();
    }
}
