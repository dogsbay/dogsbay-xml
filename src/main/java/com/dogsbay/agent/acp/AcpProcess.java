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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A spawned agent: its process, and its stderr drained to a consumer so
 * diagnostics reach the output panel instead of a full pipe buffer.
 */
public final class AcpProcess implements AutoCloseable {

    /** How many of the agent's last stderr lines are kept, for a failure message. */
    static final int RECENT_LINES = 20;

    private final Process process;
    private final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
    private volatile Thread stderrReader;

    private AcpProcess(Process process) {
        this.process = process;
    }

    private void remember(String line) {
        synchronized (recent) {
            recent.addLast(line);
            while (recent.size() > RECENT_LINES) {
                recent.removeFirst();
            }
        }
    }

    /**
     * The agent's last stderr lines. When the process ends within {@code wait},
     * its remaining output is read first, so an agent that printed why it quit and
     * exited is reported in full.
     */
    public List<String> recentStderr(java.time.Duration wait) {
        try {
            if (process.waitFor(wait.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Thread reader = stderrReader;
                if (reader != null) {
                    reader.join(wait);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    /**
     * Let the agent end by itself: close its input, and only stop it if it is still
     * running after {@code timeout}. For a start that was cancelled before the agent
     * answered, when {@code npx} or {@code uvx} may still be installing it: killing
     * the launcher mid-install leaves an incomplete copy in its cache that every later
     * start then fails to run. Once installed, an adapter sees its input closed and exits.
     *
     * <p>
     * The watch runs on a daemon thread, so it ends if the editor exits first. A
     * released process is then left to end by itself, which an installing launcher
     * and an adapter reading its closed input both do; killing it on exit would cut
     * the install short again.
     */
    public void release(java.time.Duration timeout) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignore) {
            // already closed
        }
        Thread.ofVirtual().name("acp-release").start(() -> {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<ProcessHandle> children = List.of();
            try {
                // Note the launcher's children while it runs: once it exits they are
                // re-parented and no longer its descendants.
                while (process.isAlive() && System.nanoTime() < deadline) {
                    children = process.toHandle().descendants().toList();
                    process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                close();
            } else {
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
            }
        });
    }

    /**
     * Launch {@code command} with {@code cwd}. On Windows, commands that are
     * npm shims (npx, and anything resolved through {@code .cmd}) must go
     * through {@code cmd /c}.
     */
    public static AcpProcess start(List<String> command, Path cwd, Consumer<String> stderr) throws IOException {
        return start(command, java.util.Map.of(), cwd, stderr);
    }

    /** As {@link #start(List, Path, Consumer)} with extra environment for the agent. */
    public static AcpProcess start(List<String> command, java.util.Map<String, String> env, Path cwd,
            Consumer<String> stderr) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(platformCommand(command));
        pb.environment().putAll(env);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        pb.redirectErrorStream(false);
        Process p = pb.start();
        AcpProcess acp = new AcpProcess(p);
        acp.stderrReader = Thread.ofVirtual().name("acp-stderr").start(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    acp.remember(line);
                    stderr.accept(line);
                }
            } catch (IOException ignore) {
                // process ended
            }
        });
        return acp;
    }

    static List<String> platformCommand(List<String> command) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows || command.isEmpty()) {
            return command;
        }
        String exe = command.get(0);
        if (exe.endsWith(".exe")) {
            return command;
        }
        List<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.addAll(command);
        return wrapped;
    }

    public Process process() {
        return process;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * End the agent and everything it spawned. {@code npx} and {@code uvx}
     * are launchers whose real agent is a child process; destroying only the
     * launcher would orphan the agent, still holding our pipes.
     */
    @Override
    public void close() {
        List<ProcessHandle> children = process.toHandle().descendants().toList();
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        for (ProcessHandle child : children) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }
}
