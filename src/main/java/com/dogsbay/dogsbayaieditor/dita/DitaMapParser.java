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

package com.dogsbay.dogsbayaieditor.dita;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * Parser for DITA map files.
 * Handles recursive parsing of nested maps and topic references.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DitaMapParser {
    private Set<String> visitedMaps;

    public DitaMapParser() {
        visitedMaps = new HashSet<>();
    }

    /**
     * Parses a DITA map file and returns the root node of the tree.
     *
     * @param mapFile the DITA map file to parse
     * @return the root DitaMapNode
     * @throws Exception if parsing fails
     */
    public DitaMapNode parse(File mapFile) throws Exception {
        visitedMaps.clear();
        return parseMap(mapFile, true);
    }

    private DitaMapNode parseMap(File mapFile, boolean isRoot) throws Exception {
        if (mapFile == null || !mapFile.exists()) {
            throw new java.io.FileNotFoundException("Map file not found: " + mapFile);
        }

        String canonicalPath = mapFile.getCanonicalPath();
        if (visitedMaps.contains(canonicalPath)) {
            // Cycle detected or already visited, return a placeholder or null
            // For now, let's return a node indicating recursion to avoid infinite loops
            DitaMapNode node = new DitaMapNode(mapFile.getName() + " (Recursive)");
            node.setResolvedFile(mapFile);
            return node;
        }
        visitedMaps.add(canonicalPath);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // Secure processing
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setValidating(false);
        dbFactory.setNamespaceAware(true);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        dBuilder.setEntityResolver(new org.xml.sax.EntityResolver() {
            public org.xml.sax.InputSource resolveEntity(String publicId, String systemId)
                    throws org.xml.sax.SAXException, java.io.IOException {
                return new org.xml.sax.InputSource(new java.io.StringReader(""));
            }
        });
        Document doc = dBuilder.parse(mapFile);
        doc.getDocumentElement().normalize();

        Element rootElement = doc.getDocumentElement();
        String title = getTitle(rootElement);
        if (title == null || title.isEmpty()) {
            title = mapFile.getName();
        }

        DitaMapNode mapNode = new DitaMapNode(title);
        mapNode.setRoot(isRoot);
        mapNode.setResolvedFile(mapFile);
        mapNode.setHref(mapFile.getName());

        parseChildren(rootElement, mapNode, mapFile.getParentFile());

        return mapNode;
    }

    private void parseChildren(Element element, DitaNode parentNode, File contextDir) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();

                if (tagName.equals("topicref") || tagName.equals("mapref")) {
                    handleTopicRef(childElement, parentNode, contextDir);
                } else if (tagName.equals("topicgroup") || tagName.equals("topichead")) {
                    handleTopicGroup(childElement, parentNode, contextDir);
                } else if (tagName.equals("ditavalref")) {
                    handleDitavalref(childElement, parentNode);
                }
            }
        }
    }

    private void handleTopicRef(Element element, DitaNode parentNode, File contextDir) {
        String href = element.getAttribute("href");
        String format = element.getAttribute("format");
        String navtitle = element.getAttribute("navtitle");
        String locktitle = element.getAttribute("locktitle");

        // If no navtitle, try to get it from child <topicmeta>/<navtitle> or just use
        // href
        if (navtitle == null || navtitle.isEmpty()) {
            navtitle = getChildNavTitle(element);
        }
        if (navtitle == null || navtitle.isEmpty()) {
            navtitle = href;
        }

        if (href != null && !href.isEmpty()) {
            File resolvedFile = new File(contextDir, href);

            // Check if it's a map reference
            boolean isMap = "ditamap".equalsIgnoreCase(format) || element.getTagName().equals("mapref")
                    || href.endsWith(".ditamap");

            if (isMap) {
                try {
                    // Recursively parse the sub-map
                    // Note: We don't want to create a new node IF the sub-map root is what we want
                    // But usually mapref implies a node in the tree.
                    // Let's parse it and add the result as a child.
                    DitaMapNode subMapNode = parseMap(resolvedFile, false);

                    // Override title if specified in the reference
                    if (locktitle != null && "yes".equalsIgnoreCase(locktitle) && navtitle != null
                            && !navtitle.isEmpty()) {
                        subMapNode.setTitle(navtitle);
                    }

                    parentNode.add(subMapNode);
                } catch (Exception e) {
                    // Failed to parse sub-map, add as error/placeholder
                    DitaMapNode errorNode = new DitaMapNode(navtitle + " [Error: " + e.getMessage() + "]");
                    errorNode.setHref(href);
                    errorNode.setResolvedFile(resolvedFile);
                    parentNode.add(errorNode);
                    e.printStackTrace();
                }
            } else {
                // Regular topic reference
                DitaTopicNode topicNode = new DitaTopicNode(navtitle);
                topicNode.setHref(href);
                topicNode.setResolvedFile(resolvedFile);
                parentNode.add(topicNode);

                // Recursively handle children of this topicref (nested topics)
                parseChildren(element, topicNode, contextDir);
            }
        } else {
            // Topicref without href (maybe just a container/title)
            DitaGroupNode groupNode = new DitaGroupNode(navtitle != null ? navtitle : "Group");
            parentNode.add(groupNode);
            parseChildren(element, groupNode, contextDir);
        }
    }

    /**
     * Surface a {@code <ditavalref>} (DITA 1.3 branch filtering) as a badged leaf
     * under its parent topicref/group. The label prefers {@code dvrResourcePrefix},
     * falling back to the DITAVAL href. Not recursed into (nested branches AND-combine
     * — out of scope for the in-process inventory, like {@code BranchModel}).
     */
    private void handleDitavalref(Element element, DitaNode parentNode) {
        String href = element.getAttribute("href");
        // Label core matches links.Branch.label(): dvrResourcePrefix, else the DITAVAL
        // file name, else the href — so the Map Explorer agrees with the status-bar
        // switcher / preview for the same branch.
        String prefix = directChildText(element, "ditavalmeta", "dvrResourcePrefix");
        String label;
        if (prefix != null && !prefix.isBlank()) {
            label = prefix.trim();
        } else if (href != null && !href.isEmpty()) {
            label = new File(href).getName();
        } else {
            label = "(branch)";
        }
        DitaBranchNode node = new DitaBranchNode("branch: " + label, href);
        node.setHref(href);
        parentNode.add(node);
    }

    /** Text of the first <em>direct</em> child {@code <parentTag>}'s first direct child
     *  {@code <childTag>}, or null. Direct-children only, so a nested {@code ditavalref}'s
     *  {@code ditavalmeta} (which AND-combines, out of scope here) isn't picked up. */
    private String directChildText(Element element, String parentTag, String childTag) {
        for (Element parent : directChildren(element, parentTag)) {
            for (Element kid : directChildren(parent, childTag)) {
                return kid.getTextContent().trim();
            }
        }
        return null;
    }

    private java.util.List<Element> directChildren(Element element, String tag) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && ((Element) n).getTagName().equals(tag)) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private void handleTopicGroup(Element element, DitaNode parentNode, File contextDir) {
        String navtitle = element.getAttribute("navtitle");
        if (navtitle == null || navtitle.isEmpty()) {
            navtitle = getChildNavTitle(element);
        }

        DitaGroupNode groupNode = new DitaGroupNode(navtitle != null ? navtitle : "Group");
        parentNode.add(groupNode);
        parseChildren(element, groupNode, contextDir);
    }

    private String getTitle(Element root) {
        String title = root.getAttribute("title");
        if (title != null && !title.isEmpty()) {
            return title;
        }

        // Check for <title> child
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && ((Element) node).getTagName().equals("title")) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private String getChildNavTitle(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals("topicmeta")) {
                    NodeList metaChildren = child.getChildNodes();
                    for (int j = 0; j < metaChildren.getLength(); j++) {
                        Node metaNode = metaChildren.item(j);
                        if (metaNode.getNodeType() == Node.ELEMENT_NODE
                                && ((Element) metaNode).getTagName().equals("navtitle")) {
                            return metaNode.getTextContent().trim();
                        }
                    }
                } else if (child.getTagName().equals("navtitle")) {
                    return child.getTextContent().trim();
                }
            }
        }
        return null;
    }
}
