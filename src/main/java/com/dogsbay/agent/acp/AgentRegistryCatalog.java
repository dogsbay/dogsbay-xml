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
package com.dogsbay.agent.acp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The ACP agent registry: which agents exist, how to launch them, and
 * which of them this machine can run. Fetched from the CDN and cached under
 * the user's home; a bundled snapshot means it works offline and on first
 * run.
 */
public final class AgentRegistryCatalog {

    public static final URI REGISTRY_URL =
            URI.create("https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json");
    static final String BUNDLED = "/com/dogsbay/agent/acp/registry.json";
    static final Duration REFRESH = Duration.ofDays(1);

    /** One registry entry, reduced to what launching needs. */
    public record Entry(String id, String name, String version, String description, String license,
            List<String> authors, Distribution distribution) {

        /** True when the runner this entry needs is on the PATH. */
        public boolean isInstalled() {
            return distribution != null && distribution.runnerOnPath();
        }

        public List<String> launchCommand() {
            return distribution == null ? List.of() : distribution.command();
        }

        /** Environment the registry says the agent needs (auto-update switches, model pins). */
        public Map<String, String> launchEnv() {
            return distribution == null ? Map.of() : distribution.env();
        }

        /** How close this agent is to running here. */
        public Readiness readiness() {
            if (distribution == null || !distribution.runnerOnPath()) {
                return Readiness.UNAVAILABLE;
            }
            return distribution.alreadyPresent() ? Readiness.READY : Readiness.DOWNLOAD;
        }
    }

    public enum Readiness {
        /** The agent's own tool is on this machine: starts at once, likely already signed in. */
        READY,
        /** Launchable, but the first start downloads the adapter through npx or uvx. */
        DOWNLOAD,
        /** Nothing here can run it (no npx/uvx, or a native binary not on the PATH). */
        UNAVAILABLE
    }

    /** How an entry is run: an npx or uvx package, or a native binary. */
    public sealed interface Distribution {
        List<String> command();

        boolean runnerOnPath();

        default Map<String, String> env() {
            return Map.of();
        }

        /** True when starting needs no download: the tool or its adapter is already here. */
        default boolean alreadyPresent() {
            return false;
        }

        record Npx(String pkg, List<String> args, Map<String, String> env) implements Distribution {
            @Override
            public List<String> command() {
                List<String> c = new ArrayList<>(List.of("npx", "-y", pkg));
                c.addAll(args);
                return c;
            }

            @Override
            public boolean runnerOnPath() {
                return onPath("npx");
            }

            /** The vendor's own CLI on the PATH, or the adapter already in the npx cache. */
            @Override
            public boolean alreadyPresent() {
                String cli = KNOWN_CLIS.get(binName(pkg));
                return (cli != null && onPath(cli)) || inNpxCache(binName(pkg));
            }
        }

        record Uvx(String pkg, List<String> args, Map<String, String> env) implements Distribution {
            @Override
            public List<String> command() {
                List<String> c = new ArrayList<>(List.of("uvx", pkg));
                c.addAll(args);
                return c;
            }

            @Override
            public boolean runnerOnPath() {
                return onPath("uvx");
            }
        }

        /**
         * A downloadable archive per platform. Runs the tool from the PATH when
         * it is there (goose, opencode), else from the editor's managed install
         * under {@code ~/.dogsbay/agents/<id>} when {@link AgentInstaller} put
         * it there; {@link #canInstall()} says whether that is on offer.
         *
         * @param cmd    the registry's command, relative to the extracted archive
         * @param exe    the command's basename, what a PATH lookup needs
         * @param sha256 the archive's checksum from the registry, or null
         */
        record Binary(String agentId, String platform, String archiveUrl, String cmd, String exe, List<String> args,
                String sha256) implements Distribution {
            @Override
            public List<String> command() {
                Path managed = AgentInstaller.installed(agentId, cmd);
                if (managed != null) {
                    List<String> c = new ArrayList<>(List.of(managed.toString()));
                    c.addAll(args);
                    return c;
                }
                if (exe == null) {
                    return List.of();
                }
                List<String> c = new ArrayList<>(List.of(exe));
                c.addAll(args);
                return c;
            }

            /**
             * Installed by the editor, or on the PATH <em>and</em> answering
             * {@code --help} with a mention of ACP. Basenames like {@code goose} or
             * {@code kilo} collide with unrelated tools, and a bare PATH hit would
             * list them as installed agents.
             */
            @Override
            public boolean runnerOnPath() {
                return AgentInstaller.installed(agentId, cmd) != null || (exe != null && onPath(exe) && speaksAcp(exe));
            }

            @Override
            public boolean alreadyPresent() {
                return runnerOnPath();
            }

            /** There is an archive for this platform and the tool is not here yet. */
            public boolean canInstall() {
                return archiveUrl != null && !archiveUrl.isBlank() && cmd != null && !cmd.isBlank() && !runnerOnPath();
            }
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path cacheFile;
    private final HttpClient http;
    private final URI registryUrl;
    private List<Entry> entries;
    private Instant loadedAt;

    public AgentRegistryCatalog(Path cacheDir) {
        this(cacheDir, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), REGISTRY_URL);
    }

