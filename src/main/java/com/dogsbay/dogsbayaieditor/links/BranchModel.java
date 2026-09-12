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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * Enumerates the DITA 1.3 branch-filter variants in a map — every
 * {@code <ditavalref>}, the topicref/topicgroup it filters, its DITAVAL, and the
 * generated keyscope (from {@code <ditavalmeta>}). GUI-free; parses with the headless
 * positioned document model. This is the variant <em>inventory</em> only — DITA-OT
 * performs the actual subtree duplication and resource/key renaming.
 */
public final class BranchModel {

    private BranchModel() {}

    /** All branches declared in {@code mapFile} (empty if none / unreadable). */
    public static List<Branch> enumerate(File mapFile) throws IOException {
        if (mapFile == null || !mapFile.isFile()) {
            return List.of();
        }
        String text = Files.readString(mapFile.toPath(), StandardCharsets.UTF_8);
        XElement root;
        try {
            root = new DogsBayDocument(text).getRoot();
        } catch (Exception e) {
            throw new IOException("Failed to parse map: " + mapFile, e);
        }
        if (root == null) {
            return List.of();
        }
        List<Branch> out = new ArrayList<>();
        walk(root, mapFile.getAbsoluteFile().getParentFile(), out);
        return out;
    }

    private static void walk(XElement parent, File mapDir, List<Branch> out) {
        for (Object o : parent.elements()) {
            if (!(o instanceof XElement child)) {
                continue;
            }
            if ("ditavalref".equals(child.getName())) {
                out.add(build(child, parent, mapDir));
            } else {
                walk(child, mapDir, out); // a ditavalref nests under topicref/topicgroup
            }
        }
    }

    private static Branch build(XElement ditavalref, XElement appliesToEl, File mapDir) {
        String href = attr(ditavalref, "href");
        XElement meta = firstChild(ditavalref, "ditavalmeta");
        String kPrefix = meta == null ? null : childText(meta, "dvrKeyscopePrefix");
        String kSuffix = meta == null ? null : childText(meta, "dvrKeyscopeSuffix");
        String rPrefix = meta == null ? null : childText(meta, "dvrResourcePrefix");
        String rSuffix = meta == null ? null : childText(meta, "dvrResourceSuffix");
        return new Branch(appliesTo(appliesToEl), href, resolve(href, mapDir),
                kPrefix, kSuffix, rPrefix, rSuffix);
    }

    private static String appliesTo(XElement el) {
        String href = attr(el, "href");
        if (href != null) {
            return href;
        }
        String navtitle = attr(el, "navtitle");
        return navtitle != null ? navtitle : el.getName();
    }

    private static File resolve(String href, File mapDir) {
        if (href == null || href.isBlank() || href.startsWith("#") || href.contains("://")
                || mapDir == null) {
            return null;
        }
        String path = href;
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        return path.isBlank() ? null
                : new File(mapDir, path).getAbsoluteFile().toPath().normalize().toFile();
    }

    private static XElement firstChild(XElement parent, String name) {
        for (Object o : parent.elements()) {
            if (o instanceof XElement e && name.equals(e.getName())) {
                return e;
            }
        }
        return null;
    }

    private static String childText(XElement parent, String name) {
        XElement c = firstChild(parent, name);
        if (c == null) {
            return null;
        }
        String t = c.getText();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static String attr(XElement el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isBlank() ? null : v;
    }
}
