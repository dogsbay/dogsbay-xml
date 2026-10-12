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

package com.dogsbay.xml.browser;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DITAVAL exclusion rules for the preview's pre-transform filter.
 *
 * <p>Supported subset: {@code <prop att val action>} with
 * {@code action="exclude"} (anything else counts as include; flagging is
 * ignored). Att-wide rules ({@code <prop att="platform" action="exclude"/>}
 * with no {@code val}) exclude every value of that attribute unless an
 * explicit include rule names the value.
 *
 * <p>Exclusion semantics: an element is excluded when <b>any</b> of its
 * profiling attributes ({@code audience}, {@code platform}, {@code product},
 * {@code otherprops}, {@code props}) has <b>all</b> of its space-separated
 * tokens excluded.
 */
public final class DitavalFilter {

    private static final String[] PROFILING_ATTRS = {
            "audience", "platform", "product", "otherprops", "props"};

    /** att -> (val -> action), explicit per-value rules */
    private final Map<String, Map<String, String>> valueRules = new HashMap<>();
    /** att -> default action for unlisted values of that attribute */
    private final Map<String, String> attWideRules = new HashMap<>();

    private DitavalFilter() {
    }

    /** A filter that excludes nothing. */
    public static DitavalFilter empty() {
        return new DitavalFilter();
    }

    /**
     * Parses a .ditaval file. Unreadable or malformed files yield an empty
     * filter rather than throwing.
     */
    public static DitavalFilter parse(File ditavalFile) {
        DitavalFilter filter = new DitavalFilter();
        if (ditavalFile == null || !ditavalFile.isFile()) {
            return filter;
        }
        try {
            Document doc = hardenedBuilder().parse(ditavalFile);
            NodeList props = doc.getElementsByTagName("prop");
            for (int i = 0; i < props.getLength(); i++) {
                Element prop = (Element) props.item(i);
                String att = prop.getAttribute("att");
                String val = prop.getAttribute("val");
                String action = prop.getAttribute("action");
                if (att.isEmpty() || action.isEmpty()) {
                    continue;
                }
                if (val.isEmpty()) {
                    filter.attWideRules.put(att, action);
                } else {
                    filter.valueRules
                            .computeIfAbsent(att, k -> new HashMap<>())
                            .put(val, action);
                }
            }
        } catch (Exception e) {
            // bad ditaval -> behave as "no filtering"
            return new DitavalFilter();
        }
        return filter;
    }

    public boolean isEmpty() {
        return valueRules.isEmpty() && attWideRules.isEmpty();
    }

    /** Is this single token of this attribute excluded? */
    boolean isValueExcluded(String att, String value) {
        Map<String, String> rules = valueRules.get(att);
        if (rules != null) {
            String action = rules.get(value);
            if (action != null) {
                return "exclude".equals(action);
            }
        }
        return "exclude".equals(attWideRules.get(att));
    }

    /** Should this element be removed? */
    public boolean isExcluded(Element element) {
        if (isEmpty()) {
            return false;
        }
        for (String att : PROFILING_ATTRS) {
            if (isAttributeFullyExcluded(att, element.getAttribute(att))) {
                return true;
            }
        }
        return false;
    }

    /**
     * SAX variant of {@link #isExcluded(Element)} — used to filter maps
     * during key-space extraction (LinkExtractor's ElementExclusion hook).
     */
    public boolean isExcluded(org.xml.sax.Attributes attributes) {
        if (isEmpty()) {
            return false;
        }
        for (String att : PROFILING_ATTRS) {
            if (isAttributeFullyExcluded(att, attributes.getValue(att))) {
                return true;
            }
        }
        return false;
    }

    /** All space-separated tokens of this attribute value excluded? */
    private boolean isAttributeFullyExcluded(String att, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        for (String token : value.trim().split("\\s+")) {
            if (!isValueExcluded(att, token)) {
                return false;
            }
        }
        return true;
    }

    /** Removes every excluded element (and its subtree) from the document. */
    public void apply(Document doc) {
        if (isEmpty() || doc.getDocumentElement() == null) {
            return;
        }
        List<Element> toRemove = new ArrayList<>();
        collectExcluded(doc.getDocumentElement(), toRemove);
        for (Element el : toRemove) {
            Node parent = el.getParentNode();
            if (parent != null) {
                parent.removeChild(el);
            }
        }
    }

    private void collectExcluded(Element element, List<Element> out) {
        if (isExcluded(element)) {
            out.add(element);
            return;                      // children go with the subtree
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                collectExcluded((Element) child, out);
            }
        }
    }

    /** Non-validating, entity-suppressed builder (no DTD fetching). */
    static DocumentBuilder hardenedBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) ->
                new org.xml.sax.InputSource(new StringReader("")));
        return builder;
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // EntityResolver still guards
        }
    }
}
