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

package com.dogsbay.dogsbayaieditor.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.PreviewCommands;
import com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus;
import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry;

/**
 * Live previews from the command line: start one in the background, run one in the foreground, list and
 * stop them, whoever started them (the editor, a headless MCP server, this command). See
 * {@code plans/preview-registry.md}.
 */
@Command(name = "live-preview",
        description = "Live previews of a project's deliverables: publish with the dogsbay transtype, serve with "
                + "'dogsbay site dev', republish on change. Previews run by the editor or a headless MCP server "
                + "are listed and can be stopped here too.",
        subcommands = {LivePreviewCmd.Start.class, LivePreviewCmd.Run.class, LivePreviewCmd.Status.class, LivePreviewCmd.Stop.class})
public class LivePreviewCmd implements Callable<Integer> {

    @Override
    public Integer call() {
        picocli.CommandLine.usage(this, System.out);
        return 2;
    }

    /** Options every subcommand takes. */
    abstract static class Target {
        @Parameters(index = "0", arity = "0..1", description = "The project folder (default: the working directory)")
        String root;

        @Option(names = {"-d", "--deliverable"}, description = "The deliverable (default: the project's active or "
                + "default one, else the only one that can be previewed)")
        String deliverable;

        Path root() throws CommandException {
            return PreviewCommands.root(root == null ? Path.of("").toAbsolutePath().toString() : root);
        }
    }

    static void print(LivePreviewStatus s) {
        System.out.printf("%-20s %-18s %-8s %-28s %s%n", s.deliverable(), s.state(),
                s.owner() == null ? "" : s.owner() + (s.pid() > 0 ? " " + s.pid() : ""), s.url() == null ? "" : s.url(),
                s.message() == null ? "" : s.message());
    }

    @Command(name = "run", description = "Run a live preview in the foreground; Ctrl+C stops it")
    static class Run extends Target implements Callable<Integer> {

        @Option(names = "--background", hidden = true, description = "Started by 'preview start': detach from the terminal")
        boolean background;

        @Override
        public Integer call() throws Exception {
            if (background) {
                // A background preview outlives the terminal that started it: Ctrl+C there, or closing it, is not
                // meant for this process
                for (String signal : List.of("INT", "HUP")) {
                    try {
                        sun.misc.Signal.handle(new sun.misc.Signal(signal), s -> { });
                    } catch (IllegalArgumentException unsupported) {
                        // not on this platform
                    }
                }
            }
            PreviewRegistry.setOwner("cli");
            // A stop asked from elsewhere waits for the dev server to end: this process exits right after it
            LivePreview.setRemoteStopper(preview -> preview.stop(true));
            Path root = root();
            var d = PreviewCommands.target(root, deliverable, deliverable == null
                    ? PreviewCommands.configuredDeliverable(root) : null, null);
            if (background) {
                PreviewRegistry.setLog(logFile(root, d.name()).toString());
            }
            LivePreview[] started = new LivePreview[1];
            LivePreviewStatus s = PreviewCommands.start(root, d, null, (r, del, home) -> {
                var p = new LivePreview(r, del, home);
                p.addListener(new LivePreview.Listener() {
                    @Override
                    public void changed(LivePreview preview) {
                        System.out.println("[" + preview.state() + "] " + preview.message()
                                + (preview.url() == null ? "" : "  " + preview.url()));
                    }

                    @Override
                    public void output(String line) {
                        System.out.println(line);
                    }
                });
                p.start();
                started[0] = p;
                return p;
            }, preview -> preview.stop(true));
            if (started[0] == null) {
                print(s); // already running elsewhere
                return 1;
            }
            LivePreview p = started[0];
            while (p.state() != LivePreview.State.STOPPED) {
                // Failed with no dev server running (the first publish failed, or the server ended): nothing will
                // bring it back, and a process left running would advertise a dead address
                if (p.state() == LivePreview.State.FAILED && !p.devServerAlive()) {
                    Thread.sleep(2000); // a dev server that is still starting gets a moment
                    if (p.state() == LivePreview.State.FAILED && !p.devServerAlive()) {
                        p.stop(true);
                        return 1;
                    }
                }
                Thread.sleep(500);
            }
            return 0;
        }
    }

    static Path logFile(Path root, String deliverable) {
        Path entry = PreviewRegistry.file(root, deliverable);
        return entry.resolveSibling(entry.getFileName().toString().replaceFirst("\\.json$", ".log"));
    }

    @Command(name = "start", description = "Start a live preview in the background; it keeps running until stopped")
    static class Start extends Target implements Callable<Integer> {

        @Option(names = "--no-wait", description = "Return at once instead of waiting until it is ready or has failed")
        boolean noWait;

        @Option(names = "--timeout", description = "Seconds to wait for it to be ready (default ${DEFAULT-VALUE})",
                defaultValue = "600")
        int timeout;

