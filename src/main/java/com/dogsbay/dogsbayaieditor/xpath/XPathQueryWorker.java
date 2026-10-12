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

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.dogsbayaieditor.search.GlobFileFilter;

import javax.swing.SwingWorker;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.dom4j.Node;

/**
 * Background SwingWorker that walks a directory tree, loads each XML file
 * as an DogsBayDocument, runs an XPath query, and publishes results
 * incrementally to the UI.
 */
public class XPathQueryWorker extends SwingWorker<Void, XPathResultGroup> {
    private static final boolean DEBUG = true;

    private static final Set<String> XML_EXTENSIONS = new HashSet<>(Arrays.asList(
        "xml", "xsd", "xsl", "xslt", "xhtml", "dtd", "rng", "dita", "ditamap",
        "svg", "wsdl", "xq", "xquery", "fo", "fxml", "pom"
    ));

    private File rootDirectory;
    private String xpathExpr;
    private GlobFileFilter fileFilter;
    private XPathResultsListModel resultsModel;
    private StatusCallback statusCallback;

    private AtomicInteger filesSearched = new AtomicInteger(0);
    private AtomicInteger resultsFound = new AtomicInteger(0);

    public interface StatusCallback {
        void onProgress(int filesSearched, int resultsFound);
        void onComplete(int filesSearched, int resultsFound);
        void onError(String message);
    }

    public XPathQueryWorker(File rootDirectory, String xpathExpr, GlobFileFilter fileFilter,
                           XPathResultsListModel resultsModel, StatusCallback statusCallback) {
        this.rootDirectory = rootDirectory;
        this.xpathExpr = xpathExpr;
        this.fileFilter = fileFilter;
        this.resultsModel = resultsModel;
        this.statusCallback = statusCallback;
    }

    @Override
    protected Void doInBackground() throws Exception {
        if (DEBUG) {
            System.out.println("XPathQueryWorker: Starting XPath search in " + rootDirectory);
            System.out.println("XPathQueryWorker: XPath expression: " + xpathExpr);
        }

        try {
            searchDirectory(rootDirectory);
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("XPathQueryWorker: Error during search: " + e.getMessage());
                e.printStackTrace();
            }
            if (statusCallback != null) {
                statusCallback.onError("XPath search error: " + e.getMessage());
            }
        }

        return null;
    }

    @Override
    protected void process(List<XPathResultGroup> chunks) {
        for (XPathResultGroup group : chunks) {
            resultsModel.addFileGroup(group);
            resultsFound.addAndGet(group.getResultCount());
        }

        if (statusCallback != null) {
            statusCallback.onProgress(filesSearched.get(), resultsFound.get());
        }
    }

    @Override
    protected void done() {
        if (DEBUG) {
            System.out.println("XPathQueryWorker: Search complete. Files: " + filesSearched.get() +
                             ", Results: " + resultsFound.get());
        }

        if (statusCallback != null && !isCancelled()) {
            statusCallback.onComplete(filesSearched.get(), resultsFound.get());
        }
    }

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

            if (!fileFilter.accept(file)) {
                continue;
            }

            if (file.isDirectory()) {
                searchDirectory(file);
            } else if (file.isFile() && isXmlFile(file)) {
                searchFile(file);
            }
        }
    }

    private boolean isXmlFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase();
        return XML_EXTENSIONS.contains(ext);
    }

    private void searchFile(File file) {
        int count = filesSearched.incrementAndGet();

        if (DEBUG && count % 50 == 0) {
            System.out.println("XPathQueryWorker: Searched " + count + " files so far...");
        }

        try {
            DogsBayDocument doc = new DogsBayDocument(file.toURI().toURL());
            doc.load();

            if (doc.isError()) {
                return;
            }

            Vector results = doc.search(xpathExpr);

            if (results != null && !results.isEmpty()) {
                String text = doc.getText();
                XPathResultGroup group = new XPathResultGroup(file);

                for (Object obj : results) {
                    XPathResultGroup.XPathResultItem item = createResultItem(obj, file, text);
                    if (item != null) {
                        group.addResult(item);
                    }
                }

                if (group.getResultCount() > 0) {
                    publish(group);
                }
            }
        } catch (Exception e) {
            if (DEBUG && count <= 5) {
                System.err.println("XPathQueryWorker: Error processing file " + file + ": " + e.getMessage());
            }
        }
    }

    private XPathResultGroup.XPathResultItem createResultItem(Object node, File file, String text) {
        String nodeName;
        String nodeValue;
        String xpath;
        int charPos;
        String nodeType;

        if (node instanceof XElement) {
            XElement elem = (XElement) node;
            nodeName = elem.getQualifiedName();
            nodeValue = truncate(elem.getTextTrim(), 100);
            xpath = elem.getUniquePath();
            charPos = elem.getElementStartPosition();
            nodeType = "element";
        } else if (node instanceof XAttribute) {
            XAttribute attr = (XAttribute) node;
            nodeName = "@" + attr.getQualifiedName();
            nodeValue = truncate(attr.getValue(), 100);
            xpath = attr.getUniquePath();
            charPos = attr.getAttributeStartPosition();
            nodeType = "attribute";
        } else if (node instanceof Node) {
            Node n = (Node) node;
            nodeName = n.getName() != null ? n.getName() : "(text)";
            nodeValue = truncate(n.getStringValue(), 100);
            xpath = n.getUniquePath();
            charPos = -1;
            if (n.getParent() instanceof XElement) {
                charPos = ((XElement) n.getParent()).getElementStartPosition();
            }
            nodeType = getNodeTypeName(n.getNodeType());
        } else if (node instanceof String) {
            nodeName = "(string)";
            nodeValue = truncate((String) node, 100);
            xpath = "";
            charPos = -1;
            nodeType = "value";
        } else if (node instanceof Number) {
            nodeName = "(number)";
            nodeValue = node.toString();
            xpath = "";
            charPos = -1;
            nodeType = "value";
        } else if (node instanceof Boolean) {
            nodeName = "(boolean)";
            nodeValue = node.toString();
            xpath = "";
            charPos = -1;
            nodeType = "value";
        } else {
            return null;
        }

        int lineNumber = charPos >= 0 ? computeLineNumber(text, charPos) : 1;

        return new XPathResultGroup.XPathResultItem(nodeName, nodeValue, xpath, lineNumber, nodeType, file);
    }

    private int computeLineNumber(String text, int charPos) {
        if (text == null || charPos < 0 || charPos >= text.length()) {
            return 1;
        }

        int line = 1;
        for (int i = 0; i < charPos; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String getNodeTypeName(short type) {
        switch (type) {
            case Node.ELEMENT_NODE: return "element";
            case Node.ATTRIBUTE_NODE: return "attribute";
            case Node.TEXT_NODE: return "text";
            case Node.CDATA_SECTION_NODE: return "cdata";
            case Node.COMMENT_NODE: return "comment";
            case Node.PROCESSING_INSTRUCTION_NODE: return "pi";
            case Node.NAMESPACE_NODE: return "namespace";
            case Node.DOCUMENT_NODE: return "document";
            default: return "node";
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > maxLen) {
            return s.substring(0, maxLen - 3) + "...";
        }
        return s;
    }

    public int getFilesSearched() {
        return filesSearched.get();
    }

    public int getResultsFound() {
        return resultsFound.get();
    }
}
