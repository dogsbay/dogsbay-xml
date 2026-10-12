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

package com.dogsbay.dogsbayaieditor.project;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the Project properties and can have many documents and folders.
 *
 * @version $Revision: 1.1 $, $Date: 2004/03/25 18:54:53 $
 * @author Dogsbay
 */
public class ProjectProperties extends FolderProperties {
	public static final String PROJECT_PROPERTIES = "project";
	public static final String FOLDER_PATH = "folder-path";

	public static final String PROJECT_TYPE = "project-type";
	public static final String GITHUB_REPO = "github-repo";
	public static final String DEFAULT_ROOT_MAP = "default-root-map";
	public static final String DITA_OT_PATH = "dita-ot-path";
	public static final String FRAMEWORK_NAME = "framework-name";
	public static final String ACTIVE_DELIVERABLE_FILE = "active-deliverable-file";
	public static final String ACTIVE_DELIVERABLE_NAME = "active-deliverable-name";

	public static final String TYPE_NONE = "None";
	public static final String TYPE_DITA = "DITA";
	public static final String TYPE_DOCBOOK = "DocBook";

	/**
	 * Creates the Configuration Document wrapper.
	 * It reads in the root element and if it has to, it creates the property file.
	 *
	 * @param the url to the XML document.
	 */
	public ProjectProperties(XElement element) {
		super(element);
	}

	/**
	 * Creates the Configuration Document wrapper.
	 * It reads in the root element and if it has to, it creates the property file.
	 *
	 * @param props the properties.
	 */
	public ProjectProperties(Properties props) {
		super(props);
	}

	/**
	 * Creates the Configuration Document wrapper.
	 * It reads in the root element and if it has to, it creates the property file.
	 *
	 * @param the url to the XML document.
	 */
	public ProjectProperties(String name) {
		super(new XElement(PROJECT_PROPERTIES));

		setName(name);
	}

	/**
	 * Sets the folder path for the project.
	 *
	 * @param path the folder path for the project.
	 */
	public void setFolderPath(String path) {
		set(FOLDER_PATH, path);
	}

	/**
	 * Get the folder path for the project.
	 *
	 * @return the project folder path.
	 */
	public String getFolderPath() {
		return getText(FOLDER_PATH);
	}

	/**
	 * Sets the project type.
	 *
	 * @param type the project type (e.g. DITA, DocBook).
	 */
	public void setProjectType(String type) {
		set(PROJECT_TYPE, type);
	}

	/**
	 * Get the project type.
	 *
	 * @return the project type.
	 */
	public String getProjectType() {
		String type = getText(PROJECT_TYPE);
		return type != null ? type : TYPE_NONE;
	}

	/**
	 * Sets the GitHub repository URL.
	 *
	 * @param repo the GitHub repository URL.
	 */
	public void setGithubRepo(String repo) {
		set(GITHUB_REPO, repo);
	}

	/**
	 * Get the GitHub repository URL.
	 *
	 * @return the GitHub repository URL.
	 */
	public String getGithubRepo() {
		return getText(GITHUB_REPO);
	}

	/**
	 * Sets the default root map (for DITA projects).
	 *
	 * @param map the default root map file path.
	 */
	public void setDefaultRootMap(String map) {
		set(DEFAULT_ROOT_MAP, map);
	}

	/**
	 * Get the default root map.
	 *
	 * @return the default root map file path.
	 */
	public String getDefaultRootMap() {
		return getText(DEFAULT_ROOT_MAP);
	}

	/**
	 * Sets the project-specific DITA-OT home directory path.
	 * When set, this overrides any framework-level DITA-OT path.
	 *
	 * @param path the absolute path to the DITA-OT installation directory.
	 */
	public void setDitaOtPath(String path) {
		set(DITA_OT_PATH, path);
	}

	/**
	 * Gets the project-specific DITA-OT home directory path.
	 *
	 * @return the absolute path, or null if not set.
	 */
	public String getDitaOtPath() {
		return getText(DITA_OT_PATH);
	}

	/**
	 * Sets the framework name associated with this project.
	 *
	 * @param name the framework name.
	 */
	public void setFrameworkName(String name) {
		set(FRAMEWORK_NAME, name);
	}

	/**
	 * Gets the framework name associated with this project.
	 *
	 * @return the framework name, or null if not set.
	 */
	public String getFrameworkName() {
		return getText(FRAMEWORK_NAME);
	}

	/**
	 * Records the active deliverable as a {@code (project-file, name)} pointer —
	 * never a copy of deliverable data (definitions stay in the user's DITA-OT
	 * project files). The file is stored relative to the project root.
	 *
	 * @param relativeFile the deliverable's project file, relative to the root,
	 *                     or null to clear
	 * @param name         the deliverable name, or null to clear
	 */
	public void setActiveDeliverable(String relativeFile, String name) {
		set(ACTIVE_DELIVERABLE_FILE, relativeFile);
		set(ACTIVE_DELIVERABLE_NAME, name);
	}

	/** The active deliverable's project file, relative to the root, or null. */
	public String getActiveDeliverableFile() {
		return getText(ACTIVE_DELIVERABLE_FILE);
	}

	/** The active deliverable's name, or null if none recorded. */
	public String getActiveDeliverableName() {
		return getText(ACTIVE_DELIVERABLE_NAME);
	}

	private void test() {
	}
}
