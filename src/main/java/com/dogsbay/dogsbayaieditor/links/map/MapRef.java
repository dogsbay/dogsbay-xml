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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;

/**
 * One navigation node in a {@link MapModel} — a {@code <topicref>} (or
 * {@code <topichead>}/{@code <mapref>}/{@code <chapter>}/…), bound to its source
 * {@link XElement} so edits map back to byte ranges in the map text. Captures
 * <em>every</em> attribute (not just href/navtitle), its resolved target file, its
 * children, and a stable selector ({@code @id} when present, else a 1-based
 * child-path like {@code /1/3/2}).
 */
public final class MapRef {

    private final XElement element;
    private final String type;
    private final Map<String, String> attrs;
    private final File resolvedFile;
    private final String navtitle;
    private final MapRef parent;
    private final String path;
    private final List<MapRef> children = new ArrayList<>();

    MapRef(XElement element, File mapDir, MapRef parent, String path) {
        this.element = element;
        this.type = element.getName();
        this.attrs = readAttrs(element);
        this.parent = parent;
        this.path = path;
        this.navtitle = readNavtitle(element, attrs);
        this.resolvedFile = resolveHref(attrs.get("href"), mapDir);
    }

    void addChild(MapRef child) {
        children.add(child);
    }

    private static Map<String, String> readAttrs(XElement el) {
        Map<String, String> out = new LinkedHashMap<>();
        XAttribute[] as = el.getAttributes();
        if (as != null) {
            for (XAttribute a : as) {
                out.put(a.getName(), a.getValue());
            }
        }
        return out;
    }

    /** {@code @navtitle}, else the {@code <topicmeta><navtitle>} text, else null. */
    private static String readNavtitle(XElement el, Map<String, String> attrs) {
        String attr = attrs.get("navtitle");
        if (attr != null && !attr.isBlank()) {
            return attr;
        }
        for (Object o : el.elements()) {
            if (o instanceof XElement child && "topicmeta".equals(child.getName())) {
                for (Object g : child.elements()) {
                    if (g instanceof XElement nt && "navtitle".equals(nt.getName())) {
                        String t = nt.getText();
                        return t == null || t.isBlank() ? null : t.trim();
                    }
                }
            }
        }
        return null;
    }

    private static File resolveHref(String href, File mapDir) {
        if (href == null || href.isBlank() || href.startsWith("#")
                || href.contains("://") || mapDir == null) {
            return null; // key/external/peer/empty — no local file
        }
        String path = href;
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (path.isBlank()) {
            return null;
        }
        return new File(mapDir, path).getAbsoluteFile().toPath().normalize().toFile();
    }

    /** The source element (carries the byte offsets via its position accessors). */
    public XElement element() {
        return element;
    }

    /** Local name: {@code topicref}, {@code topichead}, {@code mapref}, {@code chapter}, … */
    public String type() {
        return type;
    }

    /** All attributes in document order (immutable view via the backing map). */
    public Map<String, String> attrs() {
        return attrs;
    }

    public String attr(String name) {
        return attrs.get(name);
    }

    public String href() {
        return attrs.get("href");
    }

    /** The {@code @href} target resolved against the map directory, or null. */
    public File resolvedFile() {
        return resolvedFile;
    }

    public String navtitle() {
        return navtitle;
    }

    public String id() {
        return attrs.get("id");
    }

    public MapRef parent() {
        return parent;
    }

    public List<MapRef> children() {
        return List.copyOf(children);
    }

    /** Stable selector: {@code @id} when present, else the 1-based child-path. */
    public String selector() {
        String id = id();
        return id != null && !id.isBlank() ? id : path;
    }

    /** The 1-based child-path (e.g. {@code /1/3/2}); {@code /} for the root. */
    public String path() {
        return path;
    }

    /** Start byte offset of this element's start tag in the map text. */
    public int startOffset() {
        return element.getElementStartPosition();
    }

    /** End byte offset (exclusive) of this element in the map text. */
    public int endOffset() {
        return element.getElementEndPosition();
    }

    /** True when this node is an ancestor of (or equal to) {@code other}. */
    public boolean isAncestorOf(MapRef other) {
        for (MapRef p = other; p != null; p = p.parent) {
            if (p == this) {
                return true;
            }
        }
        return false;
    }
}
