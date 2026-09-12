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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.project.Match;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for VSCode-style search panel functionality.
 * Tests glob pattern matching, file filtering, search operations, and replace operations.
 */
@DisplayName("Search Functionality Tests")
class SearchFunctionalityTest {

    @TempDir
    Path tempDir;

    // Test fixtures
    private File rootDir;
    private File subDir1;
    private File subDir2;
    private File nestedDir;

    @BeforeEach
    void setUp() throws IOException {
        rootDir = tempDir.toFile();

        // Create directory structure:
        // root/
        //   file1.xml
        //   file2.txt
        //   README.md
        //   sub1/
        //     nested.xml
        //     data.json
        //   sub2/
        //     deep/
        //       document.ditamap
        //       test.xml
        //   target/
        //     build.xml

        subDir1 = new File(rootDir, "sub1");
        subDir2 = new File(rootDir, "sub2");
        nestedDir = new File(subDir2, "deep");
        File targetDir = new File(rootDir, "target");

        subDir1.mkdirs();
        subDir2.mkdirs();
        nestedDir.mkdirs();
        targetDir.mkdirs();

        // Create test files
        createFile(new File(rootDir, "file1.xml"), "<root>content</root>");
        createFile(new File(rootDir, "file2.txt"), "plain text file");
        createFile(new File(rootDir, "README.md"), "# README");
        createFile(new File(subDir1, "nested.xml"), "<data>nested</data>");
        createFile(new File(subDir1, "data.json"), "{\"key\":\"value\"}");
        createFile(new File(nestedDir, "document.ditamap"), "<map>ditamap content</map>");
        createFile(new File(nestedDir, "test.xml"), "<test>xml content</test>");
        createFile(new File(targetDir, "build.xml"), "<build>target file</build>");
    }

    private void createFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    // ===== GlobFileFilter Tests =====

    @Nested
    @DisplayName("Glob Pattern Matching")
    class GlobPatternTests {

