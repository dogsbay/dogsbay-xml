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
package com.dogsbay.xml.properties;

import java.io.File;

/**
 * Where this run keeps its settings: {@code ~/.dogsbay/} unless something says otherwise.
 *
 * <p>This used to be a {@code static final} field on {@code Main} computed from {@code user.home}, which fixed it when the
 * class loaded and so before any command-line option could be read. A run that wants settings of its own — a recording, a
 * test, a support session reproducing a fault without touching the user's own configuration — needs to say so and be heard,
 * hence a value resolved on each call rather than a constant.
 *
 * <p>The alternative, setting {@code user.home} before the editor loads, was rejected: it works only while nothing touches
 * the settings earlier, and it redirects the home directory for everything else in the process too, the terminal and the
 * agent configuration and git included.
 *
 * <p>Set the root before the editor starts. Changing it afterwards moves where settings are written without moving what is
 * already loaded, which is nobody's intention.
 */
public final class SettingsHome {

	/** The folder name under the user's home, when nothing overrides it. */
	public static final String DEFAULT_NAME = ".dogsbay";

	private static volatile String root = null;

	private SettingsHome() {
	}

	/**
	 * The settings folder for this run, ending in a file separator so a name can be appended directly.
	 *
	 * @return the folder, overridden or the default under the user's home.
	 */
	public static String root() {
		String chosen = root;
		if (chosen != null) {
			return chosen;
		}
		return System.getProperty("user.home") + File.separator + DEFAULT_NAME + File.separator;
	}

	/**
	 * Keep this run's settings somewhere else. Call before the editor starts.
	 *
	 * @param dir the folder to use; it need not exist yet. A trailing separator is added when missing.
	 */
	public static void setRoot(String dir) {
		if (dir == null || dir.isBlank()) {
			throw new IllegalArgumentException("a settings folder is needed");
		}
		// not trimmed: a trailing space is a legal folder name on POSIX, and trimming would store a different folder
		// from the one the caller created and checked
		root = dir.endsWith(File.separator) ? dir : dir + File.separator;
	}

	/**
	 * The settings folder as a path.
	 *
	 * <p>Everything the editor keeps per user belongs under here, not only the settings file: the note saying an editor is
	 * running and how to reach it, the register of running previews, the publishing engine, installed agents. A run given a
	 * folder of its own is asking not to touch the user's, and each of those reaching past this would touch it anyway.
	 *
	 * @return the folder.
	 */
	public static java.nio.file.Path dir() {
		return java.nio.file.Path.of(root());
	}

	/** Back to the default under the user's home. For tests, and for a run that changes its mind before starting. */
	public static void useDefault() {
		root = null;
	}

	/** Whether something has chosen a folder other than the default. */
	public static boolean isOverridden() {
		return root != null;
	}
}
