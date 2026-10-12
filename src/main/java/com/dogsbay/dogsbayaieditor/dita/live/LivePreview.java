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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.commands.BuildDeliverablesCommand;
import com.dogsbay.dogsbayaieditor.commands.DitaOtTemp;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.Param;
import com.dogsbay.xml.dita.DogsbayPublish;
import com.dogsbay.xml.dita.NodeLocator;

/**
 * A live preview of a dogsbay deliverable, in two stages. Every change in the project
 * republishes the deliverable with the {@code dogsbay} transtype in this process (DITA-OT
 * stays warm), writing the files it changed to a manifest; the preview then POSTs that
 * manifest to the bundled dogsbay-ssg dev session running in the output folder, which
 * re-renders those pages and reloads the browser.
 *
 * <p>The boundary between the two is the list of changed files: this class's job ends when
 * the Markdown is up to date, and the site builder's begins. It needs Node.js and nothing
 * else — the dev session is the {@code ssg/dev.mjs} the DITA-OT plugin carries, so there is
 * no Astro, no package install, no network access and no dogsbay CLI, matching what the
 * {@code dogsbay-site} transtype already does for a batch build.
 *
 * <p>Only deliverables with the {@code dogsbay} or {@code dogsbay-site} transtype are
 * previewed, so another output type's folder is never overwritten.
 */
public final class LivePreview {

    private static final Logger LOG = LoggerFactory.getLogger(LivePreview.class);

    /**
     * Where the preview is. READY_WITH_ERRORS is a served site whose last publish had errors: the
     * pages are there but some are missing or stale, and a caller that reported READY as success
     * would be reporting a failed build as a working preview (2026-09-17).
     */
    public enum State { STARTING, PUBLISHING, READY, READY_WITH_ERRORS, FAILED, STOPPED }

    /** Receives the preview's state changes and the dev server's output lines (on background threads). */
    public interface Listener {
        void changed(LivePreview preview);

        void output(String line);
    }

