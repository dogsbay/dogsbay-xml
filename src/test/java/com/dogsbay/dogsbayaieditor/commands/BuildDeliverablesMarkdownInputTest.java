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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;
import com.dogsbay.xml.dita.DitaOtMessage;
import com.dogsbay.xml.dita.NodeLocator;

/**
 * A project whose map references a Markdown topic publishes it.
 *
 * <p>This is {@code org.lwdita}, DITA-OT's {@code dita.parser} extension. It was in
 * {@code bundleDitaOt}'s {@code dropPlugins} from the first commit, so every Markdown
 * topic failed with {@code DOTJ013E ClassNotFoundException:
 * com.elovirta.dita.markdown.MarkdownReader} and a {@code DOTX031E} for each link to one —
 * 46 errors on dita-ot-docs, which has 14 of them. The converter was already written as
 * though they arrive ({@code INPUT_FORMATS} in dogsbay's {@code publish.ts}), so the two
 * halves disagreed.
 *
 * <p>The reader is instantiated by name from DITA-OT's own code, so what fixes it is the
 * jars on the editor's classpath, not the plugin folder: {@code bundleDitaOt} still drops
 * {@code org.lwdita}, and this passes against a framework zip with no trace of it. So the
 * test has to be a real publish — asserting the plugin folder exists would assert the
 * wrong thing, and would pass while Markdown stayed broken.
 *
 * <p>The same plugin registers {@code parser.hdita}, and that reader needs a jar Maven
 * cannot supply (see {@code lib/htmlparser-1.4.jar} in the build). A Maven version of
 * htmlparser compiles and then throws {@code NoClassDefFoundError} on the first
 * {@code .html} topic, so HDITA gets its own publish here rather than being assumed to
 * follow from Markdown working.
 */
class BuildDeliverablesMarkdownInputTest {

    @TempDir static Path engine;
    private static File home;

    /**
     * The project folder, made and removed here rather than by {@code @TempDir}.
     *
     * <p>Publishing the HDITA topic leaves its source file open: on Windows an open handle blocks
     * deletion, so {@code @TempDir}'s cleanup threw {@code Failed to delete temp directory …
     * topics\install.html} and failed a test whose own assertions had all passed. The leak is in
     * the reader, not here — see plans/known-issues.md — and a test of what the reader produces
     * should not be the thing that reports it.
     */
    private Path root;

    @BeforeEach
    void makeProjectFolder() throws Exception {
        root = Files.createTempDirectory("markdown-input");
    }

    @AfterEach
    void removeProjectFolder() throws Exception {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (java.io.IOException leftBehind) {
                    // an open handle on Windows; the runner's temp folder is thrown away anyway
                }
            });
        }
    }

    @BeforeAll
    static void extractEngine() throws Exception {
        assumeTrue(DefaultDitaOtFramework.isBundled(), "the bundled DITA-OT framework is not on the classpath");
        // The dogsbay transtype converts through Node, as DitaOtBuilderDogsbayTest also assumes.
        // Without it the build returns one FATAL and writes nothing, which would fail every
        // assertion below for a reason that has nothing to do with the readers under test.
        assumeTrue(NodeLocator.system().find().isPresent(), "no Node.js on this machine");
        home = DefaultDitaOtFramework.extractBundled(engine.toFile());
    }

    /** A map with a DITA topic, a Markdown topic and an HDITA topic beside each other. */
    private void writeProject() throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("guide.ditamap"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
                <map>
                  <title>Guide</title>
                  <topicref href="topics/intro.dita"/>
                  <topicref href="topics/notes.md" format="markdown"/>
                  <topicref href="topics/install.html" format="hdita"/>
                </map>
                """);
        Files.writeString(root.resolve("topics/intro.dita"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="intro"><title>Intro</title><body><p>From DITA.</p></body></topic>
                """);
        Files.writeString(root.resolve("topics/notes.md"), """
                # Release notes

                The first paragraph of the Markdown topic.
                """);
        Files.writeString(root.resolve("topics/install.html"), """
                <!DOCTYPE html>
                <html>
                  <body>
                    <article id="install">
                      <h1>Install notes</h1>
                      <p>The first paragraph of the HDITA topic.</p>
                    </article>
                  </body>
                </html>
                """);
        Files.writeString(root.resolve("project.json"), """
                { "deliverables": [ { "name": "site",
                    "context": { "id": "site", "input": "guide.ditamap" },
                    "output": "out/site",
                    "publication": { "transtype": "dogsbay" } } ] }
                """);
    }

    private DeliverableBuild build() throws Exception {
        List<DeliverableBuild> results = new HeadlessExecutor().execute(
                new BuildDeliverablesCommand(root.toString(), null, "site", home.getPath(), null, null));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    @Test
    void publishesAMarkdownTopicAlongsideADitaOne() throws Exception {
        writeProject();
        DeliverableBuild build = build();

        List<String> errors = build.messages().stream().filter(DitaOtMessage::isError)
                .map(DitaOtMessage::message).toList();
        assertThat(errors).as("a Markdown topic is not an error").isEmpty();
        assertThat(build.success()).as("the publish succeeded").isTrue();

        // The specific failure this guards: dropping org.lwdita again leaves the transtype
        // and the catalog in place, so the build still runs and still writes intro.md —
        // only the Markdown topic goes missing, with DOTJ013E naming the reader class.
        assertThat(build.messages().stream().map(DitaOtMessage::message))
                .noneMatch(m -> m.contains("MarkdownReader") || m.contains("ClassNotFoundException"));

        Path content = root.resolve("out/site/content/topics");
        assertThat(content.resolve("intro.md")).exists();
        assertThat(content.resolve("notes.md")).as("the Markdown topic publishes as a page").exists();

        // Read as DITA, not copied. The source text alone would not prove that — it appears
        // verbatim in the file either way. What only a round trip through a topic produces is
        // the front matter the converter writes from the parsed topic: a title lifted out of
        // the "# " heading, and an id the reader derived from it.
        String published = Files.readString(content.resolve("notes.md"));
        assertThat(published).startsWith("---");
        assertThat(published)
                .contains("title: Release notes")
                .contains("contentType: topic")
                .contains("id: release-notes")
                .contains("source: topics/notes.md");
        assertThat(published).contains("The first paragraph of the Markdown topic.");
    }

    /**
     * HDITA is the half of org.lwdita that needs htmlparser, and the version mismatch it
     * fails on is invisible until a publish runs: the editor compiles, the plugin loads, and
     * the reader throws on its own constructor.
     */
    @Test
    void publishesAnHditaTopicWithTheHtmlparserItsReaderWasBuiltAgainst() throws Exception {
        writeProject();
        DeliverableBuild build = build();

        assertThat(build.messages().stream().map(DitaOtMessage::message))
                .as("the HDITA reader found the classes it calls")
                .noneMatch(m -> m.contains("NoClassDefFoundError")
                        || m.contains("DoctypeExpectation")
                        || m.contains("HDitaReader"));
        assertThat(build.success()).as("the publish succeeded").isTrue();

        // install.html.md, not install.md: the converter appends its own extension to the
        // source name rather than replacing one, so an HDITA source keeps its .html. Checked
        // by asserting install.md and watching it fail.
        Path page = root.resolve("out/site/content/topics/install.html.md");
        assertThat(page).as("the HDITA topic publishes as a page").exists();
        assertThat(Files.readString(page))
                .as("read as a topic: the title comes from the <h1>")
                .contains("Install notes");
    }
}
