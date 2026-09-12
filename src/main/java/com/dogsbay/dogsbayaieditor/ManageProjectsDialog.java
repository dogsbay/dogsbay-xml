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
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Vector;

import com.dogsbay.dogsbayaieditor.git.GitCommandRunner;
import com.dogsbay.dogsbayaieditor.git.GitCommandRunner.GitCommandResult;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.framework.FrameworkProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Obsidian-style project management dialog.
 * Shows existing projects on the left and options to create/open projects on
 * the right.
 */
public class ManageProjectsDialog extends JDialog {
    private static final Color BACKGROUND_COLOR = UIManager.getColor("Panel.background");
    private static final Color SIDEBAR_COLOR = UIManager.getColor("List.background");
    private static final Color HOVER_COLOR = UIManager.getColor("List.selectionBackground");
    private static final Color SELECTED_COLOR = UIManager.getColor("List.selectionBackground");
    private static final Color TEXT_COLOR = UIManager.getColor("Label.foreground");
    private static final Color SUBTITLE_COLOR = UIManager.getColor("Label.disabledForeground");

    private DogsBayAIEditor parent;
    private ConfigurationProperties properties;
    private DefaultListModel<ProjectProperties> projectListModel;
    private JList<ProjectProperties> projectList;
    private ProjectProperties selectedProject;

    public ManageProjectsDialog(DogsBayAIEditor parent, ConfigurationProperties properties) {
        super(parent, "Manage Projects", true);
        this.parent = parent;
        this.properties = properties;

        initializeUI();
        loadProjects();

        setSize(900, 600);
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND_COLOR);

        // Create split pane with project list on left and options on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setBackground(BACKGROUND_COLOR);

        // Left side - Project list
        JPanel leftPanel = createProjectListPanel();
        splitPane.setLeftComponent(leftPanel);

