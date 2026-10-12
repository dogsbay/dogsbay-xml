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
package com.xagent.tool.operations;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default bash execution using ProcessBuilder.
 */
public class DefaultBashOperations implements BashOperations {

	@Override
	public BashResult exec(
		String command,
		Path cwd,
		Duration timeout,
		Consumer<String> onOutput,
		Supplier<Boolean> isCancelled
	) {
		try {
			ProcessBuilder pb = new ProcessBuilder("bash", "-c", command)
				.directory(cwd.toFile())
				.redirectErrorStream(false);

			Process process = pb.start();

			var stdoutBuilder = new StringBuilder();
			var stderrBuilder = new StringBuilder();

			// Read stdout in a thread
			Thread stdoutThread = Thread.ofVirtual().start(() -> {
				try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						stdoutBuilder.append(line).append("\n");
						if (onOutput != null) {
							onOutput.accept(line + "\n");
						}
					}
				} catch (Exception ignored) {
				}
			});

			// Read stderr in a thread
			Thread stderrThread = Thread.ofVirtual().start(() -> {
				try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						stderrBuilder.append(line).append("\n");
					}
				} catch (Exception ignored) {
				}
			});

			// Wait with timeout, checking for cancellation
			long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
			boolean finished = false;

			while (!finished && System.currentTimeMillis() < deadlineMs) {
				if (isCancelled != null && isCancelled.get()) {
					process.destroyForcibly();
					stdoutThread.join(1000);
					stderrThread.join(1000);
					return new BashResult(-1, stdoutBuilder.toString(), "Cancelled");
				}
				finished = process.waitFor(100, TimeUnit.MILLISECONDS);
			}

			if (!finished) {
				process.destroyForcibly();
				stdoutThread.join(1000);
				stderrThread.join(1000);
				return new BashResult(-1, stdoutBuilder.toString(), "Timed out after " + timeout.toSeconds() + "s");
			}

			stdoutThread.join(5000);
			stderrThread.join(5000);

			return new BashResult(
				process.exitValue(),
				stdoutBuilder.toString(),
				stderrBuilder.toString()
			);
		} catch (Exception e) {
			return new BashResult(-1, "", "Failed to execute: " + e.getMessage());
		}
	}
}
