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

package com.dogsbay.dogsbayaieditor.dita.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.Param;
import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;
import com.dogsbay.xml.dita.NodeLocator;

/** A dogsbay deliverable's live preview. */
class LivePreviewTest {

    @TempDir Path dir;

    private static Deliverable deliverable(String transtype, List<Param> params) {
        return new Deliverable("site", Path.of("guide.ditamap"), List.of(), transtype, Path.of("out/site"), params, null, "site");
    }

    @Test
    void previewsOnlyDogsbayDeliverables() {
        assertThat(LivePreview.canPreview(deliverable("dogsbay-site", List.of()))).isTrue();
        assertThat(LivePreview.canPreview(deliverable("dogsbay", List.of()))).isTrue();
        assertThat(LivePreview.canPreview(deliverable("html5", List.of()))).isFalse();
        assertThat(LivePreview.canPreview(null)).isFalse();
    }

    /**
     * The ready line is the one string both repos must agree on, and a mismatch is silent: the
     * preview would sit in "Starting" with no address and no error. dogsbay-ssg pins the same
     * format with {@code ^DOGSBAY_DEV_READY http://127\.0\.0\.1:\d+/$} (its {@code 6d5e8ba}).
     */
    @Test
    void readsTheAddressFromTheDevSessionsReadyLine() {
        assertThat(LivePreview.localUrl("DOGSBAY_DEV_READY http://127.0.0.1:43127/"))
                .isEqualTo("http://127.0.0.1:43127/");
        String esc = String.valueOf((char) 27);
        assertThat(LivePreview.localUrl(esc + "[2mDOGSBAY_DEV_READY " + esc + "[36mhttp://127.0.0.1:4322/" + esc + "[39m"))
                .isEqualTo("http://127.0.0.1:4322/");
        // A session bound elsewhere, or to IPv6, still reports its address
        assertThat(LivePreview.localUrl("DOGSBAY_DEV_READY http://[::1]:4400/")).isEqualTo("http://[::1]:4400/");
        // The marker is matched exactly, so prose that merely mentions an address is not an address
        assertThat(LivePreview.localUrl("dev server for /tmp/site at http://127.0.0.1:4400/ (ready in 812 ms)")).isNull();
        assertThat(LivePreview.localUrl("┃ Local    http://localhost:4321/")).isNull();
        assertThat(LivePreview.localUrl("DOGSBAY_DEV_READY")).isNull();
    }

    /**
     * The dev session stops when its stdin closes, so it never outlives the editor. That is only
     * correct while the child gets its own pipe: an inherited stdin is an immediate EOF in an
     * editor launched from the desktop, and under {@code live-preview start}, whose detached JVM
     * takes stdin from the null device — every preview would die at start-up.
     */
    @Test
    void givesTheDevSessionItsOwnPipedStdin() {
        ProcessBuilder pb = LivePreview.process(List.of("node", "dev.mjs"), dir.toFile());
        assertThat(pb.redirectInput()).as("an inherited stdin is an immediate EOF, which would stop the session at once")
                .isEqualTo(ProcessBuilder.Redirect.PIPE);
        assertThat(pb.directory()).isEqualTo(dir.toFile());
        assertThat(pb.redirectErrorStream()).as("the session says on stderr why it stopped").isTrue();
        assertThat(pb.environment()).containsEntry("FORCE_COLOR", "0");
    }

    @Test
    void aDeliverableNamingItsOwnCliStillRunsItAsTheConverter() {
        assertThat(LivePreview.namesItsCli(deliverable("dogsbay-site",
                List.of(new Param("dogsbay.cli", "/opt/dogsbay/packages/cli/bin/cli.js", Param.Kind.VALUE))))).isTrue();
        assertThat(LivePreview.namesItsCli(deliverable("dogsbay", List.of()))).isFalse();
    }

