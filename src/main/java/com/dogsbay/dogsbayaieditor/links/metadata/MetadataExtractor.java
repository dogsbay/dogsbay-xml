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

package com.dogsbay.dogsbayaieditor.links.metadata;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.dogsbay.dogsbayaieditor.validate.LineNumberedDom;

/**
 * Reads a DITA file's metadata into a {@link MetadataSnapshot}: a topic's
 * {@code <prolog>} or a map/keydef's top-level {@code <topicmeta>}/{@code <bookmeta>}.
 * Fields are matched by local name anywhere within the container (so the
 * {@code <metadata>}, {@code <keywords>}, {@code <prodinfo>}, {@code <critdates>}
 * wrappers don't need modelling), and one element may yield several fields (e.g.
 * audience type/job/experiencelevel).
 *
 * <p>Parsed via {@link LineNumberedDom} (external DTD off, no catalog needed) so each
 * value carries its 1-based source line; unreadable files yield
 * {@link MetadataSnapshot#empty()}.
 */
public final class MetadataExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataExtractor.class);

    private MetadataExtractor() {}

    public static MetadataSnapshot extract(File file) {
        Document doc;
        try {
            doc = LineNumberedDom.parse(file);
        } catch (Exception e) {
            LOG.debug("Could not read metadata from {}: {}", file, e.toString());
            return MetadataSnapshot.empty();
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return MetadataSnapshot.empty();
        }
        String rootType = localName(root);
        int rootLine = LineNumberedDom.lineOf(root);

        Element container = firstChild(root, "prolog");
        String containerName = "prolog";
        if (container == null) {
            container = firstChild(root, "topicmeta");
            containerName = "topicmeta";
        }
        if (container == null) {
            container = firstChild(root, "bookmeta");
            containerName = "bookmeta";
        }
        if (container == null) {
            return new MetadataSnapshot(rootType, "none", rootLine, Map.of(), Map.of());
        }

        Map<MetadataField, List<String>> values = new EnumMap<>(MetadataField.class);
        Map<MetadataField, Integer> lines = new EnumMap<>(MetadataField.class);
        collect(container, values, lines);
        int anchorLine = LineNumberedDom.lineOf(container);
        if (anchorLine < 0) {
            anchorLine = rootLine;
        }
        return new MetadataSnapshot(rootType, containerName, anchorLine, values, lines);
    }

    /** The first direct child element with the given local name, or null. */
    private static Element firstChild(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            if (localName.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    /** Walk the container subtree, recording each tracked field's value + line. */
    private static void collect(Element el, Map<MetadataField, List<String>> values,
            Map<MetadataField, Integer> lines) {
        for (Element child : childElements(el)) {
            for (MetadataField field : MetadataField.fieldsForElement(localName(child))) {
                String value = valueOf(field, child);
                if (value != null && !value.isBlank()) {
                    values.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
                    lines.putIfAbsent(field, LineNumberedDom.lineOf(child)); // first occurrence
                }
            }
            collect(child, values, lines);
        }
    }

    private static String valueOf(MetadataField field, Element el) {
        return switch (field.kind()) {
            case TEXT -> el.getTextContent() == null ? null : el.getTextContent().trim();
            case ATTR -> el.getAttribute(field.attr());
            case NAME_CONTENT -> pair(el.getAttribute("name"), el.getAttribute("content"));
            case NAME_VALUE -> pair(el.getAttribute("name"), el.getAttribute("value"));
        };
    }

    private static String pair(String name, String value) {
        if ((name == null || name.isEmpty()) && (value == null || value.isEmpty())) {
            return null;
        }
        return (name == null ? "" : name) + "=" + (value == null ? "" : value);
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    private static String localName(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
    }
}
