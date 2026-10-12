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
package com.dogsbay.dogsbayaieditor.properties;

import java.awt.Dimension;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;

/**
 * Settings this run was told to use, from the command line, applied once the settings file has been read.
 *
 * <p>They are applied rather than merely consulted, so the editor behaves the same whether a setting came from the file or
 * the command line, and so nothing downstream has to know which. A run started with a settings folder of its own therefore
 * leaves that folder holding what it was asked for, which is what makes a recording or a test repeatable.
 *
 * <p>Only what was actually given is applied: an option left off the command line leaves the setting as the file has it.
 *
 * <p>Whether they last is the caller's to say. A run given a settings folder of its own is meant to be repeatable, so what
 * it was told is written there. A run borrowing the user's own settings is not: it marks them read-only before applying, so
 * a flag meant for one run cannot turn the welcome screen off for good, or leave the user wondering months later why their
 * session no longer reopens. The cost is that this run's own preference changes are not kept either, which is the honest
 * meaning of "for this run".
 */
public final class StartupOverrides {

	private static volatile StartupOverrides pending = null;

	private final String project;
	private final Boolean welcome;
	private final Boolean reopenSessionFiles;
	private final Boolean markChanges;
	private final Dimension window;
	private final boolean persist;

	/**
	 * @param project             a folder to open as the project, or null to leave the last one alone.
	 * @param welcome             whether to show the welcome screen, or null to leave the setting alone.
	 * @param reopenSessionFiles  whether to reopen the previous session's files, or null to leave the setting alone.
	 * @param markChanges         whether an agent's edits are marked in the document for review, or null to leave the
	 *                            setting alone. False leaves clean XML behind.
	 * @param persist             whether these may be written to the settings file. True only when the run has a settings
	 *                            folder of its own; otherwise they last for this run and no longer.
	 */
	public StartupOverrides(String project, Boolean welcome, Boolean reopenSessionFiles, Boolean markChanges,
			boolean persist) {
		this(project, welcome, reopenSessionFiles, markChanges, null, persist);
	}

	/**
	 * @param project             a folder to open as the project, or null to leave the last one alone.
	 * @param welcome             whether to show the welcome screen, or null to leave the setting alone.
	 * @param reopenSessionFiles  whether to reopen the previous session's files, or null to leave the setting alone.
	 * @param markChanges         whether an agent's edits are marked in the document for review, or null to leave the
	 *                            setting alone. False leaves clean XML behind.
	 * @param window              the window size, or null to leave it as the settings have it.
	 * @param persist             whether these may be written to the settings file. True only when the run has a settings
	 *                            folder of its own; otherwise they last for this run and no longer.
	 */
	public StartupOverrides(String project, Boolean welcome, Boolean reopenSessionFiles, Boolean markChanges,
			Dimension window, boolean persist) {
		this.project = project;
		this.welcome = welcome;
		this.reopenSessionFiles = reopenSessionFiles;
		this.markChanges = markChanges;
		this.window = window;
		this.persist = persist;
	}

	/** Nothing asked for; every setting stays as the file has it. */
	public static StartupOverrides none() {
		return new StartupOverrides(null, null, null, null, false);
	}

	/** Whether any setting was actually given. */
	public boolean isEmpty() {
		return project == null && welcome == null && reopenSessionFiles == null && markChanges == null
				&& window == null;
	}

	/** What the command line asked for, to be applied when the settings are read. Call before the editor starts. */
	public static void setPending(StartupOverrides overrides) {
		pending = overrides;
	}

	/** What is waiting to be applied, or null. */
	public static StartupOverrides pending() {
		return pending;
	}

	/** Forget what was asked for. For tests, and for a second startup in one process. */
	public static void clearPending() {
		pending = null;
	}

	/**
	 * Apply anything waiting to the settings just read. Safe to call when nothing is waiting, which is the usual case.
	 *
	 * @param properties the settings to change.
	 * @return whether anything was changed.
	 */
	public static boolean applyPending(ConfigurationProperties properties) {
		StartupOverrides waiting = pending;
		if (waiting == null || properties == null) {
			return false;
		}
		return waiting.applyTo(properties);
	}

	/**
	 * Apply what was given, and only that.
	 *
	 * @param properties the settings to change.
	 * @return whether anything was changed.
	 */
	public boolean applyTo(ConfigurationProperties properties) {
		if (properties == null || isEmpty()) {
			return false;
		}
		if (!persist) {
			// before anything is changed, so that neither this nor the project below can reach the user's settings file
			properties.setReadOnly(true);
		}
		if (welcome != null) {
			properties.setShowWelcomeOnStartup(welcome);
		}
		if (reopenSessionFiles != null) {
			properties.setReopenSessionFiles(reopenSessionFiles);
		}
		if (markChanges != null) {
			properties.setAgentProposalsEnabled(markChanges);
		}
		if (window != null) {
			// a size means a window that size, so maximising has to give way or the size would never be used
			properties.setDimension(window);
			properties.setWindowMaximised(false);
			// and a position of its own, rather than wherever the last run was left: with the size asked for
			// explicitly and the position inherited, the same command gave different geometry on different
			// machines, and under a window manager a different window state as well.
			properties.placeForSize(window);
		}
		if (project != null) {
			openProject(properties);
		}
		return true;
	}

	/** The folder to open as the project, or null. */
	public String project() {
		return project;
	}

	/** Whether the welcome screen was asked for or against, or null when it was not mentioned. */
	public Boolean welcome() {
		return welcome;
	}

	/** Whether reopening the previous session's files was asked for or against, or null when not mentioned. */
	public Boolean reopenSessionFiles() {
		return reopenSessionFiles;
	}

	/** Whether an agent's edits are marked in the document, or null when not mentioned. False means clean XML. */
	public Boolean markChanges() {
		return markChanges;
	}

	/**
	 * Make the folder the project the editor opens.
	 *
	 * <p>Not just a setting: the editor chooses what to open from its registered projects, matching one against the last
	 * opened folder, so a folder it has never heard of opens nothing at all. This registers it first when it is new, the
	 * way opening a folder in the editor does, and only then says it is the last one opened.
	 */
	private void openProject(ConfigurationProperties properties) {
		ProjectProperties chosen = null;
		for (Object known : properties.getProjectProperties()) { // a raw Vector, from settings older than generics
			if (known instanceof ProjectProperties registered && project.equals(registered.getFolderPath())) {
				chosen = registered;
				break;
			}
		}
		if (chosen == null) {
			ProjectProperties made = new ProjectProperties(new java.io.File(project).getName());
			made.setFolderPath(project);
			// the stored one, not the one just built: adding clones it, and what follows must land in the settings
			chosen = properties.addProjectProperties(made);
		}
		properties.rememberOpenProject(chosen, project);
	}

	/** The window size asked for, or null when it was not mentioned. */
	public Dimension window() {
		return window;
	}

	/** Whether these may be written to the settings file, rather than lasting for this run only. */
	public boolean persists() {
		return persist;
	}
}
