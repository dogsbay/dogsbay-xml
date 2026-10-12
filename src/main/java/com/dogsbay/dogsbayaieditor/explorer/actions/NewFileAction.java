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

package com.dogsbay.dogsbayaieditor.explorer.actions;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URL;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.NewDocumentDialog;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.explorer.FileExplorerPanel;
import com.dogsbay.dogsbayaieditor.explorer.FileOperationUtils;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to create a new file in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class NewFileAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to create a new file.
     *
     * @param parent     the main editor window
     * @param node       the directory node where the file will be created
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public NewFileAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("New File");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Only enable for directories
        setEnabled(node != null && node.isDirectory());
    }

    /**
     * Creates a new file in the selected directory.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null || !node.isDirectory()) {
            return;
        }

        File directory = node.getFile();

        if (DEBUG) {
            System.out.println("NewFileAction: Creating file in " + directory.getAbsolutePath());
        }

        // Show input dialog for filename
        String filename = JOptionPane.showInputDialog(
            parent,
            "Enter filename:",
            "New File",
            JOptionPane.PLAIN_MESSAGE
        );

        if (filename == null || filename.trim().isEmpty()) {
            // User cancelled or entered empty name
            return;
        }

        filename = filename.trim();

        // Validate filename
        if (!FileOperationUtils.isValidFileName(filename)) {
            MessageHandler.showError(
                parent,
                "Invalid filename. Filenames cannot contain: < > : \" / \\ | ? *",
                "Invalid Filename"
            );
            return;
        }

        // What goes in it. File ▸ New has always asked this and the explorer never did, so the same menu item in two
        // places meant two different things, and the explorer's version handed back an empty file — which is not
        // well-formed, so the XML commands switched themselves off on the file just asked for.
        String content = "";
        if (isXmlKind(filename)) {
            NewDocumentDialog dialog = new NewDocumentDialog(parent, parent.getProperties());
            dialog.setForFile(filename);
            dialog.setVisible(true);
            if (dialog.isCancelled()) {
                return;
            }
            try {
                content = FileUtilities.initialText(dialog);
                if (dialog.getNewDocumentType() != NewDocumentDialog.NEW_FROM_CLIPBOARD_DOCUMENT) {
                    // pasted text is the writer's own; only a template carries a placeholder id to replace
                    content = withIdFrom(content, filename);
                }
            } catch (Exception failure) {
                MessageHandler.showError(parent, "Could not prepare the new file: " + failure.getMessage(), "New File");
                return;
            }
        }

        // Create the file on a background thread
        final String finalContent = content;
        final String finalFilename = filename;
        // Read on the EDT, where this action runs: the worker below needs the project folder, and asking a Swing
        // component for it from another thread is the kind of thing that works until it does not.
        final File projectRoot = parent.getFileExplorer() != null
                ? parent.getFileExplorer().getRootDirectory() : null;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    File newFile = FileOperationUtils.createFile(directory, finalFilename);
                    if (!finalContent.isEmpty()) {
                        java.nio.file.Files.writeString(newFile.toPath(), finalContent);
                    }

                    // A folder becomes a DITA project when its first topic or map appears in it. Detection runs
                    // when a folder is opened, and an empty one is not a DITA project then, so without this the
                    // project stays type "None" and the DITA commands stay switched off for the rest of the
                    // session — on the folder the writer has just started a DITA project in.
                    final com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.Result configured =
                            isDitaFile(finalFilename)
                                    ? com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.configureFolder(
                                            parent.getProperties(), projectRoot)
                                    : com.dogsbay.dogsbayaieditor.project.ProjectAutoConfigurer.Result.none();

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            if (configured.changedAnything()) {
                                parent.setStatus("DITA project"
                                        + (configured.framework() != null
                                                ? " — framework " + configured.framework() : "")
                                        + (configured.wroteConfig() ? " (saved .dogsbay/config.xml)" : ""));
                            }
                            // Refresh the node to show the new file
                            node.refresh(treeModel);
                            treeModel.nodeStructureChanged(node);

                            // Find and select the new file node
                            selectNewFile(newFile);

                            // Open the new file in the editor
                            try {
                                URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(newFile);
                                parent.open(url, null, true);
                            } catch (Exception ex) {
                                MessageHandler.showError(
                                    parent,
                                    "File created but could not be opened: " + ex.getMessage(),
                                    "Open File Error"
                                );
                                if (DEBUG) ex.printStackTrace();
                            }
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not create file: " + ex.getMessage(),
                                "Create File Error"
                            );
                        }
                    });
                    if (DEBUG) ex.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * Finds and selects the newly created file in the tree.
     *
     * @param newFile the newly created file
     */
    private void selectNewFile(File newFile) {
        if (newFile == null || tree == null) {
            return;
        }

        // Search for the node
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode childNode = (FileSystemNode) child;
                if (childNode.getFile().equals(newFile)) {
                    TreePath path = new TreePath(childNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    break;
                }
            }
        }
    }

    /** A DITA topic or map, by name: what turns a folder into a DITA project. */
    static boolean isDitaFile(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".dita") || name.endsWith(".ditamap");
    }

    /**
     * Whether a name is one of the XML kinds, so that asking what to put in it makes sense.
     *
     * <p>A Markdown or plain-text file gets no dialog: the choices File ▸ New offers — a document type, a template, a DTD —
     * are about XML, and offering them for a note would be consistency for its own sake.
     *
     * @param filename the name typed for the new file.
     * @return whether the file's format uses XML.
     */
    static boolean isXmlKind(String filename) {
        com.dogsbay.xml.editor.DocumentFormat format = com.dogsbay.xml.editor.DocumentFormatRegistry.detect(filename);
        return format != null && format.isXmlBased();
    }

    /**
     * Give the new document an id taken from its file name.
     *
     * <p>The templates carry a placeholder — {@code concept_id}, {@code task_id} — which every writer then has to change,
     * and forgetting to is how two topics end up sharing an id. The file name is already a decision the writer has made, so
     * the id follows it. File ▸ New cannot do this, because there is no file name until the document is saved.
     *
     * <p>Only the root element's own id is touched. A first version looked for the first {@code id=} after the document type
     * declaration, which in content with no declaration found a child instead: the root kept its placeholder and a
     * descendant's id was silently rewritten, breaking anything pointing at it.
     *
     * @param content  the template's text.
     * @param filename the name the file is being created as.
     * @return the text with the root element's id replaced, or unchanged when there is none to replace.
     */
    static String withIdFrom(String content, String filename) {
        String id = idFor(filename);
        if (content == null || id == null) {
            return content;
        }
        int tagStart = rootTagStart(content);
        if (tagStart < 0) {
            return content;
        }
        int tagEnd = content.indexOf('>', tagStart);
        if (tagEnd < 0) {
            return content;
        }
        java.util.regex.Matcher attribute = java.util.regex.Pattern.compile("\\bid\\s*=\\s*([\"'])(.*?)\\1")
                .matcher(content.substring(tagStart, tagEnd));
        if (!attribute.find()) {
            return content; // a root with no id of its own is left alone rather than given one
        }
        int from = tagStart + attribute.start(2);
        int to = tagStart + attribute.end(2);
        return content.substring(0, from) + id + content.substring(to);
    }

    /**
     * Where the root element's start tag begins: past the declaration, the document type and any comment before it.
     *
     * @param content the document's text.
     * @return the index of the root's {@code <}, or -1 when there is no element.
     */
    static int rootTagStart(String content) {
        int at = 0;
        while (at < content.length()) {
            int open = content.indexOf('<', at);
            if (open < 0) {
                return -1;
            }
            if (content.startsWith("<?", open)) {
                int close = content.indexOf("?>", open);
                at = close < 0 ? content.length() : close + 2;
            } else if (content.startsWith("<!--", open)) {
                int close = content.indexOf("-->", open);
                at = close < 0 ? content.length() : close + 3;
            } else if (content.startsWith("<!", open)) {
                // a document type, whose internal subset may contain its own declarations and so its own > characters
                int subset = content.indexOf('[', open);
                int close = content.indexOf('>', open);
                if (subset >= 0 && close >= 0 && subset < close) {
                    int end = content.indexOf(']', subset);
                    close = end < 0 ? -1 : content.indexOf('>', end);
                }
                at = close < 0 ? content.length() : close + 1;
            } else {
                return open;
            }
        }
        return -1;
    }

    /**
     * An XML name taken from a file name: the stem, with anything a name may not contain turned into a hyphen.
     *
     * <p>Letters are letters in any script. A first version allowed only the Latin alphabet, so a topic named in Chinese,
     * Greek or Cyrillic produced no id at all and quietly kept the template's placeholder — the very collision this exists
     * to prevent, for the writers least likely to notice it.
     *
     * @param filename the file name.
     * @return the id, or null when nothing usable is left.
     */
    static String idFor(String filename) {
        if (filename == null) {
            return null;
        }
        String stem = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            cleaned.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '-');
        }
        String id = cleaned.toString().replaceAll("-{2,}", "-").replaceAll("^[-.]+", "").replaceAll("-+$", "");
        if (id.isEmpty()) {
            return null;
        }
        // an XML name cannot begin with a digit, and a topic whose name does is ordinary enough to handle rather than refuse
        return Character.isDigit(id.charAt(0)) ? "_" + id : id;
    }
}
