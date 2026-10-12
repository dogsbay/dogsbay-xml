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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.dogsbay.dogsbayaieditor.links.KeySpace;

/**
 * Resolves conref/conkeyref transclusion for the styled preview: every
 * element carrying a resolvable reference is replaced in place by a deep
 * copy of its target element, tagged {@code dbx-conref-source} so the
 * stylesheet can render a subtle reuse marker. Unresolvable references
 * (missing file/fragment/key, cycles, topic-level pulls) keep their
 * attributes and fall through to the badge rendering.
 *
 * <p>Pre-transform DOM pass, like {@link DitavalFilter}: relative hrefs
 * resolve against each <em>containing file's</em> directory, conkeyref
 * needs the Java {@link KeySpace}, and cycle/depth control stays in one
 * place. The active DITAVAL filter is applied to every loaded target
 * document so excluded profiled content can't leak in via reuse.</p>
 *
 * @author DogsBay Ltd
 */
final class ConrefTranscluder {

    private static final int MAX_DEPTH = 8;

    private final KeySpace keySpace;
    private final DitavalFilter filter;
    private final boolean showChanges;
    private final Map<String, Document> targetCache = new HashMap<>();

    private ConrefTranscluder(KeySpace keySpace, DitavalFilter filter, boolean showChanges) {
        this.keySpace = keySpace != null ? keySpace : KeySpace.empty();
        this.filter = filter != null ? filter : DitavalFilter.empty();
        this.showChanges = showChanges;
    }

    /**
     * Resolves all transclusions in {@code doc} in place.
     *
     * @param baseDir directory of the previewed file, for relative hrefs;
     *                null limits resolution to same-document conrefs
     */
    static void apply(Document doc, File baseDir, KeySpace keySpace, DitavalFilter filter) {
        apply(doc, baseDir, keySpace, filter, false);
    }

    /** As {@link #apply(Document, File, KeySpace, DitavalFilter)}; {@code showChanges} keeps review marks in targets. */
    static void apply(Document doc, File baseDir, KeySpace keySpace, DitavalFilter filter, boolean showChanges) {
        new ConrefTranscluder(keySpace, filter, showChanges)
                .resolveChildren(doc.getDocumentElement(), baseDir, new ArrayDeque<>());
    }

