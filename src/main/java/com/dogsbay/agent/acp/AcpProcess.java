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

    private final Process process;

    private AcpProcess(Process process) {
        this.process = process;
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
        Thread.ofVirtual().name("acp-stderr").start(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderr.accept(line);
                }
            } catch (IOException ignore) {
                // process ended
            }
        });
        return new AcpProcess(p);
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