        @Override
        public Integer call() throws Exception {
            Path root = root();
            var d = PreviewCommands.target(root, deliverable, deliverable == null
                    ? PreviewCommands.configuredDeliverable(root) : null, null);
            var already = PreviewRegistry.find(root, d.name());
            if (already.isPresent()) {
                print(LivePreviewStatus.of(already.get()));
                System.out.println("Already running; 'dogsbay-xml live-preview stop -d " + d.name() + "' to stop it.");
                return "FAILED".equals(already.get().state()) ? 1 : 0;
            }
            Path log = logFile(root, d.name());
            Files.createDirectories(log.getParent());
            List<String> command = new ArrayList<>(javaCommand());
            command.addAll(List.of("live-preview", "run", "--background", "-d", d.name(), root.toString()));
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            // the options are passed on explicitly (javaCommand); read from the environment too, they would apply twice
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            pb.environment().remove("JDK_JAVA_OPTIONS");
            Process child = pb.start();
            System.out.println("Starting the preview of \"" + d.name() + "\" (process " + child.pid() + "; log " + log + ")");
            if (noWait) {
                return 0;
            }
            long until = System.currentTimeMillis() + timeout * 1000L;
            while (System.currentTimeMillis() < until) {
                if (!child.isAlive()) {
                    System.out.println("The preview process ended; see " + log);
                    tail(log);
                    return 1;
                }
                var e = PreviewRegistry.find(root, d.name());
                if (e.isPresent() && List.of("READY", "READY_WITH_ERRORS", "FAILED").contains(e.get().state())) {
                    print(LivePreviewStatus.of(e.get()));
                    return "FAILED".equals(e.get().state()) ? 1 : 0;
                }
                Thread.sleep(1000);
            }
            System.out.println("Still starting after " + timeout + " s; 'dogsbay-xml live-preview status' to follow it.");
            return 3;
        }

        private static void tail(Path log) {
            try {
                List<String> lines = Files.readAllLines(log);
                lines.subList(Math.max(0, lines.size() - 20), lines.size()).forEach(l -> System.out.println("  " + l));
            } catch (java.io.IOException ignore) {
                // nothing to show
            }
        }
    }

    /**
     * This JVM, its options and classpath, running the dogsbay-xml command line, in a session of its own where
     * {@code setsid} is available: a Ctrl+C in the terminal that started it (or a harness ending that terminal's
     * processes) must not reach the preview or its dev server.
     */
    static List<String> javaCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        List<String> cmd = new ArrayList<>();
        for (String setsid : List.of("/usr/bin/setsid", "/bin/setsid")) {
            if (!windows && Files.isExecutable(Path.of(setsid))) {
                cmd.add(setsid);
                break;
            }
        }
        cmd.add(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString());
        cmd.addAll(jvmOptions(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(DogsBayCli.class.getName());
        return cmd;
    }

    /** This JVM's options for a child, less debuggers and agents (a debugger's port would be taken twice). */
    static List<String> jvmOptions(List<String> given) {
        return given.stream().filter(a -> !(a.startsWith("-agentlib") || a.startsWith("-agentpath")
                || a.startsWith("-javaagent") || a.equals("-Xdebug") || a.startsWith("-Xrunjdwp"))).toList();
    }

    private static java.io.File nullDevice() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return new java.io.File(windows ? "NUL" : "/dev/null");
    }

    @Command(name = "status", description = "The running previews of the project, whoever runs them")
    static class Status extends Target implements Callable<Integer> {
        @Option(names = "--all-projects", description = "Every running preview on this machine")
        boolean allProjects;

        @Override
        public Integer call() throws Exception {
            List<LivePreviewStatus> all = new ArrayList<>();
            if (allProjects) {
                PreviewRegistry.list(null).forEach(e -> all.add(LivePreviewStatus.of(e)));
            } else {
                all.addAll(PreviewCommands.status(root(), deliverable).stream()
                        .filter(s -> s.pid() > 0).toList());
            }
            if (all.isEmpty()) {
                System.out.println("No live preview is running.");
                return 0;
            }
            all.forEach(LivePreviewCmd::print);
            return 0;
        }
    }

    @Command(name = "stop", description = "Stop a live preview, whoever runs it")
    static class Stop extends Target implements Callable<Integer> {
        @Option(names = "--all", description = "Every preview of the project")
        boolean all;

        @Override
        public Integer call() throws Exception {
            Path root = root();
            List<String> running = PreviewCommands.status(root, null).stream().filter(s -> s.pid() > 0)
                    .map(LivePreviewStatus::deliverable).toList();
            List<String> names = all ? running : deliverable == null ? running
                    : running.stream().filter(deliverable::equals).toList();
            if (names.isEmpty()) {
                System.out.println("No live preview is running" + (deliverable == null ? "." : " for " + deliverable + "."));
                return 0;
            }
            if (!all && names.size() > 1) {
                System.out.println("Several previews are running; name one with -d, or use --all: " + names);
                return 2;
            }
            int failed = 0;
            for (String name : names) {
                try {
                    PreviewCommands.stop(root, name, preview -> preview.stop(true));
                    System.out.println("Stopped: " + name);
                } catch (CommandException e) {
                    System.out.println("Not stopped: " + name + " — " + e.getMessage());
                    failed++;
                }
            }
            return failed == 0 ? 0 : 1;
        }
    }
}
