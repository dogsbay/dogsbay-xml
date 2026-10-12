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

package com.dogsbay.dogsbayaieditor.dita;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The local server that previews a built dogsbay site (site/, or astro/dist from an older build). */
class SitePreviewTest {

    @TempDir Path dir;

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        SitePreview.stopAll();
    }

    /** A built site as the Astro build left it, before dogsbay-ssg: output/astro/dist. */
    private Path builtSite() throws Exception {
        Path dist = dir.resolve("out/astro/dist");
        Files.createDirectories(dist.resolve("_astro"));
        Files.createDirectories(dist.resolve("topics/intro"));
        Files.createDirectories(dist.resolve("pagefind"));
        Files.writeString(dist.resolve("index.html"), "<html><link rel=\"stylesheet\" href=\"/_astro/site.css\">home</html>");
        Files.writeString(dist.resolve("topics/intro/index.html"), "<html>intro</html>");
        Files.writeString(dist.resolve("_astro/site.css"), "body { color: red }");
        Files.write(dist.resolve("pagefind/wasm.en.pagefind"), new byte[] { 0, 97, 115, 109 });
        Files.writeString(dist.resolve("pagefind/pagefind.js"), "export {}");
        Files.writeString(dist.resolve("404.html"), "<html>not here</html>");
        Files.writeString(dir.resolve("out/secret.txt"), "outside the site");
        return dist;
    }

    private HttpResponse<String> get(URI base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void findsTheBuiltSiteOfADogsbaySiteOutputOnly() throws Exception {
        Path dist = builtSite();
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile())).isEqualTo(dist.toFile());
        assertThat(SitePreview.siteRoot(dist.toFile())).isEqualTo(dist.toFile());
        Files.createDirectories(dir.resolve("project/content"));
        assertThat(SitePreview.siteRoot(dir.resolve("project").toFile())).as("a dogsbay site project, not built").isNull();
        assertThat(SitePreview.siteRoot(null)).isNull();
    }

    /**
     * A built site as dogsbay-ssg leaves it: output/site, assets in _dogsbay, and a page
     * written as topics/intro.html rather than the folder the Astro build used. A section
     * that has pages under it still gets a folder, so guide.html sits beside guide/.
     */
    private Path ssgSite() throws Exception {
        Path site = dir.resolve("out/site");
        Files.createDirectories(site.resolve("_dogsbay"));
        Files.createDirectories(site.resolve("topics"));
        Files.createDirectories(site.resolve("guide"));
        Files.writeString(site.resolve("index.html"), "<html><link rel=\"stylesheet\" href=\"/_dogsbay/site.css\">home</html>");
        Files.writeString(site.resolve("topics/intro.html"), "<html>intro</html>");
        Files.writeString(site.resolve("guide.html"), "<html>guide</html>");
        Files.writeString(site.resolve("guide/setup.html"), "<html>setup</html>");
        Files.writeString(site.resolve("_dogsbay/site.css"), "body { color: red }");
        Files.writeString(site.resolve("404.html"), "<html>not here</html>");
        return site;
    }

    /**
     * The flat layout over HTTP: a page is a file, so an extensionless request has to find
     * <path>.html, and that must still work when a folder of the same name sits beside it.
     */
    @Test
    void servesTheFlatPagesAndAssetsOfASsgSite() throws Exception {
        URI base = SitePreview.serve(ssgSite().toFile());
        assertThat(get(base, "/topics/intro.html").body()).contains("intro");
        assertThat(get(base, "/topics/intro").body()).as("extensionless").contains("intro");
        assertThat(get(base, "/guide/setup").body()).contains("setup");
        assertThat(get(base, "/_dogsbay/site.css").body()).contains("color: red");
        HttpResponse<String> shadowed = get(base, "/guide");
        assertThat(shadowed.statusCode()).as("a page with a folder of the same name beside it").isEqualTo(200);
        assertThat(shadowed.body()).contains("guide");
    }

    @Test
    void findsTheSiteDogsbaySsgWrote() throws Exception {
        Path site = ssgSite();
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile())).isEqualTo(site.toFile());
        assertThat(SitePreview.siteRoot(site.toFile())).as("the site folder itself").isEqualTo(site.toFile());
    }

    /**
     * An output folder can hold both: the Astro build this replaced left astro/dist
     * behind, and a rebuild writes site/ beside it without clearing it. The preview
     * must open what was just built, not the stale folder next to it.
     */
    @Test
    void prefersTheSsgSiteOverAnAstroBuildLeftFromBefore() throws Exception {
        builtSite();
        Path site = ssgSite();
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile())).isEqualTo(site.toFile());
    }

    @Test
    void doesNotOfferASiteFolderThatIsNotBuilt() throws Exception {
        Files.createDirectories(dir.resolve("out/site/_dogsbay"));
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile())).as("no index.html").isNull();
    }

    /**
     * An index.html alone is not a site: an html5 publish of a map with a site/index.dita
     * in it writes site/index.html, and offering that as a site preview would serve a
     * slice of someone else's output as if it were a built site.
     */
    @Test
    void doesNotMistakeASiteFolderOfHtmlPagesForABuiltSite() throws Exception {
        Files.createDirectories(dir.resolve("out/site"));
        Files.writeString(dir.resolve("out/site/index.html"), "<html>an html5 page</html>");
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile())).as("no assets folder").isNull();
    }

    @Test
    void servesPagesAsFoldersAndAssetsFromTheRootOnTheLoopbackAddress() throws Exception {
        URI base = SitePreview.serve(builtSite().toFile());
        assertThat(base.getHost()).isEqualTo("127.0.0.1");

        HttpResponse<String> home = get(base, "/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("home");
        assertThat(home.headers().firstValue("Content-Type")).contains("text/html; charset=utf-8");

        assertThat(get(base, "/topics/intro").body()).contains("intro");
        assertThat(get(base, "/topics/intro/").body()).contains("intro");

        HttpResponse<String> css = get(base, "/_astro/site.css");
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("Content-Type")).contains("text/css; charset=utf-8");
        assertThat(get(base, "/pagefind/pagefind.js").headers().firstValue("Content-Type")).contains("text/javascript; charset=utf-8");
    }

    @Test
    void answersMissingPagesWithTheSites404AndNeverServesFilesOutsideIt() throws Exception {
        URI base = SitePreview.serve(builtSite().toFile());

        HttpResponse<String> missing = get(base, "/no/such/page");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("not here");

        HttpResponse<String> escape = client.send(HttpRequest.newBuilder(URI.create(base + "..%2F..%2Fsecret.txt")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(escape.statusCode()).isEqualTo(404);
        assertThat(escape.body()).doesNotContain("outside the site");
        SitePreview.Server server = SitePreview.Server.start(dir.resolve("out/astro/dist"));
        try {
            assertThat(server.resolve("/../secret.txt")).isNull();
            assertThat(server.resolve("/../../out/secret.txt")).isNull();
        } finally {
            server.stop();
        }
    }

    @Test
    void reusesTheServerForTheSameSiteAndRejectsWrites() throws Exception {
        Path dist = builtSite();
        URI first = SitePreview.serve(dist.toFile());
        assertThat(SitePreview.serve(dist.resolve(".").toFile())).isEqualTo(first);
        HttpResponse<String> post = client.send(HttpRequest.newBuilder(first).POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(405);
    }

    @Test
    void namesContentTypesForSiteFiles() {
        assertThat(SitePreview.contentType(Path.of("a.woff2"))).isEqualTo("font/woff2");
        assertThat(SitePreview.contentType(Path.of("a.WASM"))).isEqualTo("application/wasm");
        assertThat(SitePreview.contentType(Path.of("llms.txt"))).isEqualTo("text/plain; charset=utf-8");
        assertThat(SitePreview.contentType(Path.of("index.pf_meta"))).isEqualTo("application/octet-stream");
    }
}
