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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.links.KeyDefinition;
import com.dogsbay.dogsbayaieditor.links.KeySpace;

/**
 * Parser for DITA map files, for the Topic Maps explorer.
 * Handles recursive parsing of nested maps and topic references.
 *
 * <p>Topic references by key ({@code <topicref keyref="install"/>}, with no
 * {@code href}) resolve through the root map's {@link KeySpace}, as DITA-OT does,
 * with key scopes ({@code @keyscope}) along the map structure. A node's label is its
 * navigation title, else the key definition's link text, else the referenced
 * topic's {@code <title>}, else the key name or href. A title-less topic group adds
 * no level of its own (its children appear in place, as in DITA-OT's table of
 * contents); a resource-only one is labeled as such.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DitaMapParser {
    private Set<String> visitedMaps;
    private KeySpace keySpace = KeySpace.empty();
    private final Map<File, String> topicTitles = new HashMap<>();

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
        topicTitles.clear();
        keySpace = mapFile != null && mapFile.isFile() ? KeySpace.fromRootMap(mapFile) : KeySpace.empty();
        return parseMap(mapFile, true, "");
    }

    private DitaMapNode parseMap(File mapFile, boolean isRoot, String scope) throws Exception {
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

        parseChildren(rootElement, mapNode, mapFile.getParentFile(), withScope(scope, rootElement));

        return mapNode;
    }

    private void parseChildren(Element element, DitaNode parentNode, File contextDir, String scope) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();

                if (tagName.equals("topicref") || tagName.equals("mapref")) {
                    handleTopicRef(childElement, parentNode, contextDir, scope);
                } else if (tagName.equals("topicgroup") || tagName.equals("topichead")) {
                    handleTopicGroup(childElement, parentNode, contextDir, scope);
                } else if (tagName.equals("ditavalref")) {
                    handleDitavalref(childElement, parentNode);
                }
            }
        }
    }

    private void handleTopicRef(Element element, DitaNode parentNode, File contextDir, String outerScope) {
        String href = element.getAttribute("href");
        String keyref = element.getAttribute("keyref");
        String format = element.getAttribute("format");
        String locktitle = element.getAttribute("locktitle");
        String scope = withScope(outerScope, element);
        String navtitle = navTitle(element);

        // A reference by key: the key definition supplies the target (and a title when the
        // topicref has none). Keys resolve in the scope the topicref sits in.
        File resolvedFile = null;
        String key = null;
        KeyDefinition def = null;
        if ((href == null || href.isEmpty()) && keyref != null && !keyref.isEmpty()) {
            key = keyref.split("/", 2)[0];
            def = keySpace.resolveLenient(key, outerScope);
            resolvedFile = keySpace.resolveHrefFileLenient(key, outerScope);
            if (def != null && def.href() != null) {
                href = def.href();
            }
            if ((navtitle == null || navtitle.isEmpty()) && def != null && def.linkText() != null && !def.linkText().isBlank()) {
                navtitle = def.linkText().trim();
            }
        } else if (href != null && !href.isEmpty() && !isExternal(href)) {
            resolvedFile = new File(contextDir, stripFragment(href));
        }

        if (href != null && !href.isEmpty()) {
            // Check if it's a map reference
            boolean isMap = "ditamap".equalsIgnoreCase(format) || element.getTagName().equals("mapref")
                    || stripFragment(href).endsWith(".ditamap");
            if (navtitle == null || navtitle.isEmpty()) {
                navtitle = isMap ? null : topicTitle(resolvedFile);
            }
            if (navtitle == null || navtitle.isEmpty()) {
                navtitle = key != null ? key : href;
            }

            if (isMap && resolvedFile != null) {
                try {
                    // Recursively parse the sub-map
                    // Note: We don't want to create a new node IF the sub-map root is what we want
                    // But usually mapref implies a node in the tree.
                    // Let's parse it and add the result as a child.
                    DitaMapNode subMapNode = parseMap(resolvedFile, false, scope);

                    // Override title if specified in the reference
                    if (locktitle != null && "yes".equalsIgnoreCase(locktitle) && navTitle(element) != null
                            && !navTitle(element).isEmpty()) {
                        subMapNode.setTitle(navTitle(element));
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
                parseChildren(element, topicNode, contextDir, scope);
            }
        } else if (key != null) {
            // A key that does not resolve (or has no target): show the key, marked
            DitaTopicNode topicNode = new DitaTopicNode(
                    (navtitle != null && !navtitle.isEmpty() ? navtitle : key) + (def == null ? " (unresolved key: " + key + ")" : ""));
            parentNode.add(topicNode);
            parseChildren(element, topicNode, contextDir, scope);
        } else if (navtitle != null && !navtitle.isEmpty()) {
            // Topicref without href (a titled container)
            DitaGroupNode groupNode = new DitaGroupNode(navtitle);
            parentNode.add(groupNode);
            parseChildren(element, groupNode, contextDir, scope);
        } else {
            // No target and no title: its children appear in place
            parseChildren(element, parentNode, contextDir, scope);
        }
    }

    /**
     * Surface a {@code <ditavalref>} (DITA 1.3 branch filtering) as a badged leaf
     * under its parent topicref/group. Not recursed into (nested branches AND-combine
     * — out of scope for the in-process inventory, like {@code BranchModel}).
     */
    private void handleDitavalref(Element element, DitaNode parentNode) {
        String href = element.getAttribute("href");
        // The label is Branch.label()'s, asked of a Branch rather than worked out again here, so the Map
        // Explorer cannot drift from the status-bar switcher and the preview for the same branch. It had:
        // this copy read dvrResourcePrefix only, and a suffixed branch was named after its DITAVAL file in
        // all three places (reported 2026-09-30).
        String label = new com.dogsbay.dogsbayaieditor.links.Branch(
                null, href, href == null || href.isEmpty() ? null : new File(href),
                null, null,
                directChildText(element, "ditavalmeta", "dvrResourcePrefix"),
                directChildText(element, "ditavalmeta", "dvrResourceSuffix")).label();
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

    private void handleTopicGroup(Element element, DitaNode parentNode, File contextDir, String outerScope) {
        String navtitle = navTitle(element);
        String scope = withScope(outerScope, element);
        boolean resourceOnly = "resource-only".equals(element.getAttribute("processing-role"));

        if (navtitle == null || navtitle.isEmpty()) {
            if (!resourceOnly) {
                // A group without a title adds no level (DITA-OT's table of contents shows its children in place)
                parseChildren(element, parentNode, contextDir, scope);
                return;
            }
            navtitle = "(resource-only)";
        }
        DitaGroupNode groupNode = new DitaGroupNode(navtitle);
        parentNode.add(groupNode);
        parseChildren(element, groupNode, contextDir, scope);
    }

    /** The element's navigation title: @navtitle, else topicmeta/navtitle or navtitle; null when none. */
    private String navTitle(Element element) {
        String navtitle = element.getAttribute("navtitle");
        if (navtitle == null || navtitle.isEmpty()) {
            navtitle = getChildNavTitle(element);
        }
        return navtitle == null || navtitle.isEmpty() ? null : navtitle;
    }

    /** The scope path inside an element with {@code @keyscope} (its first name), or the outer scope. */
    private static String withScope(String outer, Element element) {
        String keyscope = element.getAttribute("keyscope");
        if (keyscope == null || keyscope.isBlank()) {
            return outer;
        }
        String name = keyscope.trim().split("\\s+")[0];
        return outer == null || outer.isEmpty() ? name : outer + "." + name;
    }

    private static boolean isExternal(String href) {
        return href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }

    private static String stripFragment(String href) {
        int hash = href.indexOf('#');
        return hash >= 0 ? href.substring(0, hash) : href;
    }

    /**
     * A topic file's first {@code <title>} as text, or null, remembered for this parse.
     *
     * <p>The reading is {@link com.dogsbay.dogsbayaieditor.links.TopicTitle}'s, shared with the preview, which
     * needs the same answer for a keyref with no link text. The cache stays here: it is cleared with the rest
     * of this parser's state, and the preview's is cleared on its own terms.
     */
    private String topicTitle(File topic) {
        if (topic == null || !topic.isFile()) {
            return null;
        }
        return topicTitles.computeIfAbsent(topic,
                f -> com.dogsbay.dogsbayaieditor.links.TopicTitle.read(f, this::keyText));
    }

    /**
     * The text a key stands for, for a title written with one
     * ({@code <title>Installing <keyword keyref="product-name"/></title>}).
     *
     * <p>{@code keywordText} first and {@code linkText} second, which is the order DITA-OT's keyref step reads
     * them in for an inline element. The key space is this parse's own, so the answer is the one a build of this
     * map would give.
     */
    private String keyText(String key) {
        com.dogsbay.dogsbayaieditor.links.KeyDefinition def = keySpace.resolve(key);
        if (def == null) {
            return null;
        }
        return def.keywordText() != null ? def.keywordText() : def.linkText();
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
