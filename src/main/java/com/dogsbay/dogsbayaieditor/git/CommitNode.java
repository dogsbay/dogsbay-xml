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

package com.dogsbay.dogsbayaieditor.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

/**
 * Tree node representing a commit with expandable file list.
 * Lazily loads changed files when the node is expanded.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class CommitNode extends DefaultMutableTreeNode {
    private final CommitHistoryItem commit;
    private final Git git;
    private boolean filesLoaded = false;

    /**
     * Creates a commit node.
     *
     * @param commit the commit history item
     * @param git the Git instance for loading files
     */
    public CommitNode(CommitHistoryItem commit, Git git) {
        super(commit);
        this.commit = commit;
        this.git = git;

        // Add a placeholder child so the node appears expandable
        add(new DefaultMutableTreeNode("Loading..."));
    }

    /**
     * Gets the commit history item.
     */
    public CommitHistoryItem getCommit() {
        return commit;
    }

    /**
     * Loads the changed files for this commit.
     * Only loads once, then caches the result.
     */
    public void loadFiles() {
        if (filesLoaded) {
            return;
        }

        // Remove the placeholder
        removeAllChildren();

        try {
            RevCommit revCommit = commit.getRevCommit();

            // Handle initial commit (no parent)
            if (revCommit.getParentCount() == 0) {
                add(new DefaultMutableTreeNode("Initial commit - all files added"));
                filesLoaded = true;
                return;
            }

            RevCommit parentCommit = revCommit.getParent(0);

            // Create diff formatter
            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());

                // Get tree parsers for parent and current commit
                try (ObjectReader reader = git.getRepository().newObjectReader()) {
                    CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                    oldTreeParser.reset(reader, parentCommit.getTree());

                    CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                    newTreeParser.reset(reader, revCommit.getTree());

                    // Get diff entries
                    List<DiffEntry> diffs = diffFormatter.scan(oldTreeParser, newTreeParser);

                    // Add each file as a child node
                    for (DiffEntry diff : diffs) {
                        String changeType = getChangeTypeLabel(diff.getChangeType());
                        String fileName = getFileName(diff);
                        FileChangeNode fileNode = new FileChangeNode(fileName, changeType, diff.getChangeType());
                        add(fileNode);
                    }

                    // If no files changed, show a message
                    if (diffs.isEmpty()) {
                        add(new DefaultMutableTreeNode("No files changed"));
                    }
                }
            }
        } catch (Exception e) {
            add(new DefaultMutableTreeNode("Error loading files: " + e.getMessage()));
        }

        filesLoaded = true;
    }

    /**
     * Checks if files have been loaded.
     */
    public boolean areFilesLoaded() {
        return filesLoaded;
    }

    /**
     * Gets a readable label for the change type.
     */
    private String getChangeTypeLabel(DiffEntry.ChangeType type) {
        switch (type) {
            case ADD: return "Added";
            case MODIFY: return "Modified";
            case DELETE: return "Deleted";
            case RENAME: return "Renamed";
            case COPY: return "Copied";
            default: return type.name();
        }
    }

    /**
     * Gets the appropriate file name based on change type.
     */
    private String getFileName(DiffEntry diff) {
        switch (diff.getChangeType()) {
            case RENAME:
                return diff.getOldPath() + " → " + diff.getNewPath();
            case DELETE:
                return diff.getOldPath();
            default:
                return diff.getNewPath();
        }
    }

    /**
     * Tree node representing a changed file.
     */
    public static class FileChangeNode extends DefaultMutableTreeNode {
        private final String fileName;
        private final String changeType;
        private final DiffEntry.ChangeType changeTypeEnum;

        public FileChangeNode(String fileName, String changeType, DiffEntry.ChangeType changeTypeEnum) {
            super(fileName);
            this.fileName = fileName;
            this.changeType = changeType;
            this.changeTypeEnum = changeTypeEnum;
        }

        public String getFileName() {
            return fileName;
        }

        public String getChangeType() {
            return changeType;
        }

        public DiffEntry.ChangeType getChangeTypeEnum() {
            return changeTypeEnum;
        }

        @Override
        public String toString() {
            return fileName;
        }
    }
}
