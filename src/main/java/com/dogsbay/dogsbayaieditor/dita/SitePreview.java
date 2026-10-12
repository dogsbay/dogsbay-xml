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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Previews a built dogsbay site (the {@code dogsbay-site} transtype's
 * {@code site}) in the browser.
 *
 * <p>The built site is served rather than opened as files: search loads its index
 * over HTTP, and a site built by Astro wrote root-relative asset paths
 * ({@code /_astro/site.css}) and pages as folders ({@code /topics/intro} is
 * {@code topics/intro/index.html}). dogsbay-ssg writes a page as
 * {@code topics/intro.html} instead, which {@link Server#resolve} also handles. So the
 * editor serves the folder itself, from a small static server on the loopback
 * address and a free port, with no Node.js needed. One server runs per site folder
 * and stays up until the editor exits, so opening the preview again reuses it.
 */
public final class SitePreview {

    private static final Logger LOG = LoggerFactory.getLogger(SitePreview.class);

    private static final Map<Path, Server> SERVERS = new LinkedHashMap<>();

    private SitePreview() {}

    /**
     * The built site in a publish output folder: {@code <output>/site} (dogsbay-ssg),
     * {@code <output>/astro/dist} (the Astro builds this replaced), or the folder
     * itself when it is a built site; null otherwise (the {@code dogsbay} transtype
     * writes a site project, not a built site).
     *
     * <p>All three are still recognised, because an output folder on disk outlives
     * the builder that wrote it: a site published before the switch to dogsbay-ssg
     * should still preview rather than report that there is nothing to show.
     */
    public static File siteRoot(File outputDir) {
        if (outputDir == null) {
            return null;
        }
        // The folder itself first: a built site can contain a "site" folder of its own (a
        // section's pages), and descending into it would root the preview inside one
        // subsection, where every "/_dogsbay/..." asset link then misses.
        if (isBuiltSite(outputDir)) {
            return outputDir;
        }
        File ssg = new File(outputDir, "site");
        if (isBuiltSite(ssg)) {
            return ssg;
        }
        File dist = new File(outputDir, "astro/dist");
        if (new File(dist, "index.html").isFile()) {
            return dist;
        }
        return null;
    }

    /**
     * Whether a folder is a built site: a home page and the builder's own assets folder.
     *
     * <p>The assets folder is what makes this a site rather than any folder with an
     * {@code index.html} in it — and dogsbay-ssg writes {@code _dogsbay} first, before any
     * page, precisely as that marker. Without it an {@code html5} publish of a map that
     * happens to have a {@code site/index.dita} would be offered as a site preview.
     */
    private static boolean isBuiltSite(File dir) {
        return new File(dir, "index.html").isFile()
                && (new File(dir, "_dogsbay").isDirectory() || new File(dir, "_astro").isDirectory());
    }

    /**
     * The built site in a publish output folder, only when this build wrote it: its
     * {@code index.html} was modified at or after {@code builtSince}. A build with errors
     * can still write the site; a stale site from an earlier build is not offered.
     */
    public static File siteRoot(File outputDir, long builtSince) {
        File root = siteRoot(outputDir);
        return root != null && new File(root, "index.html").lastModified() >= builtSince - BuildOutput.CLOCK_SLACK_MILLIS
                ? root : null;
    }

    /**
     * Serve the built site in {@code siteRoot} (starting a server the first time) and
     * return its address.
     */
    public static synchronized URI serve(File siteRoot) throws IOException {
        Path root = siteRoot.toPath().toAbsolutePath().normalize();
        Server server = SERVERS.get(root);
        if (server == null) {
            server = Server.start(root);
            SERVERS.put(root, server);
            LOG.info("Site preview of {} at {}", root, server.uri());
        }
        return server.uri();
    }

    /** Serve the built site and open it in the system browser. */
    public static URI open(File siteRoot) throws IOException {
        URI uri = serve(siteRoot);
        if (java.awt.Desktop.isDesktopSupported()
                && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(uri);
        } else {
            throw new IOException("No browser to open " + uri + "; open it by hand.");
        }
        return uri;
    }

    /** Stop every preview server (tests, and editor shutdown). */
    public static synchronized void stopAll() {
        SERVERS.values().forEach(Server::stop);
        SERVERS.clear();
    }

    /** A static file server for one site folder. */
    static final class Server {

        private final Path root;
        private final HttpServer http;
        private final ExecutorService executor;

        private Server(Path root, HttpServer http, ExecutorService executor) {
            this.root = root;
            this.http = http;
            this.executor = executor;
        }

        static Server start(Path root) throws IOException {
            HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "site-preview");
                t.setDaemon(true);
                return t;
            });
            Server server = new Server(root, http, executor);
            http.createContext("/", server::handle);
            http.setExecutor(executor);
            http.start();
            return server;
        }

        URI uri() {
            return URI.create("http://" + http.getAddress().getAddress().getHostAddress() + ":"
                    + http.getAddress().getPort() + "/");
        }

        void stop() {
            http.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String method = exchange.getRequestMethod();
                if (!method.equals("GET") && !method.equals("HEAD")) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                Path file = resolve(exchange.getRequestURI().getPath());
                int status = 200;
                if (file == null) {
                    status = 404;
                    Path notFound = root.resolve("404.html");
                    file = Files.isRegularFile(notFound) ? notFound : null;
                }
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                if (file == null) {
                    byte[] body = "Not found".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(status, method.equals("HEAD") ? -1 : body.length);
                    if (!method.equals("HEAD")) {
                        exchange.getResponseBody().write(body);
                    }
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", contentType(file));
                long size = Files.size(file);
                if (method.equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Long.toString(size));
                    exchange.sendResponseHeaders(status, -1);
                    return;
                }
                exchange.sendResponseHeaders(status, size == 0 ? -1 : size);
                if (size > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        Files.copy(file, out);
                    }
                }
            }
        }

        /**
         * The file a request path serves, as a static host serves a built site: the file
         * itself, a folder's {@code index.html}, or {@code <path>.html}; null when there is
         * none or the path leaves the site folder.
         *
         * <p>A folder does not end the search. dogsbay-ssg writes a page as
         * {@code guide.html} and that page's children under {@code guide/}, so for
         * {@code /guide} both exist and only the sibling {@code .html} is the page.
         */
        Path resolve(String requestPath) {
            String path = requestPath == null ? "/" : requestPath;
            Path target = root.resolve(path.replaceFirst("^/+", "")).normalize();
            if (!target.startsWith(root)) {
                return null;
            }
            if (Files.isDirectory(target)) {
                Path index = target.resolve("index.html");
                if (Files.isRegularFile(index)) {
                    return index;
                }
            } else if (Files.isRegularFile(target)) {
                return target;
            }
            Path html = target.resolveSibling(target.getFileName() + ".html");
            return html.startsWith(root) && Files.isRegularFile(html) ? html : null;
        }
    }

    /** The Content-Type for a site file, by extension. */
    static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "html", "htm" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "json", "map" -> "application/json; charset=utf-8";
            case "txt", "md" -> "text/plain; charset=utf-8";
            case "xml" -> "application/xml; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "wasm" -> "application/wasm";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
