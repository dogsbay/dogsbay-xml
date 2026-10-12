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

import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.Main;
import com.dogsbay.dogsbayaieditor.commands.UiDriving;
import com.dogsbay.dogsbayaieditor.terminal.TerminalSettings;
import com.dogsbay.dogsbayaieditor.properties.StartupOverrides;
import com.dogsbay.xml.properties.SettingsHome;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Open the editor window, in a state the caller chose.
 *
 * <p>The editor's own entry point takes a file and nothing else, and it stays that way. This is where startup options live,
 * so the project has one argument parser rather than two that drift apart.
 *
 * <p>What it is for: starting the editor somewhere other than the user's own configuration, and in a known state. A recording
 * that has to look the same every time, a test, a support session reproducing a fault without disturbing anything. Without
 * {@code --settings} the previous way to get a clean profile was to redirect the home directory for the whole process, which
 * also moved the terminal, the agent configuration and git.
 */
@Command(name = "gui", description = "Open the editor window, optionally with its own settings and a project")
class GuiCmd implements Callable<Integer> {

	/** How the window is started, so a test can drive the options without a display. */
	interface Launcher {
		void launch(String file) throws Exception;
	}

	@Option(names = "--settings", paramLabel = "DIR",
			description = "Keep this run's settings here instead of ~/.dogsbay. Created when missing.")
	private Path settings;

	@Option(names = "--project", paramLabel = "DIR", description = "Open this folder as the project.")
	private Path project;

	@Option(names = "--no-welcome", description = "Do not show the welcome screen.")
	private boolean noWelcome;

	@Option(names = "--no-session-restore", description = "Do not reopen the files from the previous session.")
	private boolean noSessionRestore;

	@Option(names = "--window", paramLabel = "WxH",
			description = "Open the window this size, for example 1920x1080, rather than maximised or as last left.")
	private String window;

	@Option(names = "--title", paramLabel = "TEXT",
			description = "Show this in the title bar instead of the usual title, which names the open file.")
	private String title;

	@Option(names = "--terminal-font", paramLabel = "[FAMILY,]SIZE",
			description = "Show the terminal in this font, for example 18 or 'DejaVu Sans Mono,18'.")
	private String terminalFont;

	@Option(names = "--allow-driving",
			description = "Let a caller run menu commands and terminal commands over the integration server. Off by"
					+ " default: neither can be kept inside the open project.")
	private boolean allowDriving;

	@Option(names = "--no-change-marks",
			description = "Let an agent's edits land as plain XML, with no review markup in the document.")
	private boolean noChangeMarks;

	@Parameters(index = "0", arity = "0..1", description = "File to open.")
	private Path file;

	/**
	 * Whether a window was started, so the command line knows not to end the process on the way out. Every other
	 * subcommand finishes its work before returning; this one leaves a window running behind it.
	 */
	private static volatile boolean startedWindow = false;

	/** Whether this process has started an editor window that must outlive the command. */
	public static boolean startedWindow() {
		return startedWindow;
	}

	/** Forget that a window was started. For tests. */
	static void forgetWindow() {
		startedWindow = false;
	}

	/**
	 * Say so when a size cannot be had, rather than letting the window come up a different shape in silence.
	 *
	 * <p>The stored size is read back clamped to the screen, so asking for more than the display has yields the display's
	 * size. That is the right behaviour for a window and the wrong thing to be quiet about, since the point of asking is
	 * usually that a recording has to come out the same shape every time.
	 */
	private static void warnIfLargerThanTheScreen(Dimension size) {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
		try {
			Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
			if (size.width > screen.width || size.height > screen.height) {
				System.err.println("This display is " + screen.width + "x" + screen.height + ", so the window will be"
						+ " that size rather than " + size.width + "x" + size.height + ".");
			}
		} catch (RuntimeException e) {
			// no display to ask, which the window will discover for itself
		}
	}