        // Right side - Project options
        JPanel rightPanel = createOptionsPanel();
        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createProjectListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SIDEBAR_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Title
        JLabel titleLabel = new JLabel("Projects");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(0, 5, 10, 0));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Project list
        projectListModel = new DefaultListModel<>();
        projectList = new JList<>(projectListModel);
        projectList.setBackground(SIDEBAR_COLOR);
        projectList.setForeground(TEXT_COLOR);
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.setCellRenderer(new ProjectListCellRenderer());
        projectList.setBorder(new EmptyBorder(5, 5, 5, 5));

        projectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedProject = projectList.getSelectedValue();
            }
        });

        JScrollPane scrollPane = new JScrollPane(projectList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(SIDEBAR_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new CardLayout());
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Global Options Panel (default view)
        JPanel globalOptionsPanel = createGlobalOptionsPanel();
        panel.add(globalOptionsPanel, "GLOBAL");

        // Project Settings Panel (when a project is selected)
        ProjectSettingsPanel projectSettingsPanel = new ProjectSettingsPanel();
        panel.add(projectSettingsPanel, "PROJECT");

        // Switch view based on selection
        projectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CardLayout cl = (CardLayout) panel.getLayout();
                if (projectList.getSelectedValue() != null) {
                    projectSettingsPanel.setProject(projectList.getSelectedValue());
                    cl.show(panel, "PROJECT");
                } else {
                    cl.show(panel, "GLOBAL");
                }
            }
        });

        return panel;
    }

    private JPanel createGlobalOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // Add logo/title area
        JLabel logoLabel = new JLabel("DogsBay XML");
        logoLabel.setFont(logoLabel.getFont().deriveFont(Font.BOLD, 24f));
        logoLabel.setForeground(TEXT_COLOR);
        logoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(logoLabel);

        panel.add(Box.createVerticalStrut(10));

        JLabel versionLabel = new JLabel("Project Manager");
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 12f));
        versionLabel.setForeground(SUBTITLE_COLOR);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(versionLabel);

        panel.add(Box.createVerticalStrut(40));

        // Create new project option
        panel.add(createOptionButton(
                "Create new project",
                "Create a new XML project under a folder.",
                "Create",
                this::createNewProject));

        panel.add(Box.createVerticalStrut(20));

        // Open folder as project option
        panel.add(createOptionButton(
                "Open folder as project",
                "Choose an existing folder of XML/XSLT files.",
                "Open",
                this::openFolderAsProject));

        panel.add(Box.createVerticalStrut(20));

        // Clone from GitHub option
        panel.add(createOptionButton(
                "Clone from GitHub",
                "Clone a repository and open it as a project.",
                "Clone",
                this::cloneFromGitHub));

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel createOptionButton(String title, String description, String buttonText, Runnable action) {
        JPanel optionPanel = new JPanel(new BorderLayout(15, 0));
        optionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        optionPanel.setBackground(BACKGROUND_COLOR);
        optionPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        optionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Text panel
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(BACKGROUND_COLOR);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(titleLabel);

        textPanel.add(Box.createVerticalStrut(3));

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descLabel.setForeground(SUBTITLE_COLOR);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(descLabel);

        optionPanel.add(textPanel, BorderLayout.CENTER);

        // Button
        JButton button = new JButton(buttonText);
        button.setPreferredSize(new Dimension(100, 35));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        optionPanel.add(button, BorderLayout.EAST);

        return optionPanel;
    }

    private void loadProjects() {
        projectListModel.clear();
        Vector<ProjectProperties> projects = properties.getProjectProperties();
        for (ProjectProperties project : projects) {
            projectListModel.addElement(project);
        }
    }

    private void createNewProject() {
        String projectName = JOptionPane.showInputDialog(
                this,
                "Enter project name:",
                "Create New Project",
                JOptionPane.PLAIN_MESSAGE);

        if (projectName != null && !projectName.trim().isEmpty()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select folder for new project");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File folder = chooser.getSelectedFile();

                // Create new project
                ProjectProperties newProject = new ProjectProperties(projectName.trim());
                newProject.setFolderPath(folder.getAbsolutePath());
                properties.addProjectProperties(newProject);
                properties.save();
                properties.saveToDisk(); // Save to disk immediately

                // Reload list
                loadProjects();

                // Switch to the new project
                switchToNewProject(newProject);

                // Close the dialog
                dispose();
            }
        }
    }

    private void openFolderAsProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select folder to open as project");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            String projectName = folder.getName();

            // Create project from folder
            ProjectProperties newProject = new ProjectProperties(projectName);
            newProject.setFolderPath(folder.getAbsolutePath());
            properties.addProjectProperties(newProject);
            properties.save();
            properties.saveToDisk(); // Save to disk immediately

            // Reload list
            loadProjects();

            // Switch to the new project
            switchToNewProject(newProject);

            // Close the dialog
            dispose();
        }
    }

    private void cloneFromGitHub() {
        String repoUrl = JOptionPane.showInputDialog(
                this,
                "Enter GitHub repository URL:",
                "Clone from GitHub",
                JOptionPane.PLAIN_MESSAGE);

        if (repoUrl != null && !repoUrl.trim().isEmpty()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select parent folder for clone");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File parentFolder = chooser.getSelectedFile();

                // Extract project name from URL
                String projectName = extractProjectNameFromUrl(repoUrl);

                // Create destination folder for the clone
                File cloneFolder = new File(parentFolder, projectName);

                // Check if folder already exists
                if (cloneFolder.exists()) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Folder '" + projectName
                                    + "' already exists.\nPlease choose a different location or delete the existing folder manually.",
                            "Folder Exists",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Show a progress dialog while cloning
                JDialog progressDialog = new JDialog(this, "Clone in Progress", false);
                progressDialog.setLayout(new BorderLayout());
                JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
                progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
                progressPanel.add(new JLabel("Cloning repository..."), BorderLayout.NORTH);
                JProgressBar progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                progressPanel.add(progressBar, BorderLayout.CENTER);
                progressDialog.add(progressPanel);
                progressDialog.setSize(300, 120);
                progressDialog.setLocationRelativeTo(this);
                progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                progressDialog.setVisible(true);

                // Clone using system git in background
                new SwingWorker<GitCommandResult, Void>() {
                    @Override
                    protected GitCommandResult doInBackground() throws Exception {
                        return GitCommandRunner.runGitCommand(
                                parentFolder, 600, "clone", repoUrl, cloneFolder.getName());
                    }

                    @Override
                    protected void done() {
                        progressDialog.dispose();
                        try {
                            GitCommandResult result = get();

                            if (result.isSuccess()) {
                                // Create project
                                ProjectProperties newProject = new ProjectProperties(projectName);
                                newProject.setFolderPath(cloneFolder.getAbsolutePath());
                                newProject.setGithubRepo(repoUrl);
                                properties.addProjectProperties(newProject);
                                properties.save();
                                properties.saveToDisk();
                                loadProjects();

                                // Switch to the new project
                                switchToNewProject(newProject);

                                JOptionPane.showMessageDialog(
                                        ManageProjectsDialog.this,
                                        "Repository cloned successfully!",
                                        "Clone Complete",
                                        JOptionPane.INFORMATION_MESSAGE);

                                // Close the dialog
                                dispose();
                            } else {
                                JOptionPane.showMessageDialog(
                                        ManageProjectsDialog.this,
                                        "Failed to clone repository:\n" + result.getMessageWithAuthHint(repoUrl) +
                                                "\n\nNote: Partial clone may exist at:\n" + cloneFolder.getAbsolutePath() +
                                                "\n\nPlease delete it manually if needed.",
                                        "Clone Failed",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception ex) {
                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            JOptionPane.showMessageDialog(
                                    ManageProjectsDialog.this,
                                    "Failed to clone repository:\n" + msg +
                                            "\n\nNote: Partial clone may exist at:\n" + cloneFolder.getAbsolutePath() +
                                            "\n\nPlease delete it manually if needed.",
                                    "Clone Failed",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            }
        }
    }

    private String extractProjectNameFromUrl(String url) {
        // Extract project name from GitHub URL
        String name = url;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }

    /**
     * Update the DITA explorer with the project's default root map.
     */
    private void updateDitaExplorer(ProjectProperties project) {
        if (parent == null || parent.getDitaExplorer() == null) return;

        String defaultMap = project.getDefaultRootMap();
        if (ProjectProperties.TYPE_DITA.equals(project.getProjectType())
                && defaultMap != null && !defaultMap.isEmpty()) {
            String projectFolder = project.getFolderPath();
            File mapFile = (projectFolder != null)
                    ? new File(new File(projectFolder), defaultMap)
                    : new File(defaultMap);
            if (mapFile.exists()) {
                parent.getDitaExplorer().loadMap(mapFile);
            }
        } else {
            parent.getDitaExplorer().clearMap();
        }
    }

    /**
     * Switch to the newly created project
     */
    private void switchToNewProject(ProjectProperties project) {
        if (parent != null) {
            // Save as last opened project
            parent.getProperties().setLastProjectName(project.getName());

            // Switch to File Explorer tab
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
                }
            }

            // Update the ProjectSwitcher to show the new project
            ProjectSwitcher switcher = parent.getProjectSwitcher();
            if (switcher != null) {
                switcher.setCurrentProject(project);
            }

            // Notify parent to switch project context
            if (parent.getProjectPanel() != null) {
                parent.getProjectPanel().selectProject(project.getName());
            }

            // Reload DITA explorer with the new project's root map
            updateDitaExplorer(project);

            // Activate the project's associated framework
            String frameworkName = project.getFrameworkName();
            if (frameworkName != null && !frameworkName.isEmpty()) {
                Vector frameworkProps = parent.getProperties().getFrameworkProperties();
                for (int i = 0; i < frameworkProps.size(); i++) {
                    FrameworkProperties framework = (FrameworkProperties) frameworkProps.elementAt(i);
                    if (frameworkName.equals(framework.getName())) {
                        if ((project.getDitaOtPath() == null || project.getDitaOtPath().isEmpty())
                                && framework.getDitaOtPath() != null) {
                            System.setProperty("dita.ot.dir", framework.getDitaOtPath());
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Custom cell renderer for project list
     */
    private class ProjectListCellRenderer extends JPanel implements ListCellRenderer<ProjectProperties> {
        private JLabel nameLabel;
        private JLabel pathLabel;
        private boolean isSelected;
        private boolean isHovered;

        public ProjectListCellRenderer() {
            setLayout(new BorderLayout(5, 2));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(true);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(nameLabel);

            pathLabel = new JLabel();
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 10f));
            pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(pathLabel);

            add(textPanel, BorderLayout.CENTER);

            // Add more options button (three dots)
            JLabel moreButton = new JLabel("⋮");
            moreButton.setFont(moreButton.getFont().deriveFont(Font.BOLD, 16f));
            add(moreButton, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ProjectProperties> list,
                ProjectProperties value, int index,
                boolean isSelected, boolean cellHasFocus) {
            this.isSelected = isSelected;

            nameLabel.setText(value.getName());
            pathLabel.setText(value.getProjectType());

            if (isSelected) {
                setBackground(SELECTED_COLOR);
                nameLabel.setForeground(TEXT_COLOR);
                pathLabel.setForeground(SUBTITLE_COLOR);
            } else {
                setBackground(SIDEBAR_COLOR);
                nameLabel.setForeground(TEXT_COLOR);
                pathLabel.setForeground(SUBTITLE_COLOR);
            }

            return this;
        }
    }

    /**
     * Panel to edit project settings
     */
    private class ProjectSettingsPanel extends JPanel {
        private ProjectProperties currentProject;
        private JLabel projectNameLabel;
        private JLabel projectPathLabel;
        private JComboBox<String> typeCombo;
        private JTextField repoField;
        private JComboBox<String> frameworkCombo;
        private JComboBox<String> rootMapCombo;
        private JPanel rootMapPanel;
        private boolean updatingCombo = false;

        public ProjectSettingsPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(BACKGROUND_COLOR);
            setBorder(new EmptyBorder(40, 40, 40, 40));

            // Header
            projectNameLabel = new JLabel("Project Name");
            projectNameLabel.setFont(projectNameLabel.getFont().deriveFont(Font.BOLD, 20f));
            projectNameLabel.setForeground(TEXT_COLOR);
            projectNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(projectNameLabel);

            add(Box.createVerticalStrut(5));

            projectPathLabel = new JLabel("/path/to/project");
            projectPathLabel.setFont(projectPathLabel.getFont().deriveFont(Font.PLAIN, 12f));
            projectPathLabel.setForeground(SUBTITLE_COLOR);
            projectPathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(projectPathLabel);

            add(Box.createVerticalStrut(30));

            // Project Type
            add(createLabel("Project Type"));
            String[] types = { ProjectProperties.TYPE_NONE, ProjectProperties.TYPE_DITA,
                    ProjectProperties.TYPE_DOCBOOK };
            typeCombo = new JComboBox<>(types);
            typeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            typeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            typeCombo.addActionListener(e -> {
                if (currentProject != null) {
                    String type = (String) typeCombo.getSelectedItem();
                    currentProject.setProjectType(type);
                    updateRootMapVisibility();
                    properties.save();
                }
            });
            add(typeCombo);

            add(Box.createVerticalStrut(20));

            // GitHub Repo
            add(createLabel("GitHub Repository"));
            repoField = new JTextField();
            repoField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            repoField.setAlignmentX(Component.LEFT_ALIGNMENT);
            repoField.addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusLost(java.awt.event.FocusEvent evt) {
                    if (currentProject != null) {
                        currentProject.setGithubRepo(repoField.getText());
                        properties.save();
                    }
                }
            });
            add(repoField);

            add(Box.createVerticalStrut(20));

            // Framework
            add(createLabel("Framework"));
            frameworkCombo = new JComboBox<>();
            frameworkCombo.addItem("None");
            Vector frameworkProps = properties.getFrameworkProperties();
            for (int i = 0; i < frameworkProps.size(); i++) {
                FrameworkProperties pp = (FrameworkProperties) frameworkProps.elementAt(i);
                frameworkCombo.addItem(pp.getName());
            }
            frameworkCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            frameworkCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            frameworkCombo.addActionListener(e -> {
                if (currentProject != null) {
                    String selected = (String) frameworkCombo.getSelectedItem();
                    currentProject.setFrameworkName("None".equals(selected) ? null : selected);
                    properties.save();
                }
            });
            add(frameworkCombo);

            add(Box.createVerticalStrut(20));

            // Default Root Map (DITA only)
            rootMapPanel = new JPanel();
            rootMapPanel.setLayout(new BoxLayout(rootMapPanel, BoxLayout.Y_AXIS));
            rootMapPanel.setBackground(BACKGROUND_COLOR);
            rootMapPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel rootMapLabel = createLabel("Default Root Map");
            rootMapPanel.add(rootMapLabel);

            // Root map row: combo + Browse button
            rootMapCombo = new JComboBox<>();
            rootMapCombo.setEditable(false);
            rootMapCombo.addActionListener(e -> {
                if (!updatingCombo && currentProject != null && rootMapCombo.getSelectedItem() != null) {
                    currentProject.setDefaultRootMap((String) rootMapCombo.getSelectedItem());
                    properties.save();
                }
            });

            JButton browseButton = new JButton("Browse\u2026");
            browseButton.addActionListener(e -> browseForRootMap());

            JPanel rootMapRow = new JPanel(new BorderLayout(5, 0));
            rootMapRow.setBackground(BACKGROUND_COLOR);
            rootMapRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootMapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            rootMapRow.add(rootMapCombo, BorderLayout.CENTER);
            rootMapRow.add(browseButton, BorderLayout.EAST);
            rootMapPanel.add(rootMapRow);

            add(rootMapPanel);

            add(Box.createVerticalGlue());

            // Button row: Save + Open Project
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            buttonPanel.setBackground(BACKGROUND_COLOR);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(e -> {
                if (currentProject != null) {
                    saveCurrentFields();
                    properties.saveToDisk();
                    updateDitaExplorer(currentProject);
                    dispose();
                }
            });
            buttonPanel.add(saveButton);

            buttonPanel.add(Box.createHorizontalStrut(10));

            JButton openButton = new JButton("Open Project");
            openButton.addActionListener(e -> {
                if (currentProject != null) {
                    saveCurrentFields();
                    properties.saveToDisk();
                    switchToNewProject(currentProject);
                    dispose();
                }
            });
            buttonPanel.add(openButton);

            add(buttonPanel);
        }

        private void saveCurrentFields() {
            if (currentProject == null) return;
            currentProject.setProjectType((String) typeCombo.getSelectedItem());
            currentProject.setGithubRepo(repoField.getText());
            String selectedFramework = (String) frameworkCombo.getSelectedItem();
            currentProject.setFrameworkName("None".equals(selectedFramework) ? null : selectedFramework);
            if (rootMapCombo.getSelectedItem() != null) {
                currentProject.setDefaultRootMap((String) rootMapCombo.getSelectedItem());
            }
            properties.save();
        }

        private void browseForRootMap() {
            if (currentProject == null) return;

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Default Root Map");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "DITA Map files (*.ditamap)", "ditamap"));

            String folderPath = currentProject.getFolderPath();
            if (folderPath != null && !folderPath.isEmpty()) {
                chooser.setCurrentDirectory(new File(folderPath));
            }

            if (chooser.showOpenDialog(ManageProjectsDialog.this) == JFileChooser.APPROVE_OPTION) {
                File chosen = chooser.getSelectedFile();
                String path;
                if (folderPath != null && !folderPath.isEmpty()) {
                    File projectDir = new File(folderPath);
                    String projectAbs = projectDir.getAbsolutePath();
                    String chosenAbs = chosen.getAbsolutePath();
                    if (chosenAbs.startsWith(projectAbs + File.separator)) {
                        path = chosenAbs.substring(projectAbs.length() + 1);
                    } else {
                        path = chosenAbs;
                    }
                } else {
                    path = chosen.getAbsolutePath();
                }

                // Add to combo if not already present
                boolean found = false;
                for (int i = 0; i < rootMapCombo.getItemCount(); i++) {
                    if (path.equals(rootMapCombo.getItemAt(i))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    rootMapCombo.addItem(path);
                }
                rootMapCombo.setSelectedItem(path);
            }
        }

        private JLabel createLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setForeground(TEXT_COLOR);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setBorder(new EmptyBorder(0, 0, 5, 0));
            return label;
        }

        public void setProject(ProjectProperties project) {
            this.currentProject = project;
            projectNameLabel.setText(project.getName());
            projectPathLabel.setText(project.getFolderPath());

            // Set Type
            typeCombo.setSelectedItem(project.getProjectType());

            // Set Repo
            String repo = project.getGithubRepo();
            repoField.setText(repo != null ? repo : "");

            // Set Framework
            String frameworkName = project.getFrameworkName();
            frameworkCombo.setSelectedItem(frameworkName != null ? frameworkName : "None");

            // Show/hide root map and populate + restore selection
            updateRootMapVisibility();
        }

        private void updateRootMapVisibility() {
            String type = (String) typeCombo.getSelectedItem();
            boolean isDita = ProjectProperties.TYPE_DITA.equals(type);
            rootMapPanel.setVisible(isDita);

            if (isDita) {
                updateRootMapOptions();
            }
        }

        private void updateRootMapOptions() {
            updatingCombo = true;
            try {
                rootMapCombo.removeAllItems();
                if (currentProject != null && currentProject.getFolderPath() != null) {
                    File projectDir = new File(currentProject.getFolderPath());
                    if (projectDir.exists() && projectDir.isDirectory()) {
                        scanForDitaMaps(projectDir, projectDir.getAbsolutePath().length() + 1);
                    }
                }
            } finally {
                updatingCombo = false;
            }
            // Restore the saved selection after repopulating
            if (currentProject != null) {
                String saved = currentProject.getDefaultRootMap();
                if (saved != null && !saved.isEmpty()) {
                    // Add to combo if not already present (e.g. browsed file outside scan)
                    boolean found = false;
                    for (int i = 0; i < rootMapCombo.getItemCount(); i++) {
                        if (saved.equals(rootMapCombo.getItemAt(i))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        rootMapCombo.addItem(saved);
                    }
                    rootMapCombo.setSelectedItem(saved);
                }
            }
        }

        private void scanForDitaMaps(File dir, int prefixLen) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanForDitaMaps(file, prefixLen);
                    } else if (file.getName().toLowerCase().endsWith(".ditamap")) {
                        String relativePath = file.getAbsolutePath().substring(prefixLen);
                        rootMapCombo.addItem(relativePath);
                    }
                }
            }
        }
    }
}
