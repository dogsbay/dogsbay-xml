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
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Represents a single commit in the Git history.
 * Holds commit metadata for display in the commit history list.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class CommitHistoryItem {
    private final String commitId;
    private final String shortId;
    private final String message;
    private final String author;
    private final Date date;
    private final RevCommit revCommit;

    /**
     * Creates a commit history item from a JGit RevCommit.
     *
     * @param commit the RevCommit to extract data from
     */
    public CommitHistoryItem(RevCommit commit) {
        this.revCommit = commit;
        this.commitId = commit.getName();
        this.shortId = commit.getId().abbreviate(7).name();
        this.message = commit.getShortMessage();
        this.author = commit.getAuthorIdent().getName();
        this.date = new Date(commit.getCommitTime() * 1000L);
    }

    /**
     * Gets the full commit SHA.
     */
    public String getCommitId() {
        return commitId;
    }

    /**
     * Gets the abbreviated commit SHA (7 chars).
     */
    public String getShortId() {
        return shortId;
    }

    /**
     * Gets the commit message (short version).
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the commit author name.
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Gets the commit date.
     */
    public Date getDate() {
        return date;
    }

    /**
     * Gets the underlying RevCommit for detailed operations.
     */
    public RevCommit getRevCommit() {
        return revCommit;
    }

    /**
     * Calculates diff statistics for this commit.
     * Returns an array: [filesChanged, insertions, deletions]
     */
    public int[] calculateDiffStats(Git git) {
        int[] stats = new int[3]; // [files, insertions, deletions]

        try {
            // Get parent commit (if this is not the first commit)
            if (revCommit.getParentCount() == 0) {
                // Initial commit - count all files as additions
                stats[0] = 1; // At least one file
                return stats;
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
                    stats[0] = diffs.size(); // Files changed

                    // Calculate insertions and deletions
                    for (DiffEntry diff : diffs) {
                        FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                        for (Edit edit : fileHeader.toEditList()) {
                            stats[1] += edit.getEndB() - edit.getBeginB(); // Insertions
                            stats[2] += edit.getEndA() - edit.getBeginA(); // Deletions
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If stats calculation fails, return zeros
            stats[0] = 0;
            stats[1] = 0;
            stats[2] = 0;
        }

        return stats;
    }

    /**
     * Formats a detailed tooltip for this commit.
     * Shows date, file count, insertions/deletions, and full commit ID.
     */
    public String formatTooltip(Git git) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm");
        int[] stats = calculateDiffStats(git);

        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append("<b>").append(message).append("</b><br>");
        tooltip.append(author).append("<br>");
        tooltip.append(dateFormat.format(date)).append("<br><br>");
        tooltip.append("<b>Files changed:</b> ").append(stats[0]).append("<br>");
        tooltip.append("<b>Insertions:</b> <font color='green'>+").append(stats[1]).append("</font><br>");
        tooltip.append("<b>Deletions:</b> <font color='red'>-").append(stats[2]).append("</font><br><br>");
        tooltip.append("<b>Commit ID:</b> ").append(commitId);
        tooltip.append("</html>");

        return tooltip.toString();
    }

    /**
     * Returns a formatted string for display in the list.
     * Format: "message - author"
     */
    @Override
    public String toString() {
        return String.format("%s - %s", message, author);
    }
}