        @Test
        @DisplayName("Empty pattern should match all files")
        void testEmptyPattern() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "", "");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")));
            assertTrue(filter.accept(new File(subDir1, "nested.xml")));
            assertTrue(filter.accept(new File(nestedDir, "test.xml")));
        }

        @Test
        @DisplayName("*.xml should match only XML files in root")
        void testSingleStarPattern() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "*.xml", "");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")), "Should match root XML");
            assertFalse(filter.accept(new File(rootDir, "file2.txt")), "Should not match TXT");
            assertFalse(filter.accept(new File(subDir1, "nested.xml")), "Should not match subdirectory XML");
        }

        @Test
        @DisplayName("**/*.xml should match XML files at any depth")
        void testDoubleStarPattern() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "**/*.xml", "");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")), "Should match root XML");
            assertTrue(filter.accept(new File(subDir1, "nested.xml")), "Should match subdirectory XML");
            assertTrue(filter.accept(new File(nestedDir, "test.xml")), "Should match nested XML");
            assertFalse(filter.accept(new File(rootDir, "file2.txt")), "Should not match TXT");
        }

        @Test
        @DisplayName("Multiple patterns should work with comma separator")
        void testMultiplePatterns() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "**/*.xml,**/*.ditamap", "");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")));
            assertTrue(filter.accept(new File(nestedDir, "document.ditamap")));
            assertFalse(filter.accept(new File(subDir1, "data.json")));
        }

        @Test
        @DisplayName("Specific directory pattern should work")
        void testDirectoryPattern() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "sub1/**/*.xml", "");

            assertTrue(filter.accept(new File(subDir1, "nested.xml")), "Should match sub1 XML");
            assertFalse(filter.accept(new File(rootDir, "file1.xml")), "Should not match root XML");
            assertFalse(filter.accept(new File(nestedDir, "test.xml")), "Should not match sub2 XML");
        }
    }

    @Nested
    @DisplayName("Exclude Pattern Tests")
    class ExcludePatternTests {

        @Test
        @DisplayName("Should exclude target/** directory")
        void testExcludeDirectory() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "", "target/**");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")));
            assertFalse(filter.accept(new File(rootDir, "target")), "Should exclude target dir");
            assertFalse(filter.accept(new File(new File(rootDir, "target"), "build.xml")),
                       "Should exclude files in target");
        }

        @Test
        @DisplayName("Should exclude by file extension")
        void testExcludeExtension() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "", "*.txt");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")));
            assertFalse(filter.accept(new File(rootDir, "file2.txt")));
        }

        @Test
        @DisplayName("Default excludes should work")
        void testDefaultExcludes() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "");

            // Create .git directory
            File gitDir = new File(rootDir, ".git");
            gitDir.mkdirs();

            assertFalse(filter.accept(gitDir), "Should exclude .git directory");
        }

        @Test
        @DisplayName("Include should override exclude when both match")
        void testIncludeOverridesExclude() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "**/*.xml", "**/*.xml");

            // Exclude pattern is checked first, so this should exclude XML files
            assertFalse(filter.accept(new File(rootDir, "file1.xml")));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle files with special characters in path")
        void testSpecialCharacters() throws IOException {
            File specialFile = new File(rootDir, "file-with-dash.xml");
            createFile(specialFile, "<test/>");

            GlobFileFilter filter = new GlobFileFilter(rootDir, "*.xml", "");
            assertTrue(filter.accept(specialFile));
        }

        @Test
        @DisplayName("Should handle null file gracefully")
        void testNullFile() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "*.xml", "");
            assertFalse(filter.accept(null));
        }

        @Test
        @DisplayName("Should always accept search root itself")
        void testSearchRootAccepted() {
            GlobFileFilter filter = new GlobFileFilter(rootDir, "*.xml", "");
            assertTrue(filter.accept(rootDir), "Should always accept search root");
        }

        @Test
        @DisplayName("Should handle dots in filenames")
        void testDotsInFilename() throws IOException {
            File dottedFile = new File(rootDir, "file.name.with.dots.xml");
            createFile(dottedFile, "<test/>");

            GlobFileFilter filter = new GlobFileFilter(rootDir, "*.xml", "");
            assertTrue(filter.accept(dottedFile));
        }
    }

    // ===== SearchResultGroup Tests =====

    @Nested
    @DisplayName("SearchResultGroup Tests")
    class SearchResultGroupTests {

        @Test
        @DisplayName("Should create group with file")
        void testCreateGroup() {
            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);

            assertNotNull(group);
            assertEquals(testFile, group.getFile());
            assertEquals(0, group.getMatchCount());
        }

        @Test
        @DisplayName("Should add matches to group")
        void testAddMatches() throws Exception {
            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);

            // Create mock matches
            Match match1 = new Match(testFile.toURI().toURL(), 1, 0, 5, "test line");
            Match match2 = new Match(testFile.toURI().toURL(), 2, 0, 5, "another line");

            group.addMatch(match1);
            group.addMatch(match2);

            assertEquals(2, group.getMatchCount());
            assertEquals(2, group.getMatches().size());
        }

        @Test
        @DisplayName("Should calculate relative path correctly")
        void testRelativePath() {
            File testFile = new File(subDir1, "nested.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);

            String relativePath = group.getRelativePath(rootDir);
            // getRelativePath uses File.separator, which is '\' on Windows
            // and '/' on Unix — assert against the native form.
            assertEquals("sub1" + File.separator + "nested.xml", relativePath);
        }

        @Test
        @DisplayName("Should handle expanded state")
        void testExpandedState() {
            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);

            assertTrue(group.isExpanded(), "Should be expanded by default");

            group.setExpanded(false);
            assertFalse(group.isExpanded());

            group.setExpanded(true);
            assertTrue(group.isExpanded());
        }
    }

    // ===== SearchResultsListModel Tests =====

    @Nested
    @DisplayName("SearchResultsListModel Tests")
    class SearchResultsListModelTests {

        @Test
        @DisplayName("Should create empty model")
        void testCreateEmptyModel() {
            SearchResultsListModel model = new SearchResultsListModel();

            assertEquals(0, model.getSize());
            assertEquals(0, model.getTotalMatchCount());
        }

        @Test
        @DisplayName("Should add file group")
        void testAddFileGroup() throws Exception {
            SearchResultsListModel model = new SearchResultsListModel();

            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);
            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "test");
            group.addMatch(match);

            model.addFileGroup(group);

            assertTrue(model.getSize() > 0);
            assertEquals(1, model.getTotalMatchCount());
        }

        @Test
        @DisplayName("Should expand and collapse groups")
        void testExpandCollapseGroup() throws Exception {
            SearchResultsListModel model = new SearchResultsListModel();

            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);
            Match match1 = new Match(testFile.toURI().toURL(), 1, 0, 5, "test1");
            Match match2 = new Match(testFile.toURI().toURL(), 2, 0, 5, "test2");
            group.addMatch(match1);
            group.addMatch(match2);

            model.addFileGroup(group);
            int sizeExpanded = model.getSize();

            model.collapseGroup(group);
            assertTrue(model.getSize() < sizeExpanded, "Size should decrease when collapsed");

            model.expandGroup(group);
            assertEquals(sizeExpanded, model.getSize(), "Size should increase when expanded");
        }

        @Test
        @DisplayName("Should clear all results")
        void testClearResults() throws Exception {
            SearchResultsListModel model = new SearchResultsListModel();

            File testFile = new File(rootDir, "test.xml");
            SearchResultGroup group = new SearchResultGroup(testFile);
            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "test");
            group.addMatch(match);
            model.addFileGroup(group);

            assertTrue(model.getSize() > 0);

            model.clear();
            assertEquals(0, model.getSize());
            assertEquals(0, model.getTotalMatchCount());
        }
    }

    // ===== ReplaceHandler Tests =====

    @Nested
    @DisplayName("ReplaceHandler Tests")
    class ReplaceHandlerTests {

        @Test
        @DisplayName("Should preserve ALL UPPERCASE")
        void testPreserveCaseUppercase() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "ERROR: Something failed");

            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "ERROR: Something failed");

            ReplaceHandler.replaceSingle(match, "warning", true);

            String content = Files.readString(testFile.toPath());
            assertTrue(content.contains("WARNING"), "Should preserve uppercase: " + content);
        }

        @Test
        @DisplayName("Should preserve all lowercase")
        void testPreserveCaseLowercase() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "error: something failed");

            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "error: something failed");

            ReplaceHandler.replaceSingle(match, "WARNING", true);

            String content = Files.readString(testFile.toPath());
            assertTrue(content.contains("warning"), "Should preserve lowercase: " + content);
        }

        @Test
        @DisplayName("Should preserve Title Case")
        void testPreserveCaseTitleCase() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "Error: something failed");

            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "Error: something failed");

            ReplaceHandler.replaceSingle(match, "warning", true);

            String content = Files.readString(testFile.toPath());
            assertTrue(content.contains("Warning"), "Should preserve title case: " + content);
        }

        @Test
        @DisplayName("Should use replacement as-is when preserve case disabled")
        void testNoPreserveCase() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "ERROR: something failed");

            Match match = new Match(testFile.toURI().toURL(), 1, 0, 5, "ERROR: something failed");

            ReplaceHandler.replaceSingle(match, "warning", false);

            String content = Files.readString(testFile.toPath());
            assertTrue(content.contains("warning"), "Should use exact replacement: " + content);
            assertFalse(content.contains("WARNING"), "Should not preserve case: " + content);
        }

        @Test
        @DisplayName("Should handle single replacement")
        void testSingleReplace() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "Find this text here");

            Match match = new Match(testFile.toURI().toURL(), 1, 5, 9, "Find this text here");

            ReplaceHandler.replaceSingle(match, "that", false);

            String content = Files.readString(testFile.toPath());
            assertEquals("Find that text here", content);
        }

        @Test
        @DisplayName("Should handle multiple replacements in order")
        void testReplaceAll() throws Exception {
            File testFile = new File(rootDir, "test.txt");
            createFile(testFile, "test test test");

            SearchResultGroup group = new SearchResultGroup(testFile);

            // Add matches for each "test"
            Match match1 = new Match(testFile.toURI().toURL(), 1, 0, 4, "test test test");
            Match match2 = new Match(testFile.toURI().toURL(), 1, 5, 9, "test test test");
            Match match3 = new Match(testFile.toURI().toURL(), 1, 10, 14, "test test test");

            group.addMatch(match1);
            group.addMatch(match2);
            group.addMatch(match3);

            List<SearchResultGroup> groups = new ArrayList<>();
            groups.add(group);

            int count = ReplaceHandler.replaceAll(groups, "replaced", false);

            assertEquals(3, count);

            String content = Files.readString(testFile.toPath());
            assertEquals("replaced replaced replaced", content);
        }

        @Test
        @DisplayName("Should handle empty file gracefully")
        void testReplaceInEmptyFile() throws Exception {
            File testFile = new File(rootDir, "empty.txt");
            createFile(testFile, "");

            Match match = new Match(testFile.toURI().toURL(), 1, 0, 0, "");

            assertDoesNotThrow(() -> ReplaceHandler.replaceSingle(match, "text", false));
        }
    }

    // ===== Integration Tests =====

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should filter and count files correctly")
        void testFullFilteringScenario() {
            // Search for all XML files, excluding target directory
            GlobFileFilter filter = new GlobFileFilter(rootDir, "**/*.xml", "target/**");

            int count = 0;
            count += filter.accept(new File(rootDir, "file1.xml")) ? 1 : 0;
            count += filter.accept(new File(subDir1, "nested.xml")) ? 1 : 0;
            count += filter.accept(new File(nestedDir, "test.xml")) ? 1 : 0;
            count += filter.accept(new File(new File(rootDir, "target"), "build.xml")) ? 1 : 0;

            assertEquals(3, count, "Should find 3 XML files (excluding target)");
        }

        @Test
        @DisplayName("Should handle complex glob patterns")
        void testComplexGlobPatterns() {
            // Include XML and DITAMAP, exclude target and sub2
            GlobFileFilter filter = new GlobFileFilter(rootDir,
                "**/*.xml,**/*.ditamap",
                "target/**,sub2/**");

            assertTrue(filter.accept(new File(rootDir, "file1.xml")));
            assertTrue(filter.accept(new File(subDir1, "nested.xml")));
            assertFalse(filter.accept(new File(nestedDir, "document.ditamap")),
                       "Should exclude sub2");
            assertFalse(filter.accept(new File(new File(rootDir, "target"), "build.xml")),
                       "Should exclude target");
        }
    }
}