	/**
	 * A font written as a size, or a family and a size separated by a comma. Returns the family (null when not given) and
	 * the size as text, or null when it is neither.
	 */
	static String[] parseFont(String text) {
		int comma = text.lastIndexOf(',');
		String family = comma < 0 ? null : text.substring(0, comma).trim();
		String size = (comma < 0 ? text : text.substring(comma + 1)).trim();
		if (comma == 0 || (family != null && family.isEmpty())) {
			return null;
		}
		try {
			return Integer.parseInt(size) > 0 ? new String[] { family, size } : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * A size written as width by height, or null when it is not one.
	 *
	 * <p>Both have to be positive: a window of zero width cannot be shown, and a negative number here would be stored and
	 * puzzled over later rather than refused now.
	 */
	static Dimension parseSize(String text) {
		int cross = text.indexOf('x');
		if (cross <= 0 || cross == text.length() - 1) {
			return null;
		}
		try {
			int width = Integer.parseInt(text.substring(0, cross).trim());
			int height = Integer.parseInt(text.substring(cross + 1).trim());
			return width > 0 && height > 0 ? new Dimension(width, height) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Replaced in tests; the window is not something a test can ask for. */
	private Launcher launcher = f -> Main.main(f == null ? new String[0] : new String[] { f });

	void setLauncher(Launcher launcher) {
		this.launcher = launcher;
	}

	@Override
	public Integer call() throws Exception {
		if (project != null && !Files.isDirectory(project)) {
			System.err.println("Not a folder: " + project + " (--project takes a folder)");
			return 1;
		}
		if (file != null && !Files.isRegularFile(file)) {
			System.err.println("No such file: " + file);
			return 1;
		}
		Dimension size = null;
		if (window != null) {
			size = parseSize(window);
			if (size == null) {
				System.err.println("Not a window size: " + window + " (--window takes WxH, for example 1920x1080)");
				return 1;
			}
		}
		String[] font = null;
		if (terminalFont != null) {
			font = parseFont(terminalFont);
			if (font == null) {
				System.err.println("Not a font: " + terminalFont
						+ " (--terminal-font takes a size, or a family and a size: 'DejaVu Sans Mono,18')");
				return 1;
			}
		}
		boolean configured = settings != null || project != null || noWelcome || noSessionRestore || noChangeMarks
				|| size != null || title != null || font != null || allowDriving;
		if (size != null) {
			warnIfLargerThanTheScreen(size);
		}
		if (settings == null) {
			// a second run in one process must not inherit the first one's folder
			SettingsHome.useDefault();
		}
		if (settings != null) {
			// before anything reads a setting, which is the whole reason this option cannot be a preference
			File dir = settings.toAbsolutePath().toFile();
			if (!dir.exists() && !dir.mkdirs()) {
				System.err.println("Could not make the settings folder: " + dir);
				return 1;
			}
			if (!dir.isDirectory()) {
				System.err.println("Not a folder: " + dir + " (--settings takes a folder)");
				return 1;
			}
			SettingsHome.setRoot(dir.toString());
		}
		StartupOverrides.setPending(new StartupOverrides(
				project == null ? null : project.toAbsolutePath().toString(),
				noWelcome ? Boolean.FALSE : null,
				noSessionRestore ? Boolean.FALSE : null,
				noChangeMarks ? Boolean.FALSE : null,
				size,
				// kept only when this run has a settings folder of its own; otherwise a flag meant for one run would
				// stay in the user's own settings for every launch afterwards
				settings != null));
		// A configured run gets a window of its own. Otherwise the file would be handed to an editor already running,
		// which would open it under the user's settings and silently drop everything this run was told.
		// neither of these is a setting, so they are not written anywhere and last for this run by their nature
		DogsBayAIEditor.setTitleOverride(title);
		if (font != null) {
			TerminalSettings.setFont(font[0], Integer.parseInt(font[1]));
		}
		UiDriving.allow(allowDriving);
		Main.setOwnWindow(configured);
		startedWindow = true;
		launcher.launch(file == null ? null : file.toAbsolutePath().toString());
		return 0;
	}
}