    AgentRegistryCatalog(Path cacheDir, HttpClient http, URI registryUrl) {
        this.cacheFile = cacheDir == null ? null : cacheDir.resolve("acp-registry.json");
        this.http = http;
        this.registryUrl = registryUrl;
    }

    /** Entries from the cache or the bundled snapshot; never blocks on the network. */
    public synchronized List<Entry> entries() {
        if (entries == null) {
            entries = parse(loadCachedOrBundled());
        }
        return entries;
    }

    public List<Entry> installed() {
        return entries().stream().filter(Entry::isInstalled).toList();
    }

    /** Agents whose tool is already here: start at once. */
    public List<Entry> ready() {
        return entries().stream().filter(e -> e.readiness() == Readiness.READY).toList();
    }

    /** Agents the editor can install here: a binary archive for this platform, not present yet. */
    public List<Entry> installable() {
        return entries().stream()
                .filter(e -> e.distribution() instanceof Distribution.Binary b && b.canInstall()).toList();
    }

    /** Agents that can run here after a first-use download. */
    public List<Entry> downloadable() {
        return entries().stream().filter(e -> e.readiness() == Readiness.DOWNLOAD).toList();
    }

    public Optional<Entry> byId(String id) {
        return entries().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /**
     * Fetch the live registry if the cache is older than a day. Safe to call
     * from a background thread; failures leave the current entries in place.
     */
    public boolean refresh() {
        synchronized (this) {
            entries();   // establishes loadedAt from the cache, if any
            if (loadedAt != null && loadedAt.plus(REFRESH).isAfter(Instant.now())) {
                return false;
            }
        }
        // The network round trip happens outside the lock so entries() never
        // waits on it; only the result is published under the lock.
        String body;
        List<Entry> fresh;
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(registryUrl)
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                return false;
            }
            body = r.body();
            fresh = parse(JSON.readTree(body));
        } catch (IOException | InterruptedException | RuntimeException e) {
            return false;
        }
        if (fresh.isEmpty()) {
            return false;
        }
        synchronized (this) {
            entries = fresh;
            loadedAt = Instant.now();
        }
        if (cacheFile != null) {
            try {
                Files.createDirectories(cacheFile.getParent());
                Files.writeString(cacheFile, body, StandardCharsets.UTF_8);
            } catch (IOException ignore) {
                // the fresh entries are in memory regardless
            }
        }
        return true;
    }

    private JsonNode loadCachedOrBundled() {
        if (cacheFile != null && Files.isRegularFile(cacheFile)) {
            try {
                JsonNode n = JSON.readTree(Files.readString(cacheFile, StandardCharsets.UTF_8));
                loadedAt = Files.getLastModifiedTime(cacheFile).toInstant();
                return n;
            } catch (IOException ignore) {
                // fall through to the bundled copy
            }
        }
        try (InputStream in = AgentRegistryCatalog.class.getResourceAsStream(BUNDLED)) {
            return in == null ? JSON.createObjectNode() : JSON.readTree(in);
        } catch (IOException e) {
            return JSON.createObjectNode();
        }
    }

    static List<Entry> parse(JsonNode registry) {
        List<Entry> out = new ArrayList<>();
        for (JsonNode a : registry.path("agents")) {
            List<String> authors = new ArrayList<>();
            a.path("authors").forEach(x -> authors.add(x.asText()));
            out.add(new Entry(a.path("id").asText(), a.path("name").asText(), a.path("version").asText(null),
                    a.path("description").asText(null), a.path("license").asText(null), authors,
                    distribution(a.path("id").asText(), a.path("distribution"))));
        }
        return out;
    }