    @Test
    void sayingReadyMeansThePagesAreTrustworthy() {
        // A publish with errors still serves a site, and a caller that read READY as success reported
        // a failed build as a working preview (2026-09-17).
        assertThat(LivePreview.published("http://localhost:4321/", 0)).isEqualTo(LivePreview.State.READY);
        assertThat(LivePreview.published("http://localhost:4321/", 88)).isEqualTo(LivePreview.State.READY_WITH_ERRORS);
        assertThat(LivePreview.published(null, 0)).isEqualTo(LivePreview.State.STARTING);
    }

    @Test
    void nothingBringsAStoppedPreviewBack() {
        // A stop during the first publish, which takes seconds, was overwritten when the publish
        // finished, and a dev server started that nothing could stop any more (review, 2026-09-18).
        LivePreview preview = new LivePreview(dir, deliverable("dogsbay-site", List.of()), null);
        preview.stop();
        assertThat(preview.moveTo(LivePreview.State.PUBLISHING, "Publishing")).isFalse();
        assertThat(preview.moveTo(LivePreview.State.READY, "Ready")).isFalse();
        assertThat(preview.state()).isEqualTo(LivePreview.State.STOPPED);
        preview.republish(); // after stop: neither runs nor throws
        assertThat(preview.state()).isEqualTo(LivePreview.State.STOPPED);
    }

    /** A project with two site deliverables, as for comparing a macOS and a Windows guide side by side. */
    private Path twoDeliverableProject() throws Exception {
        Path project = dir.resolve("audacity");
        Files.createDirectories(project.resolve("topics"));
        Files.writeString(project.resolve("guide.ditamap"), "<map/>");
        Files.writeString(project.resolve("project.json"), """
                { "deliverables": [
                  { "name": "site-macos", "context": { "input": "guide.ditamap" }, "publication": { "transtype": "dogsbay-site" } },
                  { "name": "site-windows", "context": { "input": "guide.ditamap" }, "output": "previews/windows",
                    "publication": { "transtype": "dogsbay-site" } } ] }
                """);
        return project;
    }

    @Test
    void knowsWhereEveryDeliverableOfTheProjectPublishes() throws Exception {
        Path project = twoDeliverableProject();
        assertThat(LivePreview.projectOutputs(project)).containsExactlyInAnyOrder(
                project.resolve("out/site-macos"), project.resolve("previews/windows"));
        assertThat(LivePreview.projectOutputs(dir.resolve("no-project"))).isEmpty();
    }

    @Test
    void twoPreviewsOfOneProjectDoNotRepublishEachOther() throws Exception {
        // Each excluded only its own output, so a publish of one was a project change to the other,
        // and the pair republished each other every few seconds for as long as both ran (2026-09-19).
        Path project = twoDeliverableProject();
        java.util.List<Path> outputs = LivePreview.projectOutputs(project);
        Files.createDirectories(project.resolve("out/site-macos/content"));
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        try (ProjectWatcher watcher = new ProjectWatcher(project,
                p -> LivePreview.inAny(p.toAbsolutePath().normalize(), outputs), 200, changes::incrementAndGet)) {
            watcher.start();
            Files.writeString(project.resolve("out/site-macos/content/page.md"), "# macOS"); // the other preview publishing
            Files.createDirectories(project.resolve("previews/windows/content"));
            Files.writeString(project.resolve("previews/windows/content/page.md"), "# Windows");
            Thread.sleep(2000);
            assertThat(changes.get()).as("published output is not a source change").isZero();
            Files.writeString(project.resolve("topics/a.dita"), "<topic/>");
            Thread.sleep(2000);
            assertThat(changes.get()).as("a source change still republishes").isEqualTo(1);
        }
    }

