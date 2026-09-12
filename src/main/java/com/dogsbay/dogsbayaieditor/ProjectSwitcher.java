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

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * A compact project switcher component similar to Obsidian's vault switcher.
 * Displays the current project name and allows switching between projects
 * via a popup menu.
 */
public class ProjectSwitcher extends StatusSegment {
    private ConfigurationProperties properties;
    private DogsBayAIEditor parent;
    private ProjectProperties currentProject;

    public ProjectSwitcher(DogsBayAIEditor parent, ConfigurationProperties properties) {
        super("📁", "Current project — click to switch or manage");
        this.parent = parent;
        this.properties = properties;
        setOnClick(this::showProjectMenu);
        updateCurrentProject();
    }

    private void showProjectMenu() {
        JPopupMenu menu = new JPopupMenu();

        Vector<ProjectProperties> projects = properties.getProjectProperties();

        // Add all projects to the menu
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties project = projects.elementAt(i);
            JMenuItem item = new JMenuItem(project.getName());

            // Mark current project with a checkmark
            if (currentProject != null && project.getName().equals(currentProject.getName())) {
                item.setIcon(new CheckIcon());
            }

            item.addActionListener(e -> switchToProject(project));
            menu.add(item);
        }

        // Add separator and "Manage Projects..." option
        if (projects.size() > 0) {
            menu.addSeparator();
        }

        JMenuItem manageItem = new JMenuItem("Manage Projects...");
        manageItem.addActionListener(e -> showManageProjectsDialog());
        menu.add(manageItem);

        // Show the menu below the switcher
        menu.show(this, 0, getHeight());
    }

    private void switchToProject(ProjectProperties project) {
        currentProject = project;
        setProjectText(project.getName());

        // Save as last opened project
        properties.setLastProjectName(project.getName());

        // Switch to File Explorer tab and open project folder if available
        if (parent != null) {
            parent.switchToFileExplorerTab();

            // If project has a folder path, open it in the File Explorer
            String folderPath = project.getFolderPath();
            if (folderPath != null && !folderPath.isEmpty()) {
                java.io.File projectFolder = new java.io.File(folderPath);
                if (projectFolder.exists() && projectFolder.isDirectory()) {
                    // Get the File Explorer panel and set its root directory
                    if (parent.getFileExplorer() != null) {
                        parent.getFileExplorer().setRootDirectory(projectFolder);
                    }

                    // Explicitly refresh Git panel to detect repository in new folder
                    if (parent.getGitPanel() != null) {
                        parent.getGitPanel().refreshRepository();
                    }

                    // Add to recent projects list (for File → Open Recent menu)
                    properties.setLastOpenedProject(folderPath);
                    properties.setLastOpenedFolder(folderPath);
                }
            }

            // Notify parent to switch project context
            if (parent.getProjectPanel() != null) {
                parent.getProjectPanel().selectProject(project.getName());
            }
        }
    }

    private void showManageProjectsDialog() {
        ManageProjectsDialog dialog = new ManageProjectsDialog(parent, properties);
        dialog.setVisible(true);

        // Reload the project panel tree to show any newly added/removed projects
        if (parent != null && parent.getProjectPanel() != null) {
            parent.getProjectPanel().setProjects(properties.getProjectProperties(), false);
        }

        // Note: Don't call updateCurrentProject() here because ManageProjectsDialog
        // already sets the correct project via switchToNewProject() when creating new
        // projects
    }

    /** Set the project name, or a muted "No Project" placeholder — matching the
     *  branch/deliverable segments' empty-state style for a consistent strip. */
    private void setProjectText(String name) {
        setValue(name, "No Project");
    }

    public void updateCurrentProject() {
        // Try to get the currently selected project from the project panel
        if (parent != null && parent.getProjectPanel() != null) {
            com.dogsbay.dogsbayaieditor.project.ProjectNode selectedProject = parent.getProjectPanel().getSelectedProject();

            if (selectedProject != null) {
                currentProject = (ProjectProperties) selectedProject.getProperties();
                setProjectText(currentProject.getName());
                return;
            }
        }

        // Otherwise, use the first project if available
        Vector<ProjectProperties> projects = properties.getProjectProperties();
        if (projects.size() > 0) {
            currentProject = projects.elementAt(0);
            setProjectText(currentProject.getName());
        } else {
            currentProject = null;
            setProjectText(null);
        }
    }

    /**
     * Set the current project directly
     */
    public void setCurrentProject(ProjectProperties project) {
        if (project != null) {
            currentProject = project;
            setProjectText(project.getName());
            // Cheap, side-effect-free sync from any committed .dogsbay/config.xml,
            // so the Map Explorer/publishing pick up type/root-map/framework
            // whether the project was opened, switched to, or restored at startup.
            // Full detection + materialization happens only on explicit open
            // (OpenFolderAction); this path does no filesystem scan and no writes.
            com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.syncFromConfig(project);
        }
    }

    /**
     * Get the current project
     */
    public ProjectProperties getCurrentProject() {
        return currentProject;
    }

    /**
     * Simple checkmark icon for the selected project
     */
    private static class CheckIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0, 120, 215));
            g2d.setStroke(new BasicStroke(2));

            // Draw checkmark
            int[] xPoints = { x + 3, x + 6, x + 11 };
            int[] yPoints = { y + 7, y + 10, y + 4 };
            g2d.drawPolyline(xPoints, yPoints, 3);

            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
