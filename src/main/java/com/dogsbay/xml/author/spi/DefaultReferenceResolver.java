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

package com.dogsbay.xml.author.spi;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * Filesystem-based resolver for standalone use: hrefs resolve against the
 * document URI, keys come from a best-effort walk of the root map closure
 * (keydef/@keys with keyword/navtitle text, submaps followed), and conrefs
 * load the target file and find the id path. All parsing is non-validating
 * and never fetches external DTDs.
 */
public class DefaultReferenceResolver implements ReferenceResolver {

    private final URI documentUri;
    private final File rootMap;
    private final Map<String, Document> documentCache = new HashMap<>();
    private Map<String, String> keyTexts;
    private Map<String, String> keyHrefs;

    /**
     * @param documentUri the edited document's URI (not its directory) —
     *                    relative hrefs and same-file conrefs resolve against it
     * @param rootMap     optional DITA root map for key resolution
     */
    public DefaultReferenceResolver(URI documentUri, File rootMap) {
        this.documentUri = documentUri;
        this.rootMap = rootMap;
    }

    @Override
    public String keyText(String key) {
        ensureKeys();
        String text = keyTexts.get(key);
        if (text == null) {
            // href-only key definitions (typical for glossary keys): the
            // display text comes from the target topic's glossterm/title
            String href = keyHrefs.get(key);
            if (href != null) {
                text = targetDisplayText(href);
                if (text != null) {
                    keyTexts.put(key, text);
                }
            }
        }
        return text;
    }

    /** glossterm (or title) of the topic an absolute key href points at. */
    private String targetDisplayText(String hrefUri) {
        try {
            String withoutFragment = hrefUri;
            int hash = hrefUri.indexOf('#');
            if (hash >= 0) {
                withoutFragment = hrefUri.substring(0, hash);
            }
            Document doc = load(new java.net.URI(withoutFragment));
            if (doc == null) {
                return null;
            }
            Element root = doc.getRootElement();
            Element glossterm = root.element("glossterm");
            if (glossterm != null && !glossterm.getTextTrim().isEmpty()) {
                return glossterm.getTextTrim();
            }
            Element title = root.element("title");
            return title != null && !title.getTextTrim().isEmpty() ? title.getTextTrim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String keyHref(String key) {
        ensureKeys();
        return keyHrefs.get(key);
    }

    @Override
    public URI resolveHref(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        try {
            return documentUri == null ? new URI(href) : documentUri.resolve(href.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Element resolveConref(String conref) {
        if (conref == null || conref.isBlank()) {
            return null;
        }
        String filePart = conref;
        String idPath = null;
        int hash = conref.indexOf('#');
        if (hash >= 0) {
            filePart = conref.substring(0, hash);
            idPath = conref.substring(hash + 1);
        }
        URI target = filePart.isEmpty() ? documentUri : resolveHref(filePart);
        Document doc = load(target);
        if (doc == null) {
            return null;
        }
        if (idPath == null || idPath.isEmpty()) {
            return doc.getRootElement();
        }
        String[] ids = idPath.split("/", 2);
        Element scope = findById(doc.getRootElement(), ids[0]);
        if (scope == null) {
            return null;
        }
        return ids.length == 1 ? scope : findById(scope, ids[1]);
    }

    private Element findById(Element scope, String id) {
        if (id.equals(scope.attributeValue("id"))) {
            return scope;
        }
        for (Object child : scope.elements()) {
            Element found = findById((Element) child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Key space (best-effort root-map walk)
    // ------------------------------------------------------------------

    private synchronized void ensureKeys() {
        if (keyTexts != null) {
            return;
        }
        keyTexts = new LinkedHashMap<>();
        keyHrefs = new LinkedHashMap<>();
        if (rootMap != null && rootMap.isFile()) {
            walkMap(rootMap, new HashSet<>());
        }
    }

    private void walkMap(File mapFile, Set<String> visited) {
        String canonical;
        try {
            canonical = mapFile.getCanonicalPath();
        } catch (Exception e) {
            canonical = mapFile.getAbsolutePath();
        }
        if (!visited.add(canonical)) {
            return;
        }
        Document doc = load(mapFile.toURI());
        if (doc == null) {
            return;
        }
        collectKeys(doc.getRootElement(), mapFile, visited);
    }

    private void collectKeys(Element element, File mapFile, Set<String> visited) {
        String keys = element.attributeValue("keys");
        if (keys != null && !keys.isBlank()) {
            String text = keyDisplayText(element);
            String href = element.attributeValue("href");
            for (String key : keys.trim().split("\\s+")) {
                // DITA: the first definition of a key wins
                if (text != null) {
                    keyTexts.putIfAbsent(key, text);
                }
                if (href != null && !href.isBlank()) {
                    // map hrefs are map-relative — absolutize at collection time
                    keyHrefs.putIfAbsent(key, absolutize(mapFile, href));
                }
            }
        }
        String format = element.attributeValue("format");
        String href = element.attributeValue("href");
        if (href != null && ("ditamap".equals(format) || "mapref".equals(element.getName()))) {
            walkMap(new File(mapFile.getParentFile(), href), visited);
        }
        for (Object child : element.elements()) {
            collectKeys((Element) child, mapFile, visited);
        }
    }

    private String absolutize(File mapFile, String href) {
        if (href.matches("(?i)^[a-z][a-z0-9+.\\-]*:.*")) {
            return href; // already a URI (http:, file:, ...)
        }
        return new File(mapFile.getParentFile(), href).toURI().toString();
    }

    /** keyword text, then navtitle, from the topicmeta of a key definition. */
    private String keyDisplayText(Element keydef) {
        Element meta = keydef.element("topicmeta");
        if (meta == null) {
            return null;
        }
        Element keywords = meta.element("keywords");
        if (keywords != null) {
            Element keyword = keywords.element("keyword");
            if (keyword != null && !keyword.getTextTrim().isEmpty()) {
                return keyword.getTextTrim();
            }
        }
        Element navtitle = meta.element("navtitle");
        if (navtitle != null && !navtitle.getTextTrim().isEmpty()) {
            return navtitle.getTextTrim();
        }
        Element linktext = meta.element("linktext");
        if (linktext != null && !linktext.getTextTrim().isEmpty()) {
            return linktext.getTextTrim();
        }
        return null;
    }

    private Document load(URI uri) {
        if (uri == null) {
            return null;
        }
        String key = uri.toString();
        if (documentCache.containsKey(key)) {
            return documentCache.get(key);
        }
        Document doc = null;
        try {
            SAXReader reader = new SAXReader();
            reader.setValidation(false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setIncludeExternalDTDDeclarations(false);
            doc = reader.read(new File(uri));
        } catch (Exception e) {
            // unreadable target: resolve nothing
        }
        documentCache.put(key, doc);
        return doc;
    }
}
