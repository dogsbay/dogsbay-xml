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

package com.dogsbay.dogsbayaieditor.xpath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups XPath results by file. Each group holds a list of
 * XPathResultItem objects representing matching nodes in a single file.
 */
public class XPathResultGroup {
    private File file;
    private List<XPathResultItem> results;
    private boolean expanded;

    public XPathResultGroup(File file) {
        this.file = file;
        this.results = new ArrayList<>();
        this.expanded = true;
    }

    public File getFile() {
        return file;
    }

    public List<XPathResultItem> getResults() {
        return results;
    }

    public void addResult(XPathResultItem item) {
        results.add(item);
    }

    public int getResultCount() {
        return results.size();
    }

    public String getRelativePath(File root) {
        if (root == null) {
            return file.getAbsolutePath();
        }

        String filePath = file.getAbsolutePath();
        String rootPath = root.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative;
        }

        return filePath;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /**
     * Represents a single XPath result node within a file.
     */
    public static class XPathResultItem {
        private String nodeName;
        private String nodeValue;
        private String xpath;
        private int lineNumber;
        private String nodeType;
        private File file;

        public XPathResultItem(String nodeName, String nodeValue, String xpath,
                              int lineNumber, String nodeType, File file) {
            this.nodeName = nodeName;
            this.nodeValue = nodeValue;
            this.xpath = xpath;
            this.lineNumber = lineNumber;
            this.nodeType = nodeType;
            this.file = file;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getNodeValue() {
            return nodeValue;
        }

        public String getXPath() {
            return xpath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getNodeType() {
            return nodeType;
        }

        public File getFile() {
            return file;
        }
    }
}