    /**
     * End to end: publish a small project, start the dev session, change a topic, and see the
     * served page change. Needs only the bundled DITA-OT framework and Node.js now that the
     * dev session travels in the plugin — no dogsbay CLI, no package install and no network —
     * but it publishes a real project twice, so it stays behind DOGSBAY_LIVE_PREVIEW_TEST=1.
     */
    @Test
    void republishesATopicChangeIntoTheRunningSite() throws Exception {
        assumeTrue("1".equals(System.getenv("DOGSBAY_LIVE_PREVIEW_TEST")), "set DOGSBAY_LIVE_PREVIEW_TEST=1 to run");
        assumeTrue(DefaultDitaOtFramework.isBundled() && NodeLocator.system().find().isPresent());
        File home = DefaultDitaOtFramework.extractBundled(dir.resolve("engine").toFile());
        assumeTrue(com.dogsbay.xml.dita.DogsbayPublish.devSession(home.getPath()).isPresent(),
                "the bundled plugin carries no ssg/dev.mjs: run ./gradlew bundleDogsbaySsg bundleDitaOt");

        Path project = dir.resolve("project");
        Files.createDirectories(project.resolve("topics"));
        Files.writeString(project.resolve("guide.ditamap"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
                <map><title>Guide</title><topicref href="topics/intro.dita"/></map>
                """);
        Path topic = project.resolve("topics/intro.dita");
        Files.writeString(topic, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="intro"><title>Intro</title><body><p>First version.</p></body></topic>
                """);
        Files.writeString(project.resolve("project.json"), """
                { "deliverables": [ { "name": "site", "context": { "id": "site", "input": "guide.ditamap" },
                  "output": "out/site",
                  "publication": { "transtype": "dogsbay-site" } } ] }
                """);
        Deliverable site = new Deliverable("site", project.resolve("guide.ditamap"), List.of(), "dogsbay-site",
                project.resolve("out/site"), List.of(), project.resolve("project.json"), "site");

        LivePreview preview = new LivePreview(project, site, home.getPath());
        preview.addListener(new LivePreview.Listener() {
            @Override
            public void changed(LivePreview p) {
                System.out.println("[state] " + p.state() + " " + p.message());
            }

            @Override
            public void output(String line) {
                System.out.println("[dev] " + line);
            }
        });
        try {
            preview.start();
            long deadline = System.currentTimeMillis() + 240_000;
            while (preview.url() == null && preview.state() != LivePreview.State.FAILED && System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
            }
            assertThat(preview.state()).as(preview.message()).isEqualTo(LivePreview.State.READY);
            assertThat(preview.lastBuild().success()).as("a clean sample publishes without errors").isTrue();
            String page = preview.url().replaceAll("/$", "") + "/topics/intro";
            // HTTP/1.1: the client's default upgrade request to HTTP/2 is never answered
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(5)).build();
            assertThat(get(http, page)).contains("First version.");

            long changed = System.currentTimeMillis();
            Files.writeString(topic, Files.readString(topic).replace("First version.", "Second version."));
            String body = "";
            deadline = System.currentTimeMillis() + 90_000;
            while (!body.contains("Second version.") && System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
                body = get(http, page);
            }
            System.out.println("[timing] edit visible after " + (System.currentTimeMillis() - changed) + " ms");
            assertThat(body).contains("Second version.");
        } finally {
            preview.stop();
        }
        assertThat(preview.state()).isEqualTo(LivePreview.State.STOPPED);
    }

