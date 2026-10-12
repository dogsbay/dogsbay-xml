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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus;
import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.dogsbayaieditor.dita.live.LivePreviewTargets;
import com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * Starting, stopping and listing live previews, for every process that runs them (the editor, the headless
 * MCP server, the command line). A preview is known to the process that runs it and, through
 * {@link PreviewRegistry}, to every other: one preview per project and deliverable on the machine, and any
 * process can see and stop any preview. See {@code plans/preview-registry.md}.
 */
public final class PreviewCommands {

    /** How a process shows a preview it starts (the editor: in its window; elsewhere: just started). */
    @FunctionalInterface
    public interface Starter {
        LivePreview start(Path root, Deliverable deliverable, String ditaOtHome) throws CommandException;
    }

    /** How a process stops a preview it runs (the editor: closing its window). */
    @FunctionalInterface
    public interface Stopper {
        void stop(LivePreview preview) throws CommandException;
    }

    /** How long a stop waits for another process to stop its preview (tests shorten it). */
    static long stopWaitMillis = 10_000;

    private PreviewCommands() {
    }

    private static long stopWait() {
        return Long.getLong("dogsbay.previews.stopWait", stopWaitMillis);
    }

    /** The project root of a preview command, canonical; refused when it is not a folder. */
    public static Path root(String root) throws CommandException {
        if (root == null || root.isBlank() || !java.nio.file.Files.isDirectory(Path.of(root))) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "root must be an existing project directory: " + root);
        }
        return LivePreview.canonical(Path.of(root));
    }

    /** start or stop, or a refusal. */
    public static String action(String action) throws CommandException {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!a.equals("start") && !a.equals("stop")) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "action must be start or stop, not '" + action + "'");
        }
        return a;
    }

    /**
     * The deliverable a preview command means: the one named, else {@code active} (the editor's active
     * deliverable, or the project config's), else the only one that can be previewed.
     */
    public static Deliverable target(Path root, String requested, String active, String ditaOtHome)
            throws CommandException {
        com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx;
        try {
            List<Path> catalogs = new ArrayList<>();
            Path bundled = com.dogsbay.dogsbayaieditor.validate.DocumentValidator.builtinDitaCatalog();
            if (bundled != null) {
                catalogs.add(bundled);
            }
            ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader.load(root, null, catalogs,
                    DitaOtHome.resolve(root, ditaOtHome == null || ditaOtHome.isBlank() ? null : Path.of(ditaOtHome)));
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to load project context: " + e.getMessage(), e);
        }
        try {
            return LivePreviewTargets.choose(ctx.deliverables(), requested, active);
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, e.getMessage());
        }
    }

    /** The deliverable the project's config marks active (local.xml) or default (config.xml), or null. */
    public static String configuredDeliverable(Path root) {
        try {
            var c = com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load(root);
            return c.getActiveDeliverableName() != null ? c.getActiveDeliverableName() : c.getDefaultDeliverableName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Start a deliverable's preview, unless one runs already: in this process (that one), or in another
     * (that one, described from the registry, so there are never two builds into one output folder). A
     * hosted or external agent's preview publishes only inside the project.
     */
    public static LivePreviewStatus start(Path root, Deliverable deliverable, String ditaOtHome, Starter starter,
            Stopper stopper) throws CommandException {
        var here = LivePreview.running(root, deliverable.name());
        if (here.isPresent() && here.get().state() == LivePreview.State.FAILED && !here.get().devServerAlive()) {
            stopper.stop(here.get()); // a preview that failed is started again, not returned as it was
            here = java.util.Optional.empty();
        }
        if (here.isPresent()) {
            return LivePreviewStatus.of(here.get());
        }
        var failedElsewhere = PreviewRegistry.find(root, deliverable.name())
                .filter(e -> !e.ours() && "FAILED".equals(e.state()) && e.url() == null);
        if (failedElsewhere.isPresent()) {
            stop(root, deliverable.name(), stopper); // ask its process to let it go, then start it here
        }
        var elsewhere = PreviewRegistry.find(root, deliverable.name()).filter(e -> !e.ours());
        if (elsewhere.isPresent()) {
            var e = elsewhere.get();
            LivePreviewStatus s = LivePreviewStatus.of(e);
            return new LivePreviewStatus(s.deliverable(), s.state(), "Already running in " + describe(e)
                    + "; not started again" + (e.message() == null ? "" : " (" + e.message() + ")"), s.url(),
                    s.outputDir(), s.lastPublish(), s.recentOutput(), s.owner(), s.pid(), s.log());
        }
        if (SessionContext.isBound()) {
            var session = SessionContext.current();
            if (session.isAgent() && session.kind() != SessionKind.BUILTIN
                    && !WriteGate.real(deliverable.outputFolder(root)).startsWith(WriteGate.real(root))) {
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED, "The output folder of "
                        + "deliverable \"" + deliverable.name() + "\" (" + deliverable.outputFolder(root)
                        + ") is outside the project; " + session.displayName() + " may only publish into it");
            }
            if (session.isAgent() && session.kind() != SessionKind.BUILTIN && LivePreview.namesItsCli(deliverable)) {
                // every republish runs that command as the converter: an agent that can edit the
                // project file could name anything
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED, "Deliverable \""
                        + deliverable.name() + "\" names its own dogsbay CLI (dogsbay.cli), which each publish would run; "
                        + session.displayName() + " may not start it. Ask the writer to start this preview.");
            }
        }
        return LivePreviewStatus.of(starter.start(root, deliverable, ditaOtHome));
    }

    /**
     * Stop a preview: one this process runs directly, one another process runs by asking it (a stop file
     * its process picks up within a second). A background preview (the command line's) that has not
     * stopped after ten seconds is ended. With no deliverable named, the only running one.
     */
    public static LivePreviewStatus stop(Path root, String deliverable, Stopper stopper) throws CommandException {
        List<PreviewRegistry.Entry> entries = PreviewRegistry.list(root);
        List<LivePreview> here = LivePreview.running(root);
        List<String> names = new ArrayList<>();
        here.forEach(p -> names.add(p.deliverable().name()));
        entries.stream().filter(e -> !e.ours()).forEach(e -> names.add(e.deliverable()));
        List<String> matching = deliverable == null || deliverable.isBlank() ? names
                : names.stream().filter(deliverable::equals).toList();
        if (matching.size() > 1) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Several previews are running; name the deliverable to stop: " + matching);
        }
        if (matching.isEmpty()) {
            return LivePreviewStatus.stopped(deliverable == null ? "" : deliverable);
        }
        String name = matching.get(0);
        var mine = here.stream().filter(p -> p.deliverable().name().equals(name)).findFirst();
        if (mine.isPresent()) {
            stopper.stop(mine.get());
            return LivePreviewStatus.stopped(name);
        }
        PreviewRegistry.Entry e = entries.stream().filter(x -> x.deliverable().equals(name)).findFirst().orElseThrow();
        PreviewRegistry.requestStop(e);
        if (waitGone(root, name, stopWait())) {
            return LivePreviewStatus.stopped(name);
        }
        PreviewRegistry.withdrawStop(root, name); // not taken: it must not stop a later preview
        var process = ProcessHandle.of(e.pid()).filter(h -> e.alive());
        if ("cli".equals(e.owner()) && process.isPresent() && isPreviewProcess(process.get())) {
            // a background preview process that does not answer: it runs this preview alone, so end it, its dev
            // server first (a forced end skips the process's own cleanup)
            ProcessHandle h = process.get();
            h.descendants().forEach(ProcessHandle::destroy);
            h.destroy();
            if (waitGone(root, name, stopWait() / 2) || !e.alive()) {
                return LivePreviewStatus.stopped(name);
            }
            h.descendants().forEach(ProcessHandle::destroyForcibly);
            h.destroyForcibly();
            return LivePreviewStatus.stopped(name);
        }
        throw new CommandException(CommandException.ErrorCode.CONFLICT, "Asked " + describe(e) + " to stop the preview "
                + "of \"" + name + "\", but it is still running; stop it there");
    }

    /** Whether a process is a background live preview (the command line it was started with says so, when known). */
    static boolean isPreviewProcess(ProcessHandle h) {
        return h.info().commandLine().map(c -> c.contains("live-preview")).orElse(true)
                || h.info().arguments().map(a -> java.util.Arrays.asList(a).contains("live-preview")).orElse(false);
    }

    /** Every preview of a project, whoever runs it; with a deliverable named, that one (stopped when none runs). */
    public static List<LivePreviewStatus> status(Path root, String deliverable) {
        List<LivePreviewStatus> out = new ArrayList<>();
        for (LivePreview p : LivePreview.running(root)) {
            out.add(LivePreviewStatus.of(p));
        }
        for (PreviewRegistry.Entry e : PreviewRegistry.list(root)) {
            if (!e.ours()) {
                out.add(LivePreviewStatus.of(e));
            }
        }
        if (deliverable != null && !deliverable.isBlank()) {
            out = out.stream().filter(s -> deliverable.equals(s.deliverable())).toList();
            if (out.isEmpty()) {
                return List.of(LivePreviewStatus.stopped(deliverable));
            }
        }
        return out;
    }

    private static boolean waitGone(Path root, String deliverable, long millis) {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            if (PreviewRegistry.find(root, deliverable).isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String describe(PreviewRegistry.Entry e) {
        String who = switch (e.owner() == null ? "" : e.owner()) {
            case "editor" -> "the editor";
            case "mcp" -> "a headless MCP server";
            case "cli" -> "a background preview (dogsbay-xml live-preview)";
            default -> "another process";
        };
        return who + ", process " + e.pid();
    }
}