    /**
     * The dev session's ready line, agreed with dogsbay-ssg and pinned on both sides
     * (dogsbay-ssg {@code 6d5e8ba}, whose test uses
     * {@code ^DOGSBAY_DEV_READY http://127\\.0\\.0\\.1:\\d+/$}). The marker is matched
     * exactly, since that is what could drift; the address is left open so a session bound to
     * another host or an IPv6 address still reports it. Until this matches, a preview has no
     * address and sits in "Starting", so a silent change here is the worst kind.
     */
    private static final Pattern READY_LINE = Pattern.compile("^DOGSBAY_DEV_READY (https?://\\S+)$");
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*[A-Za-z]");
    /** The dev session's control endpoint, relative to its address. */
    private static final String CHANGE_PATH = "/_dogsbay/dev/change";
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final List<LivePreview> RUNNING = new CopyOnWriteArrayList<>();
    private static final int RECENT_OUTPUT_LINES = 40;
    /**
     * One publish at a time per project and deliverable, across previews: a preview started right after
     * another was stopped would otherwise publish into the same output (and temp) folder while the
     * stopped one's in-process build, which does not stop on an interrupt, is still writing there.
     */
    private static final java.util.Map<String, Object> PUBLISH_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> RUNNING.forEach(p -> p.stop(true)), "live-preview-shutdown"));
    }

    private final Path root;
    private final Deliverable deliverable;
    private final String ditaOtHome;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-preview-publish");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean republishQueued = new AtomicBoolean();
    /**
     * This session's bearer token, one per preview. Without it the dev session serves no control
     * endpoint at all, so another local process cannot drive someone's preview.
     */
    private final String token = newToken();
    /**
     * Where the converter writes the files each publish changed. Under {@code .dogsbay}, which
     * {@link ProjectWatcher#SKIPPED_FOLDERS} already ignores — written into the project proper it
     * would look like a source change and every publish would trigger the next one.
     */
    private final File manifest;
    private final java.util.ArrayDeque<String> recentOutput = new java.util.ArrayDeque<>();

    private volatile State state = State.STARTING;
    private volatile String message = "Starting";
    private volatile String url;
    private volatile DeliverableBuild lastBuild;
    private volatile File outputDir;
    private volatile Process devServer;
    private volatile ProjectWatcher watcher;
    private volatile List<Path> projectOutputs = List.of();

    public LivePreview(Path root, Deliverable deliverable, String ditaOtHome) {
        this.root = canonical(root);
        this.deliverable = deliverable;
        this.ditaOtHome = ditaOtHome;
        this.manifest = this.root.resolve(".dogsbay").resolve("previews")
                .resolve(DitaOtTemp.safeName(deliverable.name()) + "-changed.json").toFile();
    }

    /** Whether a deliverable can be previewed: its transtype is {@code dogsbay} or {@code dogsbay-site}. */
    public static boolean canPreview(Deliverable deliverable) {
        return deliverable != null && DogsbayPublish.isDogsbay(deliverable.transtype());
    }

    /**
     * A project folder as one path whatever it was reached by: the editor may have opened a folder through
     * a symbolic link that an agent names by its real path, and the two must find the same preview.
     */
    public static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** The running preview of a deliverable in a project, if any. */
    public static Optional<LivePreview> running(Path root, String deliverable) {
        Path r = canonical(root);
        return RUNNING.stream().filter(p -> p.root.equals(r) && p.deliverable.name().equals(deliverable)
                && p.state != State.STOPPED).findFirst();
    }

    /** A project's running previews. */
    public static List<LivePreview> running(Path root) {
        Path r = canonical(root);
        return RUNNING.stream().filter(p -> p.root.equals(r) && p.state != State.STOPPED).toList();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public State state() {
        return state;
    }

    /** A short description of what the preview is doing or why it failed. */
    public String message() {
        return message;
    }

    /** The dev server's address, once it is running. */
    public String url() {
        return url;
    }

    /** The last republish, with its step times and diagnostics. */
    public DeliverableBuild lastBuild() {
        return lastBuild;
    }

    public Deliverable deliverable() {
        return deliverable;
    }

    /** The published site project's folder, once the first publish has run. */
    public File outputDir() {
        return outputDir;
    }

    /** The last lines of the dev server's output (and the site scaffolding's), oldest first. */
    public List<String> recentOutput() {
        synchronized (recentOutput) {
            return List.copyOf(recentOutput);
        }
    }

    /** Publish, start the dev server and watch the project, in the background. */
    public void start() {
        // One preview of a deliverable on the machine: claimed before anything is built, so a second process
        // starting it at the same moment does not publish into the same folder
        if (!PreviewRegistry.claim(entry())) {
            String where = PreviewRegistry.find(root, deliverable.name()).map(e -> e.owner() + ", process " + e.pid())
                    .orElse("another process");
            moveTo(State.FAILED, "Already running in " + where);
            notifyChanged();
            return;
        }
        RUNNING.add(this);
        register();
        ensurePoller();
        publisher.submit(() -> {
            try {
                // Each step checks for a stop: the first publish takes seconds, and a stop during it must
                // not be followed by a dev server nothing can reach any more (it has left RUNNING)
                if (!publish() || stopped()) {
                    return;
                }
                startDevServer();
                if (stopped()) {
                    return;
                }
                projectOutputs = projectOutputs(root);
                ProjectWatcher w = new ProjectWatcher(root, this::isOutput, 800, this::republish);
                watcher = w;
                w.start();
                if (stopped()) {
                    w.close();
                }
            } catch (Exception e) {
                fail("Could not start the live preview: " + e.getMessage());
                LOG.warn("Live preview failed to start", e);
            }
        });
    }

    /** Publish again (a project change, or Republish Now). A republish already queued is not repeated. */
    public void republish() {
        if (state == State.STOPPED || state == State.FAILED && devServer == null) {
            return;
        }
        if (republishQueued.compareAndSet(false, true)) {
            try {
                publisher.submit(() -> {
                    republishQueued.set(false);
                    publish();
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                republishQueued.set(false); // stopped meanwhile
            }
        }
    }

    private boolean stopped() {
        return state == State.STOPPED;
    }

    /** Move to a state, unless the preview has been stopped: nothing may bring a stopped preview back. */
    synchronized boolean moveTo(State next, String text) {
        if (state == State.STOPPED) {
            return false;
        }
        state = next;
        message = text;
        return true;
    }

    /**
     * Stop the dev server and the watcher. Returns at once: the preview is STOPPED and out of the running
     * list when this returns, and the dev server is asked to end; one that has not ended after five seconds
     * is killed from a background thread, so a stop from the event thread does not freeze the window.
     */
    public void stop() {
        stop(false);
    }

    /** As {@link #stop()}; {@code wait} blocks until the dev server has ended (the editor's shutdown). */
    public void stop(boolean wait) {
        synchronized (this) {
            if (state == State.STOPPED) {
                return;
            }
            state = State.STOPPED;
            message = "Stopped";
            // under the lock register() takes: no entry can be written after this one is removed
            PreviewRegistry.remove(root, deliverable.name());
        }
        RUNNING.remove(this);
        ProjectWatcher w = watcher;
        if (w != null) {
            w.close();
        }
        publisher.shutdownNow();
        Process process = devServer;
        if (process != null) {
            if (wait) {
                end(process);
            } else {
                Thread t = new Thread(() -> end(process), "live-preview-stop");
                t.setDaemon(true);
                t.start();
            }
        }
        notifyChanged();
    }

    /** End a process and its children: politely, then forcibly after five seconds. */
    private static void end(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether a path is published output, whose changes must not trigger a republish: this preview's,
     * any other deliverable's in the project, and any other running preview's. Excluding only its own
     * made two previews of one project (a macOS and a Windows guide side by side) republish each other
     * forever: each publish was a project change to the other (reported 2026-09-19).
     */
    boolean isOutput(Path path) {
        Path p = path.toAbsolutePath().normalize();
        List<Path> outputs = new ArrayList<>(projectOutputs);
        File own = outputDir;
        if (own != null) {
            outputs.add(own.toPath());
        }
        for (LivePreview other : RUNNING) {
            File theirs = other.outputDir;
            if (theirs != null) {
                outputs.add(theirs.toPath());
            }
        }
        outputs.addAll(elsewhere());
        return inAny(p, outputs);
    }

    private static volatile long elsewhereAt;
    private static volatile List<Path> elsewhere = List.of();

    /** The output folders of previews other processes run (the registry, read at most every three seconds). */
    private static List<Path> elsewhere() {
        long now = System.currentTimeMillis();
        if (now - elsewhereAt > 3000) {
            elsewhere = PreviewRegistry.list(null).stream().filter(e -> !e.ours() && e.outputDir() != null)
                    .map(e -> Path.of(e.outputDir())).toList();
            elsewhereAt = now;
        }
        return elsewhere;
    }

    private final String startedAt = java.time.Instant.now().toString();

    /** Write this preview's registry entry, so other processes see it (and can ask it to stop). */
    private synchronized void register() {
        if (state == State.STOPPED || !RUNNING.contains(this)) {
            return;
        }
        PreviewRegistry.put(entry());
    }

    private PreviewRegistry.Entry entry() {
        DeliverableBuild build = lastBuild;
        Integer errors = null;
        Integer warnings = null;
        if (build != null) {
            errors = (int) build.messages().stream().filter(com.dogsbay.xml.dita.DitaOtMessage::isError).count();
            warnings = (int) build.messages().stream()
                    .filter(m -> "WARN".equals(m.severity()) || "WARNING".equals(m.severity())).count();
        }
        File out = outputDir;
        return new PreviewRegistry.Entry(root.toString(), deliverable.name(), PreviewRegistry.owner(),
                ProcessHandle.current().pid(), url, state.name(), message, out == null ? null : out.getAbsolutePath(),
                PreviewRegistry.log(), startedAt, errors, warnings, PreviewRegistry.processStart());
    }

    /** How this process stops a preview another process asked it to stop (the editor closes its window). */
    private static volatile java.util.function.Consumer<LivePreview> remoteStopper = LivePreview::stop;

    public static void setRemoteStopper(java.util.function.Consumer<LivePreview> stopper) {
        remoteStopper = stopper;
    }

    private static Thread poller;

    /** Once per process: stop the previews another process asked to stop (a stop file beside the entry). */
    private static synchronized void ensurePoller() {
        if (poller != null) {
            return;
        }
        poller = new Thread(() -> {
            while (true) {
                for (LivePreview p : RUNNING) {
                    try {
                        if (PreviewRegistry.takeStopRequest(p.root, p.deliverable.name(), p.startedAt)) {
                            remoteStopper.accept(p);
                        }
                    } catch (RuntimeException e) {
                        LOG.warn("Could not stop a live preview another process asked to stop", e); // keep polling
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "live-preview-stop-requests");
        poller.setDaemon(true);
        poller.start();
    }

    /** Whether the dev server is running (it may have ended on its own, with the preview left FAILED). */
    public boolean devServerAlive() {
        Process p = devServer;
        return p != null && p.isAlive();
    }

    /**
     * Whether the deliverable names its own dogsbay CLI ({@code dogsbay.cli}). The preview no
     * longer runs a CLI for the dev server, but the plugin still honours this parameter as the
     * <em>converter</em> a publish runs ({@code build_dogsbay.xml}, {@code dogsbay.converter}),
     * so a deliverable that names one still runs an arbitrary command on every republish.
     */
    public static boolean namesItsCli(Deliverable deliverable) {
        return deliverable.params().stream().anyMatch(p -> p.name().equals("dogsbay.cli"));
    }

    /** The project root, canonical. */
    public Path root() {
        return root;
    }

    static boolean inAny(Path path, java.util.Collection<Path> folders) {
        for (Path folder : folders) {
            Path f = folder.toAbsolutePath().normalize();
            if (path.startsWith(f) || path.startsWith(canonical(f))) {
                return true;
            }
        }
        return false;
    }

    /** The output folder of every deliverable in the project, from its project files; empty when none can be read. */
    static List<Path> projectOutputs(Path root) {
        try {
            return com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader.load(root, null, List.of(), null)
                    .deliverables().stream().map(d -> d.outputFolder(root)).toList();
        } catch (Exception e) {
            LOG.debug("Could not read the project's deliverables for the live preview's watcher", e);
            return List.of();
        }
    }

    /** Publish the deliverable with the dogsbay transtype. False when the build could not run. */
    private boolean publish() {
        if (!moveTo(State.PUBLISHING, "Publishing")) {
            return false;
        }
        notifyChanged();
        try {
            List<DeliverableBuild> results;
            synchronized (PUBLISH_LOCKS.computeIfAbsent(root + "\n" + deliverable.name(), k -> new Object())) {
                if (stopped()) {
                    return false;
                }
                java.nio.file.Files.createDirectories(manifest.toPath().getParent());
                java.nio.file.Files.deleteIfExists(manifest.toPath());
                results = new HeadlessExecutor().execute(new BuildDeliverablesCommand(
                        root.toString(), null, null, ditaOtHome, List.of(deliverable.name()), null, "dogsbay",
                        manifest.toString()));
            }
            DeliverableBuild build = results.stream().filter(b -> b.name().equals(deliverable.name())).findFirst().orElse(null);
            if (build == null) {
                fail("The deliverable " + deliverable.name() + " was not built.");
                return false;
            }
            lastBuild = build;
            outputDir = new File(build.outputDir());
            long errors = build.messages().stream().filter(com.dogsbay.xml.dita.DitaOtMessage::isError).count();
            // The manifest is the converter's last write, after everything a publish can fail at,
            // so its presence means the site project on disk is complete. A stronger signal than
            // the content/nav.yml probe this replaces, and independent of the error count.
            if (!manifest.isFile()) {
                fail("Publishing failed" + (errors > 0 ? " with " + errors + " error(s)" : "") + "; see the Project Validation tab.");
                return false;
            }
            String rejected = applyChanges(manifest);
            if (rejected != null) {
                fail("The site builder could not apply the change: " + rejected);
                return false;
            }
            // Ready once the dev server has reported its address; until then still starting
            if (!moveTo(published(url, errors), "Published" + (errors > 0 ? " with " + errors + " error(s)" : ""))) {
                return false;
            }
            notifyChanged();
            return true;
        } catch (Exception e) {
            fail("Publishing failed: " + e.getMessage());
            return false;
        }
    }

    /** What a finished publish leaves the preview in: serving, and whether its pages are trustworthy. */
    static State published(String url, long errors) {
        if (url == null) {
            return State.STARTING; // the dev server has not reported its address yet
        }
        return errors > 0 ? State.READY_WITH_ERRORS : State.READY;
    }

    /** Start the bundled dogsbay-ssg dev session in the output folder and read its address. */
    private void startDevServer() throws IOException, InterruptedException {
        message = "Starting the site builder";
        notifyChanged();
        Path entry = DogsbayPublish.devSession(ditaOtHome)
                .orElseThrow(() -> new IOException(DogsbayPublish.DEV_SESSION_MISSING));
        Path node = NodeLocator.system().find().orElseThrow(() -> new IOException(DogsbayPublish.NODE_MISSING));
        // The session is lazy, searchless and watcherless by construction — dev.mjs takes no
        // flags for those, so passing any would be a usage error. Port 0 picks a free one.
        ProcessBuilder pb = process(List.of(node.toString(), entry.toString(), ".", "-p", "0"), outputDir);
        // The control endpoint exists only when the session has a token, and refuses anything
        // without it. Passed in the environment, not on the command line, where `ps` would show it.
        pb.environment().put("DOGSBAY_DEV_TOKEN", token);
        Process server = pb.start();
        devServer = server;
        if (stopped()) {
            end(server); // stopped while it was being started
            return;
        }
        pump(devServer, true);
        devServer.onExit().thenAccept(p -> {
            if (state != State.STOPPED) {
                fail("The site builder stopped (exit " + p.exitValue() + ")");
            }
        });
    }

    /**
     * Hand the dev session the files the last publish changed, and return what it made of them.
     *
     * <p>The manifest the converter wrote <em>is</em> the request body — {@code {written, removed}}
     * is the shape the endpoint takes — so its bytes are forwarded as they are rather than parsed
     * and rebuilt.
     *
     * <p>Called whenever a manifest exists, whatever the publish's error count. DITA-OT recovers
     * from most content errors and still writes complete output (hence READY_WITH_ERRORS), and
     * skipping the POST then would freeze the preview exactly while someone fixes a broken topic.
     */
    private String applyChanges(File manifest) {
        if (url == null) {
            return null; // the session has not reported its address yet; its first render is the whole site
        }
        try {
            byte[] body = java.nio.file.Files.readAllBytes(manifest.toPath());
            java.net.http.HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build()
                    .send(java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url.replaceAll("/$", "") + CHANGE_PATH))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .timeout(java.time.Duration.ofMinutes(2))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
                            .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            com.fasterxml.jackson.databind.JsonNode json = MAPPER.readTree(response.body());
            if (response.statusCode() != 200) {
                String error = json.path("error").asText("HTTP " + response.statusCode());
                com.fasterxml.jackson.databind.JsonNode paths = json.path("paths");
                return error + (paths.isArray() && !paths.isEmpty() ? ": " + paths : "");
            }
            com.fasterxml.jackson.databind.JsonNode warnings = json.path("warnings");
            for (com.fasterxml.jackson.databind.JsonNode w : warnings) {
                String line = "warning: " + w.asText();
                remember(line);
                listeners.forEach(l -> l.output(line));
            }
            return json.path("error").isNull() || json.path("error").isMissingNode() ? null : json.path("error").asText();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Could not tell the site builder what changed", e);
            return "the site builder did not accept the change: " + e.getMessage();
        }
    }

    /** A fresh bearer token for one dev session. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private void remember(String line) {
        synchronized (recentOutput) {
            recentOutput.addLast(line);
            while (recentOutput.size() > RECENT_OUTPUT_LINES) {
                recentOutput.removeFirst();
            }
        }
    }

    /**
     * A process in the output folder, with Node.js's folder first on the PATH.
     *
     * <p>Stdin is left as ProcessBuilder's default pipe, which this process holds open and the
     * OS closes when it exits. The dev session stops on stdin EOF so it never outlives its
     * editor; an inherited stdin would hand it an immediate EOF in a desktop-launched editor or
     * under {@code live-preview start}, whose detached JVM has stdin from the null device.
     */
    static ProcessBuilder process(List<String> command, File directory) {
        ProcessBuilder pb = new ProcessBuilder(command).directory(directory).redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        NodeLocator.system().find().ifPresent(node -> {
            String pathKey = env.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
            String current = env.getOrDefault(pathKey, "");
            env.put(pathKey, node.getParent() + File.pathSeparator + current);
        });
        env.put("FORCE_COLOR", "0");
        return pb;
    }

    /** Forward a process's output; for the dev server, read its address. */
    private void pump(Process process, boolean devServerOutput) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = ANSI.matcher(line).replaceAll("");
                    remember(text);
                    listeners.forEach(l -> l.output(text));
                    if (devServerOutput && url == null) {
                        String found = localUrl(text);
                        if (found != null) {
                            // The state before the address: a caller that sees the address must see it ready
                            if (state != State.PUBLISHING) {
                                DeliverableBuild build = lastBuild;
                                moveTo(published(found, build == null ? 0
                                        : build.messages().stream().filter(com.dogsbay.xml.dita.DitaOtMessage::isError).count()),
                                        "Ready");
                            }
                            url = found;
                            notifyChanged();
                        }
                    }
                }
            } catch (IOException e) {
                // process ended
            }
        }, devServerOutput ? "live-preview-dev-server" : "live-preview-init");
        t.setDaemon(true);
        t.start();
        if (!devServerOutput) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Astro's dev server address from a line of its startup banner ("┃ Local    http://localhost:4321/"), or null. */
    static String localUrl(String line) {
        Matcher m = READY_LINE.matcher(ANSI.matcher(line).replaceAll("").strip());
        return m.matches() ? m.group(1) : null;
    }

    private void fail(String reason) {
        if (moveTo(State.FAILED, reason)) {
            notifyChanged();
        }
    }

    private void notifyChanged() {
        if (state != State.STOPPED) {
            register();
        }
        listeners.forEach(l -> {
            try {
                l.changed(this);
            } catch (RuntimeException e) {
                LOG.debug("Live preview listener failed", e);
            }
        });
    }
}
