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

package com.dogsbay.xml.dita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;

/**
 * The dogsbay publishing plugin in the editor's bundled DITA-OT: both transtypes are
 * registered, and a build runs the plugin's own converter with Node.js in the
 * editor's JVM (skipped when this machine has no Node.js).
 */
class DitaOtBuilderDogsbayTest {

    @TempDir static Path engine;
    private static File home;

    @TempDir Path dir;

    @BeforeAll
    static void extractEngine() throws Exception {
        assumeTrue(DefaultDitaOtFramework.isBundled(), "the bundled DITA-OT framework is not on the classpath");
        home = DefaultDitaOtFramework.extractBundled(engine.toFile());
    }

    @Test
    void theBundledDitaOtRegistersTheDogsbayTranstypesAndCarriesTheConverter() throws Exception {
        assertThat(home.toPath().resolve("plugins/com.dogsbay.publish/converter/publish-dita.mjs")).isRegularFile();
        Path ssg = home.toPath().resolve("plugins/com.dogsbay.publish/ssg");
        assertThat(ssg.resolve("build.mjs")).as("the site builder dogsbay-site runs").isRegularFile();
        // build.mjs code-splits: it loads chunks/ from beside itself and spawns the
        // highlight worker, so a bundle of build.mjs alone would fail at run time.
        assertThat(ssg.resolve("highlight-worker.mjs")).isRegularFile();
        assertThat(ssg.resolve("chunks")).isDirectory();
        assertThat(ssg.resolve("chunks").toFile().list()).isNotEmpty();
        assertThat(ssg.resolve("VERSION")).as("which dogsbay-ssg commit this bundle came from").isRegularFile();
        String plugins = Files.readString(home.toPath().resolve("config/plugins.xml"));
        assertThat(plugins).contains("name=\"dogsbay\"").contains("name=\"dogsbay-site\"");
        assertThat(org.dita.dost.util.Configuration.transtypes).as("the editor's in-process DITA-OT runs them")
                .contains("dogsbay", "dogsbay-site", "html5", "pdf");
    }

    @Test
    void publishesADogsbaySiteProjectInProcess() throws Exception {
        Optional<Path> node = NodeLocator.system().find();
        assumeTrue(node.isPresent(), "no Node.js on this machine");
        Path map = DitaOtBuilderKeepTempTest.writeProject(dir.resolve("project"));
        Path out = dir.resolve("site");

        DitaOtBuilder.Outcome outcome = new DitaOtBuilder(home.getPath()).buildTimed(map.toFile(), "dogsbay", List.of(),
                Map.of(), out.toFile(), dir.resolve("temp").toFile());
        List<DitaOtMessage> messages = outcome.messages();

        assertThat(messages).noneMatch(DitaOtMessage::isError);
        assertThat(outcome.steps()).extracting(BuildStep::name)
                .contains("Loading DITA-OT", "Initialization", "Preprocessing", "Converting to dogsbay Markdown");
        assertThat(outcome.steps().stream().filter(s -> s.name().equals("Preprocessing")).findFirst().orElseThrow().stages())
                .extracting(BuildStep::name).contains("map-reader", "topic-conref");
        assertThat(outcome.engine()).isEqualTo("DITA-OT 4.3.5 (Java)");
        assertThat(dir.resolve("temp").toFile().list()).as("the build's temporary files are deleted").isEmpty();
        String page = Files.readString(out.resolve("content/topics/t.md"));
        assertThat(page).contains("title: About Audacity").contains("Install Audacity.");
        assertThat(out.resolve("content/nav.yml")).isRegularFile();
        assertThat(out.resolve("dogsbay.config.yml")).isRegularFile();
    }

    /**
     * The whole dogsbay-site path, in the editor's JVM: preprocessing, the converter, and
     * the bundled dogsbay-ssg builder over what it wrote. No package install and no
     * network access, which is the point of the builder being bundled.
     */
    @Test
    void buildsTheSiteItselfWithTheBundledBuilder() throws Exception {
        Optional<Path> node = NodeLocator.system().find();
        assumeTrue(node.isPresent(), "no Node.js on this machine");
        Path map = DitaOtBuilderKeepTempTest.writeProject(dir.resolve("project"));
        Path out = dir.resolve("site");

        DitaOtBuilder.Outcome outcome = new DitaOtBuilder(home.getPath()).buildTimed(map.toFile(), "dogsbay-site",
                List.of(), Map.of(), out.toFile(), dir.resolve("temp").toFile());

        assertThat(outcome.messages()).noneMatch(DitaOtMessage::isError);
        assertThat(outcome.steps()).extracting(BuildStep::name)
                .contains("Converting to dogsbay Markdown", "Building the site");
        Path built = out.resolve("site");
        assertThat(built.resolve("index.html")).isRegularFile();
        assertThat(built.resolve("_dogsbay")).as("the site's own assets folder").isDirectory();
        // dogsbay-ssg writes a page as <path>.html; the Astro build wrote <path>/index.html
        assertThat(Files.readString(built.resolve("topics/t.html"))).contains("About Audacity");
        assertThat(com.dogsbay.dogsbayaieditor.dita.SitePreview.siteRoot(out.toFile()))
                .as("the preview opens what this build wrote").isEqualTo(built.toFile());
        assertThat(out.resolve("astro")).as("no Astro project is scaffolded any more").doesNotExist();
    }

    @Test
    void reportsAConverterThatCannotRunAsABuildError() throws Exception {
        Path map = DitaOtBuilderKeepTempTest.writeProject(dir.resolve("project"));

        List<DitaOtMessage> messages = new DitaOtBuilder(home.getPath()).build(map.toFile(), "dogsbay", List.of(),
                Map.of("dogsbay.node", dir.resolve("no-such-node").toString()), dir.resolve("site").toFile(),
                dir.resolve("temp").toFile());

        assertThat(messages).anyMatch(DitaOtMessage::isError);
    }
}
