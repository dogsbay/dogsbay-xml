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

package com.dogsbay.dogsbayaieditor.links.map;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * A round-trippable, GUI-free model of a DITA map: the {@code <topicref>} tree
 * (every navigation element, with all attributes and source offsets) over the
 * map's exact source text, so a {@link MapEditor} can splice formatting-preserving
 * textual edits rather than re-serialize the DOM.
 *
 * <p>Parsed via the headless positioned document model ({@link DogsBayDocument},
 * which stamps each element's byte offsets) — the same path {@code
 * ProjectContextLoader} uses. {@link DitaMapParser}-style display parsing is
 * unaffected; this is the editing view.
 */
public final class MapModel {

    /** Navigation elements that form the topicref tree (matched by local name —
     *  {@code @class} is DTD-defaulted and absent with external DTD off). */
    private static final Set<String> NAV_ELEMENTS = Set.of(
            "topicref", "topichead", "topicgroup", "mapref", "keydef", "glossref",
            "anchorref", "navref",
            // bookmap navigation
            "chapter", "appendix", "appendices", "part", "preface", "notices",
            "frontmatter", "backmatter", "booklists", "glossarylist", "abbrevlist",
            "bibliolist", "figurelist", "indexlist", "tablelist", "toc", "trademarklist");

    private final File file;
    private final String text;
    private final MapRef root;
    private final String rootType;

    private MapModel(File file, String text, MapRef root, String rootType) {
        this.file = file;
        this.text = text;
        this.root = root;
        this.rootType = rootType;
    }

    /** Parse {@code mapFile} into a positioned topicref tree. The source text is
     *  read as UTF-8 (the DITA default) and kept verbatim as the splice buffer the
     *  element offsets index into. */
    public static MapModel parse(File mapFile) throws IOException {
        String text = Files.readString(mapFile.toPath(), StandardCharsets.UTF_8);
        XElement rootEl;
        try {
            // The String constructor runs setText → parses and stamps every
            // element's byte offsets into this exact text (unlike load-from-URL).
            DogsBayDocument doc = new DogsBayDocument(text);
            rootEl = doc.getRoot();
        } catch (Exception e) {
            throw new IOException("Failed to parse map: " + mapFile, e);
        }
        if (rootEl == null) {
            throw new IOException("Empty or invalid map: " + mapFile);
        }
        File mapDir = mapFile.getAbsoluteFile().getParentFile();
        MapRef rootRef = new MapRef(rootEl, mapDir, null, "/");
        buildChildren(rootEl, rootRef, mapDir);
        return new MapModel(mapFile, text, rootRef, rootEl.getName());
    }

    private static void buildChildren(XElement parentEl, MapRef parentRef, File mapDir) {
        int index = 0;
        for (Object o : parentEl.elements()) {
            if (!(o instanceof XElement child) || !NAV_ELEMENTS.contains(child.getName())) {
                continue;
            }
            index++;
            String path = ("/".equals(parentRef.path()) ? "" : parentRef.path()) + "/" + index;
            MapRef ref = new MapRef(child, mapDir, parentRef, path);
            parentRef.addChild(ref);
            buildChildren(child, ref, mapDir);
        }
    }

    public File file() {
        return file;
    }

    /** The exact source text the element offsets index into. */
    public String text() {
        return text;
    }

    /** The map/bookmap root, wrapping the {@code <map>} element. */
    public MapRef root() {
        return root;
    }

    /** {@code "map"} or {@code "bookmap"} (the root element's local name). */
    public String rootType() {
        return rootType;
    }

    /** Every navigation node, depth-first (excluding the root). */
    public List<MapRef> refs() {
        List<MapRef> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(MapRef ref, List<MapRef> out) {
        for (MapRef child : ref.children()) {
            out.add(child);
            collect(child, out);
        }
    }

    /**
     * Resolve a selector to a node: the root for {@code "/"}; a 1-based child-path
     * like {@code /1/3} (walking {@code root}'s children); else an {@code @id} match.
     * Returns null if unresolved.
     */
    public MapRef find(String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        if ("/".equals(selector)) {
            return root;
        }
        if (selector.startsWith("/")) {
            MapRef cur = root;
            for (String part : selector.substring(1).split("/")) {
                int idx;
                try {
                    idx = Integer.parseInt(part) - 1;
                } catch (NumberFormatException e) {
                    return null;
                }
                List<MapRef> kids = cur.children();
                if (idx < 0 || idx >= kids.size()) {
                    return null;
                }
                cur = kids.get(idx);
            }
            return cur;
        }
        for (MapRef ref : refs()) {
            if (selector.equals(ref.id())) {
                return ref;
            }
        }
        return null;
    }
}