    /** Depth-first walk; replaced subtrees are resolved by the replacer. */
    private void resolveChildren(Element parent, File baseDir, Deque<String> chain) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();      // capture before any replace
            if (child instanceof Element element) {
                if (!element.getAttribute("conref").isEmpty()
                        || !element.getAttribute("conkeyref").isEmpty()) {
                    if (!resolveReference(element, baseDir, chain)) {
                        // Unresolved: badge renders; fallback content may
                        // still carry references of its own.
                        resolveChildren(element, baseDir, chain);
                    }
                } else {
                    resolveChildren(element, baseDir, chain);
                }
            }
            child = next;
        }
    }

    /**
     * Replaces {@code element} with a copy of its reference target.
     * False when the reference can't (or shouldn't) be resolved.
     */
    private boolean resolveReference(Element element, File baseDir, Deque<String> chain) {
        if (chain.size() >= MAX_DEPTH) {
            return false;
        }
        String conref = element.getAttribute("conref");
        String conkeyref = element.getAttribute("conkeyref");

        File targetFile;        // null = same document
        String fragment;
        String label;
        if (!conref.isEmpty()) {
            label = conref;
            int hash = conref.indexOf('#');
            if (hash < 0) {
                return false;                       // whole-file pull — badge
            }
            String path = conref.substring(0, hash);
            fragment = conref.substring(hash + 1);
            if (path.isEmpty()) {
                targetFile = null;
            } else if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                return false;                       // external scheme — badge
            } else {
                File file = new File(path);
                if (!file.isAbsolute()) {
                    if (baseDir == null) {
                        return false;
                    }
                    file = new File(baseDir, path);
                }
                targetFile = file;
            }
        } else {
            label = "key " + conkeyref;
            int slash = conkeyref.indexOf('/');
            if (slash < 0) {
                return false;                       // topic-level pull — badge
            }
            String key = conkeyref.substring(0, slash);
            fragment = conkeyref.substring(slash + 1);
            // Lenient: a bare conkeyref to a key unique to one @keyscope resolves
            // (matches the preview keys-block in DitaConverter).
            targetFile = keySpace.resolveHrefFileLenient(key, "");
            if (targetFile == null) {
                return false;                       // key unresolved — badge
            }
        }
        if (fragment.isEmpty()) {
            return false;
        }

        String chainKey = canonicalKey(targetFile, baseDir) + "#" + fragment;
        if (chain.contains(chainKey)) {
            return false;                           // cycle — badge
        }

        Document targetDoc;
        File targetDir;
        if (targetFile == null) {
            targetDoc = element.getOwnerDocument();
            targetDir = baseDir;
        } else {
            if (!targetFile.isFile()) {
                return false;
            }
            targetDoc = loadTarget(targetFile);
            if (targetDoc == null) {
                return false;
            }
            targetDir = targetFile.getParentFile();
        }

        Element target = findByFragment(targetDoc, fragment);
        if (target == null || target == element) {
            return false;
        }

        Element copy = (Element) element.getOwnerDocument().importNode(target, true);
        // Target's own conref (a chain) stays on the copy — the recursive
        // pass below resolves it against the TARGET's directory.
        copy.setAttribute("dbx-conref-source", label);
        String localId = element.getAttribute("id");
        if (!localId.isEmpty()) {
            copy.setAttribute("id", localId);       // local id keeps anchors working
        }
        element.getParentNode().replaceChild(copy, element);

        chain.push(chainKey);
        boolean chained = (!copy.getAttribute("conref").isEmpty()
                        || !copy.getAttribute("conkeyref").isEmpty())
                && resolveReference(copy, targetDir, chain);
        if (!chained) {
            // chained resolution replaced `copy` and recursed already
            resolveChildren(copy, targetDir, chain);
        }
        chain.pop();
        return true;
    }

    private String canonicalKey(File targetFile, File baseDir) {
        File file = targetFile != null ? targetFile
                : (baseDir != null ? new File(baseDir, ".") : new File("."));
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    /** Loads (and DITAVAL-filters) a target document, cached per file. */
    private Document loadTarget(File file) {
        String key;
        try {
            key = file.getCanonicalPath();
        } catch (Exception e) {
            key = file.getAbsolutePath();
        }
        if (targetCache.containsKey(key)) {
            return targetCache.get(key);
        }
        Document doc = null;
        try {
            // Reused content renders as the preview does: proposals accepted and
            // comments gone, or the marks kept when the preview shows changes.
            String text = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            if (!showChanges) {
                text = DitaConverter.acceptedView(text);
            }
            org.xml.sax.InputSource in = new org.xml.sax.InputSource(new java.io.StringReader(text));
            in.setSystemId(file.toURI().toString());
            doc = DitavalFilter.hardenedBuilder().parse(in);
            if (!filter.isEmpty()) {
                filter.apply(doc);
            }
        } catch (Exception e) {
            // unparsable target — badge
        }
        targetCache.put(key, doc);
        return doc;
    }

    /**
     * The element a {@code topicId/elemId} (or bare {@code id}) fragment
     * names. Strict topic-scoped lookup first; falls back to the first
     * element with the trailing id anywhere (no DTD, no getElementById).
     */
    private static Element findByFragment(Document doc, String fragment) {
        int slash = fragment.indexOf('/');
        if (slash >= 0) {
            String topicId = fragment.substring(0, slash);
            String elemId = fragment.substring(slash + 1);
            Element topic = findById(doc.getDocumentElement(), topicId);
            if (topic != null) {
                Element strict = findById(topic, elemId);
                if (strict != null && strict != topic) {
                    return strict;
                }
            }
            return findById(doc.getDocumentElement(), elemId);
        }
        return findById(doc.getDocumentElement(), fragment);
    }

    private static Element findById(Element root, String id) {
        if (id.equals(root.getAttribute("id"))) {
            return root;
        }
        Node child = root.getFirstChild();
        while (child != null) {
            if (child instanceof Element element) {
                Element found = findById(element, id);
                if (found != null) {
                    return found;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
