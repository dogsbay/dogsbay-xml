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

package com.dogsbay.dogsbayaieditor;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Vector;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;

/**
 * Action to open a project folder in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class OpenFolderAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;

    /**
     * Creates an action to open a project folder.
     *
     * @param parent the main editor window
     */
    public OpenFolderAction(DogsBayAIEditor parent) {
        super("Open Project Folder...");

        putValue(MNEMONIC_KEY, new Integer('P'));
        putValue(SMALL_ICON, DogsBayImageLoader.get().getImage(
            "com/dogsbay/dogsbayaieditor/icons/FileExplorerIcon.gif"));
        putValue(SHORT_DESCRIPTION, "Open project folder in File Explorer");

        this.parent = parent;
    }

    /**
     * Opens a directory chooser and updates the file explorer.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (DEBUG) {
            System.out.println("OpenFolderAction: Opening directory chooser");
        }

        JFileChooser chooser = FileUtilities.getDirectoryChooser();

        // Set current directory to last opened folder if available
        String lastFolder = parent.getProperties().getLastOpenedFolder();
        if (lastFolder != null && !lastFolder.isEmpty()) {
            File lastDir = new File(lastFolder);
            if (lastDir.exists() && lastDir.isDirectory()) {
                chooser.setCurrentDirectory(lastDir);
            }
        }

        int result = chooser.showOpenDialog(parent);

        if (result == JFileChooser.APPROVE_OPTION) {
            File directory = chooser.getSelectedFile();
            if (directory != null && directory.isDirectory()) {
                openFolder(directory);
            }
        }
    }

    /**
     * Open {@code directory} as a project: set the explorer root, refresh Git,
     * register/find the project, run full auto-config (DITA type/root map/
     * framework, materializing a {@code .dogsbay/config.xml} if absent), and make
     * it the current project. Reused by "Open Sample Project" and any other
     * programmatic open. Must be called on the EDT.
     *
     * @param directory the project folder to open
     */
    public void openFolder(File directory) {
        if (directory != null && directory.isDirectory()) {
                if (DEBUG) {
                    System.out.println("OpenFolderAction: Selected " + directory.getAbsolutePath());
                }

                // Update file explorer
                parent.getFileExplorer().setRootDirectory(directory);

                // Explicitly refresh Git panel to detect repository in new folder
                if (parent.getGitPanel() != null) {
                    parent.getGitPanel().refreshRepository();
                }

                // Switch to file explorer tab
                parent.switchToFileExplorerTab();

                // Create or find project for this folder
                String folderPath = directory.getAbsolutePath();
                ProjectProperties project = findOrCreateProject(folderPath, directory.getName());

                // Explicit user open: full auto-config — load .dogsbay/config.xml or
                // detect (type/root map/framework), and materialize a config when
                // there's none. Detection (a bounded folder scan) and the write
                // happen here, on the user-initiated path — never on plain project
                // switches/restores (which use the cheap syncFromConfig).
                com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.Result auto =
                        com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.configure(
                                project, parent.getProperties());
                if (auto.changedAnything()) {
                    StringBuilder msg = new StringBuilder(
                            auto.fromConfig() ? "DITA project — loaded .dogsbay/config.xml" : "DITA project detected");
                    if (auto.rootMap() != null) msg.append(" — root map ").append(auto.rootMap());
                    if (auto.framework() != null) msg.append(", framework ").append(auto.framework());
                    if (auto.wroteConfig()) msg.append(" (saved .dogsbay/config.xml)");
                    parent.setStatus(msg.toString());
                }

                // Update project switcher to show this project (cheap config sync).
                if (parent.getProjectSwitcher() != null) {
                    parent.getProjectSwitcher().setCurrentProject(project);
                }

                // Select the project in the project panel as well
                if (parent.getProjectPanel() != null) {
                    parent.getProjectPanel().selectProject(project.getName());
                }

                // Save to preferences. lastProjectName is what the startup restore
                // matches on to re-sync this project from .dogsbay/config.xml (type /
                // root map / framework) — without it, a reopened folder loses its DITA
                // context on the next launch.
                parent.getProperties().setLastOpenedFolder(folderPath);
                parent.getProperties().setLastOpenedProject(folderPath);
                parent.getProperties().setLastProjectName(project.getName());

                // Load the DITA deliverable context so the status-bar deliverable
                // selector populates from project.json (deferred a cycle, like the
                // session-restore path, so it can't perturb the layout pass).
                javax.swing.SwingUtilities.invokeLater(parent::refreshDeliverableContext);
        }
    }

    /**
     * Find existing project for folder or create a new one.
     *
     * @param folderPath the absolute path to the folder
     * @param folderName the name of the folder
     * @return the ProjectProperties for this folder
     */
    private ProjectProperties findOrCreateProject(String folderPath, String folderName) {
        // Check if a project already exists for this folder
        Vector<ProjectProperties> projects = parent.getProperties().getProjectProperties();
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties existingProject = projects.elementAt(i);
            String existingPath = existingProject.getFolderPath();
            if (existingPath != null && existingPath.equals(folderPath)) {
                if (DEBUG) {
                    System.out.println("OpenFolderAction: Found existing project for folder");
                }
                return existingProject;
            }
        }

        // No existing project found - create a new one
        if (DEBUG) {
            System.out.println("OpenFolderAction: Creating new project for folder: " + folderName);
        }

        ProjectProperties newProject = new ProjectProperties(folderName);
        newProject.setFolderPath(folderPath);
        // The stored project, not the one just built: adding clones the element,
        // and the auto-configuration that follows this call has to land in the
        // settings document, not on a detached copy.
        ProjectProperties stored = parent.getProperties().addProjectProperties(newProject);

        // Update project panel if available
        if (parent.getProjectPanel() != null) {
            parent.getProjectPanel().setProjects(parent.getProperties().getProjectProperties(), false);
        }

        return stored;
    }
}
