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

package com.dogsbay.dogsbayaieditor.git.actions;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.git.GitFileNode;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import com.dogsbay.xml.xdiff.XDiff;
import com.dogsbay.xml.xdiff.XDiffTreeDialog;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Action to open XML Diff comparing current file with HEAD version.
 * Shows structured XML differences using the XML Diff and Merge tool.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class XmlDiffAction extends AbstractAction {
    private static final boolean DEBUG = true;

    private GitPanel gitPanel;
    private GitFileNode fileNode;
    private DogsBayAIEditor parent;

    /**
     * Creates an action to show XML diff.
     *
     * @param gitPanel the Git panel
     * @param fileNode the file node to diff
     * @param parent   the parent editor
     */
    public XmlDiffAction(GitPanel gitPanel, GitFileNode fileNode, DogsBayAIEditor parent) {
        super("XML Diff vs HEAD");
        this.gitPanel = gitPanel;
        this.fileNode = fileNode;
        this.parent = parent;

        // Enable for all modified/staged files
        // TODO: In future, can add extension filter for known XML types (.xml, .dita, .ditamap, .concept, etc.)
        boolean hasChanges = fileNode != null &&
                (fileNode.getStatus() == GitFileNode.GitStatus.MODIFIED ||
                 fileNode.getStatus() == GitFileNode.GitStatus.STAGED);
        setEnabled(hasChanges);
    }

    /**
     * Opens XML Diff comparing current file with HEAD version.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (fileNode == null || gitPanel == null || parent == null) {
            return;
        }

        File currentFile = fileNode.getFile();
        File headFile = null;

        try {
            Git git = gitPanel.getGit();
            if (git == null) {
                MessageHandler.showError("No Git repository found.", "XML Diff Error");
                return;
            }

            // Get file content from HEAD
            String relativePath = fileNode.getRelativePath();
            byte[] headContent = getFileContentFromHead(git.getRepository(), relativePath);

            if (headContent == null) {
                MessageHandler.showError(
                        "Could not retrieve HEAD version of file:\n" + currentFile.getName(),
                        "XML Diff Error");
                return;
            }

            // Create temp file for HEAD version
            headFile = File.createTempFile("xmldiff_head_", ".xml");
            headFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(headFile)) {
                fos.write(headContent);
            }

            if (DEBUG) {
                System.out.println("XmlDiffAction: Comparing " + currentFile + " with HEAD version");
            }

            // Create XDiff and generate diff
            if (DEBUG) {
                System.out.println("XmlDiffAction: Creating XDiff for:");
                System.out.println("  HEAD: " + headFile.toURI().toString());
                System.out.println("  Working: " + currentFile.toURI().toString());
            }

            XDiff xdiff = new XDiff(
                    headFile.toURI().toString(),
                    currentFile.toURI().toString(),
                    parent.getProperties());
            String diffResult = xdiff.getDiff();

            if (DEBUG) {
                System.out.println("XmlDiffAction: Got diff result, length: " +
                    (diffResult != null ? diffResult.length() : "null"));
            }

            if (diffResult == null) {
                MessageHandler.showError("Diff operation returned null result", "XML Diff Error");
                return;
            }

            if (XDiff.NO_DIFF.equals(diffResult)) {
                MessageHandler.showMessage("The two files are identical!", "XML Diff");
                return;
            }

            if (DEBUG) {
                System.out.println("XmlDiffAction: Cleaning up result...");
            }

            // Clean up result
            String cleanResult = cleanUpResult(diffResult);

            if (DEBUG) {
                System.out.println("XmlDiffAction: Cleaned result, length: " +
                    (cleanResult != null ? cleanResult.length() : "null"));
            }

            if (cleanResult == null || cleanResult.trim().isEmpty()) {
                MessageHandler.showError("Diff result is empty after cleanup", "XML Diff Error");
                return;
            }

            // Create result document
            StringBuilder buf = new StringBuilder();
            buf.append("<diff_result>");
            buf.append(cleanResult);
            buf.append("</diff_result>");

            DogsBayDocument doc = new DogsBayDocument(cleanResult, true);

            // Show diff tree dialog
            XDiffTreeDialog dialogTree = new XDiffTreeDialog(parent, parent.getProperties());
            dialogTree.show(doc,
                    "HEAD:" + currentFile.getName(),
                    "Working:" + currentFile.getName(),
                    parent);

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("XmlDiffAction: Error: " + ex.getMessage());
                ex.printStackTrace();
            }

            // Build detailed error message
            String errorMsg = ex.getMessage();
            if (errorMsg == null) {
                errorMsg = ex.getClass().getName();
            }

            Throwable cause = ex.getCause();
            if (cause != null && cause.getMessage() != null) {
                errorMsg += "\nCause: " + cause.getMessage();
            }

            MessageHandler.showError(
                    "Error performing XML diff:\n" + errorMsg,
                    "XML Diff Error");
        } finally {
            // Clean up temp file (keep for now as dialog might need it)
            // The deleteOnExit() will clean it up eventually
        }
    }

    /**
     * Gets file content from HEAD commit.
     *
     * @param repository   the git repository
     * @param relativePath the relative path of the file
     * @return file content as bytes, or null if not found
     */
    private byte[] getFileContentFromHead(Repository repository, String relativePath) {
        try {
            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                return null;
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit headCommit = revWalk.parseCommit(headId);

                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(headCommit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(relativePath));

                    if (!treeWalk.next()) {
                        if (DEBUG) {
                            System.err.println("XmlDiffAction: File not found in HEAD: " + relativePath);
                        }
                        return null;
                    }

                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    return loader.getBytes();
                }
            }
        } catch (IOException ex) {
            if (DEBUG) {
                System.err.println("XmlDiffAction: Error reading from HEAD: " + ex.getMessage());
                ex.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Cleans up XML diff result by removing declarations and DOCTYPE.
     * Copied from XDiffAction to maintain consistency.
     */
    private String cleanUpResult(String resultDiff) {
        if (resultDiff == null) {
            return null;
        }
        resultDiff = resultDiff.trim();

        // Remove XML declaration
        if (resultDiff.startsWith("<?xml") && !resultDiff.startsWith("<?xml-stylesheet")) {
            int xmlDeclEnd = resultDiff.indexOf("?>", resultDiff.indexOf("<?xml"));
            if (xmlDeclEnd != -1) {
                resultDiff = resultDiff.substring(xmlDeclEnd + 2);
            }
        }

        // Remove DOCTYPE
        resultDiff = resultDiff.trim();
        if (resultDiff.startsWith("<!DOCTYPE")) {
            resultDiff = stripOutDoctype(resultDiff);
        }

        return resultDiff;
    }

    /**
     * Strips DOCTYPE declaration from result.
     * Copied from XDiffAction to maintain consistency.
     */
    private String stripOutDoctype(String resultDiff) {
        int doctypeStart = resultDiff.indexOf("<!DOCTYPE");
        int endBracket = resultDiff.indexOf(">", doctypeStart);

        // Check for quotes that might contain '>'
        int quote = resultDiff.indexOf("\"");
        int secondQuote = -1;
        if (quote == -1) {
            quote = resultDiff.indexOf("'");
            if (quote != -1) {
                secondQuote = resultDiff.indexOf("'", quote + 1);
            }
        } else {
            secondQuote = resultDiff.indexOf("\"", quote + 1);
        }

        if (endBracket < secondQuote) {
            endBracket = resultDiff.indexOf(">", secondQuote);
        }

        int internalDoctypeEnd = resultDiff.indexOf("]>");

        if (internalDoctypeEnd == -1) {
            resultDiff = resultDiff.substring(endBracket + 1);
        } else {
            int internalDoctypeStart = resultDiff.indexOf("[");

            if (endBracket < internalDoctypeStart) {
                resultDiff = resultDiff.substring(endBracket + 1);
            } else {
                resultDiff = resultDiff.substring(internalDoctypeEnd + 2);
            }
        }

        return resultDiff;
    }
}
