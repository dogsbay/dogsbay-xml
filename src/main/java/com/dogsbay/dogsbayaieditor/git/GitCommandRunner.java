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

package com.dogsbay.dogsbayaieditor.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility for running system git commands via ProcessBuilder.
 * Used for network operations (pull, push, fetch) so that the user's
 * existing authentication setup (SSH agent, credential helpers, PATs)
 * is respected.
 */
public class GitCommandRunner {

    private static final long TIMEOUT_SECONDS = 60;

    /**
     * Result of a git command execution.
     */
    public static class GitCommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public GitCommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        /**
         * Returns a user-friendly message: stdout on success, stderr on failure.
         */
        public String getMessage() {
            if (isSuccess()) {
                String out = stdout.trim();
                return out.isEmpty() ? "Completed successfully." : out;
            } else {
                String err = stderr.trim();
                return err.isEmpty() ? "Command failed with exit code " + exitCode : err;
            }
        }

        /**
         * Returns true if the error looks like an SSH authentication failure.
         */
        public boolean isSshAuthError() {
            String combined = (stderr + " " + stdout).toLowerCase();
            return combined.contains("permission denied")
                    || combined.contains("publickey")
                    || combined.contains("host key verification failed")
                    || combined.contains("could not read from remote repository");
        }

        /**
         * Returns the error message with an SSH auth hint appended if applicable.
         */
        public String getMessageWithAuthHint(String remoteUrl) {
            String msg = getMessage();
            if (!isSuccess() && isSshAuthError()) {
                String host = extractSshHost(remoteUrl);
                msg += "\n\nSSH authentication may not be configured for this session.";
                if (host != null) {
                    msg += "\nTry running this in a terminal first:\n  ssh -T git@" + host;
                }
            }
            return msg;
        }
    }

    /**
     * Extracts the SSH host from a git remote URL.
     * e.g. "git@github-dogsbay:org/repo.git" -> "github-dogsbay"
     */
    static String extractSshHost(String url) {
        if (url == null) return null;
        // git@host:path format
        if (url.contains("@") && url.contains(":") && !url.contains("://")) {
            int at = url.indexOf('@');
            int colon = url.indexOf(':', at);
            if (colon > at) {
                return url.substring(at + 1, colon);
            }
        }
        // ssh://git@host/path format
        if (url.startsWith("ssh://")) {
            String afterScheme = url.substring(6);
            int at = afterScheme.indexOf('@');
            if (at >= 0) {
                String afterAt = afterScheme.substring(at + 1);
                int slash = afterAt.indexOf('/');
                int colon = afterAt.indexOf(':');
                int end = afterAt.length();
                if (slash >= 0) end = Math.min(end, slash);
                if (colon >= 0) end = Math.min(end, colon);
                return afterAt.substring(0, end);
            }
        }
        return null;
    }

    /**
     * Runs a system git command in the given working directory.
     *
     * @param workingDir the git repository working directory
     * @param args       git subcommand and arguments (e.g. "pull", "push", "fetch")
     * @return the command result with exit code, stdout, and stderr
     * @throws Exception if the process cannot be started or times out
     */
    public static GitCommandResult runGitCommand(File workingDir, String... args) throws Exception {
        return runGitCommand(workingDir, TIMEOUT_SECONDS, args);
    }

    /**
     * Runs a system git command in the given working directory with a custom timeout.
     *
     * @param workingDir     the git repository working directory
     * @param timeoutSeconds timeout in seconds
     * @param args           git subcommand and arguments
     * @return the command result with exit code, stdout, and stderr
     * @throws Exception if the process cannot be started or times out
     */
    public static GitCommandResult runGitCommand(File workingDir, long timeoutSeconds, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        // Prevent git from hanging on interactive auth prompts
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = pb.start();

        // Read stdout and stderr in parallel to avoid blocking
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() > 0) stdout.append("\n");
                    stdout.append(line);
                }
            } catch (Exception e) {
                // ignore
            }
        });

        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() > 0) stderr.append("\n");
                    stderr.append(line);
                }
            } catch (Exception e) {
                // ignore
            }
        });

        stdoutReader.start();
        stderrReader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Git command timed out after " + TIMEOUT_SECONDS + " seconds.");
        }

        stdoutReader.join(5000);
        stderrReader.join(5000);

        return new GitCommandResult(process.exitValue(), stdout.toString(), stderr.toString());
    }
}