    private static Distribution distribution(String agentId, JsonNode d) {
        if (d.has("npx")) {
            return new Distribution.Npx(d.path("npx").path("package").asText(), args(d.path("npx")), env(d.path("npx")));
        }
        if (d.has("uvx")) {
            return new Distribution.Uvx(d.path("uvx").path("package").asText(), args(d.path("uvx")), env(d.path("uvx")));
        }
        if (d.has("binary")) {
            String platform = platformKey();
            JsonNode b = d.path("binary").path(platform);
            String cmd = b.path("cmd").asText(null);
            String exe = null;
            if (cmd != null && !cmd.isBlank()) {
                String name = cmd.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1);
                exe = name.endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
            }
            return new Distribution.Binary(agentId, platform, b.path("archive").asText(null), cmd, exe, args(b),
                    b.path("sha256").asText(null));
        }
        return null;
    }

    private static Map<String, String> env(JsonNode dist) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        dist.path("env").fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    private static List<String> args(JsonNode dist) {
        List<String> out = new ArrayList<>();
        dist.path("args").forEach(x -> out.add(x.asText()));
        return out;
    }

    /**
     * The vendor CLI that an npx adapter wraps, by the adapter's bin name.
     * Having it on the PATH means the user has installed and, almost always,
     * signed in to that agent.
     */
    static final Map<String, String> KNOWN_CLIS = Map.ofEntries(
            Map.entry("claude-agent-acp", "claude"),
            Map.entry("codex-acp", "codex"),
            Map.entry("gemini-cli", "gemini"),
            Map.entry("copilot", "copilot"),
            Map.entry("cline", "cline"),
            Map.entry("kilo", "kilo"),
            Map.entry("qwen-code", "qwen"),
            Map.entry("cursor-agent", "cursor-agent"),
            Map.entry("auggie", "auggie"),
            Map.entry("droid", "droid"));

    /** {@code @scope/name@1.2.3} becomes {@code name}. */
    static String binName(String pkg) {
        String p = pkg;
        int slash = p.lastIndexOf('/');
        if (slash >= 0) {
            p = p.substring(slash + 1);
        }
        int at = p.indexOf('@');
        return at > 0 ? p.substring(0, at) : p;
    }

    private static final Map<String, Boolean> ACP_PROBE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether {@code exe --help} mentions ACP: a cheap identity check for
     * native binaries whose basename is not distinctive. Cached per process;
     * a probe that hangs or fails counts as no.
     */
    static boolean speaksAcp(String exe) {
        return ACP_PROBE.computeIfAbsent(exe, e -> {
            try {
                Process p = new ProcessBuilder(e, "--help").redirectErrorStream(true).start();
                p.getOutputStream().close();
                byte[] out = p.getInputStream().readNBytes(64 * 1024);
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
                String text = new String(out, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                return text.contains("acp") || text.contains("agent client protocol");
            } catch (IOException | InterruptedException | RuntimeException ex) {
                return false;
            }
        });
    }

    /** True when npx has this adapter's bin cached under {@code ~/.npm/_npx}. */
    static boolean inNpxCache(String bin) {
        Path cache = Path.of(System.getProperty("user.home"), ".npm", "_npx");
        if (!Files.isDirectory(cache)) {
            return false;
        }
        try (var dirs = Files.list(cache)) {
            return dirs.anyMatch(d -> Files.exists(d.resolve("node_modules").resolve(".bin").resolve(bin))
                    || Files.exists(d.resolve("node_modules").resolve(".bin").resolve(bin + ".cmd")));
        } catch (IOException e) {
            return false;
        }
    }

    static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String o = os.contains("mac") ? "darwin" : os.contains("win") ? "windows" : "linux";
        String a = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64" : "x86_64";
        return o + "-" + a;
    }

    static boolean onPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path p = Path.of(dir);
            if (Files.isExecutable(p.resolve(exe))
                    || (windows && (Files.exists(p.resolve(exe + ".cmd")) || Files.exists(p.resolve(exe + ".exe"))))) {
                return true;
            }
        }
        return false;
    }
}