    /**
     * End to end, like {@link #republishesATopicChangeIntoTheRunningSite}: two previews of one project,
     * a macOS and a Windows guide, side by side. Each publish used to be a change to the other, so the
     * pair republished each other every few seconds for as long as both ran (2026-09-19).
     */
    @Test
    void twoPreviewsOfOneProjectSettle() throws Exception {
        assumeTrue("1".equals(System.getenv("DOGSBAY_LIVE_PREVIEW_TEST")), "set DOGSBAY_LIVE_PREVIEW_TEST=1 to run");
        assumeTrue(DefaultDitaOtFramework.isBundled() && NodeLocator.system().find().isPresent());
        File home = DefaultDitaOtFramework.extractBundled(dir.resolve("engine").toFile());
        assumeTrue(com.dogsbay.xml.dita.DogsbayPublish.devSession(home.getPath()).isPresent(),
                "the bundled plugin carries no ssg/dev.mjs: run ./gradlew bundleDogsbaySsg bundleDitaOt");

        Path project = dir.resolve("audacity");
        Files.createDirectories(project.resolve("topics"));
        Files.writeString(project.resolve("guide.ditamap"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
                <map><title>Guide</title><topicref href="topics/intro.dita"/></map>
                """);
        Files.writeString(project.resolve("topics/intro.dita"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="intro"><title>Intro</title><body><p platform="mac">Mac.</p><p platform="windows">Windows.</p></body></topic>
                """);
        Files.writeString(project.resolve("mac.ditaval"), "<val><prop att=\"platform\" val=\"windows\" action=\"exclude\"/></val>");
        Files.writeString(project.resolve("windows.ditaval"), "<val><prop att=\"platform\" val=\"mac\" action=\"exclude\"/></val>");
        java.util.List<Param> params = List.of();
        Deliverable mac = new Deliverable("site-macos", project.resolve("guide.ditamap"), List.of(project.resolve("mac.ditaval")),
                "dogsbay-site", null, params, null, "site-macos");
        Deliverable windows = new Deliverable("site-windows", project.resolve("guide.ditamap"),
                List.of(project.resolve("windows.ditaval")), "dogsbay-site", null, params, null, "site-windows");
        Files.writeString(project.resolve("project.json"), """
                { "deliverables": [
                  { "name": "site-macos", "context": { "input": "guide.ditamap", "profiles": { "ditavals": ["mac.ditaval"] } },
                    "publication": { "transtype": "dogsbay-site" } },
                  { "name": "site-windows", "context": { "input": "guide.ditamap", "profiles": { "ditavals": ["windows.ditaval"] } },
                    "publication": { "transtype": "dogsbay-site" } } ] }
                """);

        java.util.concurrent.atomic.AtomicInteger publishes = new java.util.concurrent.atomic.AtomicInteger();
        LivePreview.Listener counting = new LivePreview.Listener() {
            @Override
            public void changed(LivePreview p) {
                if (p.state() == LivePreview.State.PUBLISHING) {
                    publishes.incrementAndGet();
                    System.out.println("[publish] " + p.deliverable().name());
                }
            }

            @Override
            public void output(String line) {
            }
        };
        LivePreview first = new LivePreview(project, mac, home.getPath());
        LivePreview second = new LivePreview(project, windows, home.getPath());
        first.addListener(counting);
        second.addListener(counting);
        try {
            first.start();
            second.start();
            long deadline = System.currentTimeMillis() + 240_000;
            while ((first.url() == null || second.url() == null) && System.currentTimeMillis() < deadline
                    && first.state() != LivePreview.State.FAILED && second.state() != LivePreview.State.FAILED) {
                Thread.sleep(500);
            }
            assertThat(first.url()).as("macOS: " + first.state() + " " + first.message() + " "
                    + (first.lastBuild() == null ? "" : first.lastBuild().messages())).isNotNull();
            assertThat(second.url()).as("Windows: " + second.state() + " " + second.message()).isNotNull();
            Thread.sleep(3000); // let any publish the other's startup caused run and finish
            int settled = publishes.get();
            Thread.sleep(20_000);
            assertThat(publishes.get()).as("publishes while nothing changed").isEqualTo(settled);

            Files.writeString(project.resolve("topics/intro.dita"),
                    Files.readString(project.resolve("topics/intro.dita")).replace("Mac.", "Mac edited."));
            Thread.sleep(15_000);
            assertThat(publishes.get() - settled).as("one source change republishes each preview once").isEqualTo(2);
        } finally {
            first.stop();
            second.stop();
        }
    }

    /** A page's body. Vite may listen on ::1 only, while Java's client tries 127.0.0.1 for localhost; browsers try both. */
    private static String get(HttpClient http, String url) throws Exception {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString()).body();
        } catch (java.net.ConnectException e) {
            String ipv6 = url.replace("//localhost:", "//[::1]:");
            return http.send(HttpRequest.newBuilder(URI.create(ipv6)).timeout(java.time.Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString()).body();
        }
    }
}
