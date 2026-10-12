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

package com.dogsbay.dogsbayaieditor.search;

import com.dogsbay.dogsbayaieditor.project.Finder;
import com.dogsbay.dogsbayaieditor.project.Match;

import javax.swing.SwingWorker;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background worker for searching files in a directory tree.
 * Uses SwingWorker to perform directory traversal and file searching
 * without blocking the UI thread. Publishes results incrementally
 * as files are searched.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class SearchWorker extends SwingWorker<Void, SearchResultGroup> {
    private static final boolean DEBUG = true;

    private File rootDirectory;
    private String searchText;
    private boolean useRegex;
    private boolean matchCase;
    private boolean matchWholeWord;
    private GlobFileFilter fileFilter;
    private SearchResultsListModel resultsModel;
    private SearchStatusCallback statusCallback;

    private AtomicInteger filesSearched = new AtomicInteger(0);
    private AtomicInteger matchesFound = new AtomicInteger(0);

    /**
     * Callback interface for search status updates.
     */
    public interface SearchStatusCallback {
        void onProgress(int filesSearched, int matchesFound);
        void onComplete(int filesSearched, int matchesFound);
        void onError(String message);
    }

    /**
     * Creates a new search worker.
     *
     * @param rootDirectory the root directory to search
     * @param searchText the text to search for
     * @param useRegex true to use regular expressions
     * @param matchCase true for case-sensitive search
     * @param matchWholeWord true to match whole words only
     * @param fileFilter file filter for include/exclude patterns
     * @param resultsModel the model to add results to
     * @param statusCallback callback for status updates
     */
    public SearchWorker(File rootDirectory, String searchText, boolean useRegex,
                       boolean matchCase, boolean matchWholeWord,
                       GlobFileFilter fileFilter, SearchResultsListModel resultsModel,
                       SearchStatusCallback statusCallback) {
        this.rootDirectory = rootDirectory;
        this.searchText = searchText;
        this.useRegex = useRegex;
        this.matchCase = matchCase;
        this.matchWholeWord = matchWholeWord;
        this.fileFilter = fileFilter;
        this.resultsModel = resultsModel;
        this.statusCallback = statusCallback;
    }

    @Override
    protected Void doInBackground() throws Exception {
        if (DEBUG) {
            System.out.println("SearchWorker: Starting search in " + rootDirectory);
        }

        try {
            searchDirectory(rootDirectory);
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("SearchWorker: Error during search: " + e.getMessage());
                e.printStackTrace();
            }
            if (statusCallback != null) {
                statusCallback.onError("Search error: " + e.getMessage());
            }
        }

        return null;
    }

    @Override
    protected void process(List<SearchResultGroup> chunks) {
        // Add results to model as they come in (on EDT)
        for (SearchResultGroup group : chunks) {
            resultsModel.addFileGroup(group);
            matchesFound.addAndGet(group.getMatchCount());
        }

        // Update status
        if (statusCallback != null) {
            statusCallback.onProgress(filesSearched.get(), matchesFound.get());
        }
    }

    @Override
    protected void done() {
        if (DEBUG) {
            System.out.println("SearchWorker: Search complete. Files: " + filesSearched.get() +
                             ", Matches: " + matchesFound.get());
        }

        if (statusCallback != null && !isCancelled()) {
            statusCallback.onComplete(filesSearched.get(), matchesFound.get());
        }
    }

    /**
     * Recursively searches a directory.
     *
     * @param dir the directory to search
     */
    private void searchDirectory(File dir) {
        if (isCancelled()) {
            return;
        }

        if (!dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (isCancelled()) {
                break;
            }

            // Apply file filter
            if (!fileFilter.accept(file)) {
                continue;
            }

            if (file.isDirectory()) {
                searchDirectory(file); // Recursive
            } else if (file.isFile()) {
                searchFile(file);
            }
        }
    }

    /**
     * Searches a single file for matches.
     *
     * @param file the file to search
     */
    private void searchFile(File file) {
        int count = filesSearched.incrementAndGet();

        if (DEBUG && count % 10 == 0) {
            System.out.println("SearchWorker: Searched " + count + " files so far...");
        }

        if (DEBUG && count <= 5) {
            System.out.println("SearchWorker: Searching file: " + file.getAbsolutePath());
        }

        try {
            URL url = file.toURI().toURL();
            Vector matches = Finder.find(url, searchText, useRegex, matchCase, matchWholeWord);

            if (DEBUG && count <= 5) {
                System.out.println("  -> Found " + (matches != null ? matches.size() : 0) + " matches");
            }

            if (matches != null && !matches.isEmpty()) {
                SearchResultGroup group = new SearchResultGroup(file);
                for (Object match : matches) {
                    group.addMatch((Match) match);
                }

                // Publish result for incremental UI update
                publish(group);

                if (DEBUG) {
                    System.out.println("SearchWorker: Found " + matches.size() +
                                     " matches in " + file.getName());
                }
            }
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("SearchWorker: Error searching file " + file + ": " +
                                 e.getMessage());
            }
            // Continue with other files even if one fails
        }
    }

    /**
     * Gets the number of files searched so far.
     *
     * @return files searched count
     */
    public int getFilesSearched() {
        return filesSearched.get();
    }

    /**
     * Gets the number of matches found so far.
     *
     * @return matches found count
     */
    public int getMatchesFound() {
        return matchesFound.get();
    }
}
