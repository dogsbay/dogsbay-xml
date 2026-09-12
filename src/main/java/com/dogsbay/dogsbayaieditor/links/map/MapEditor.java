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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dogsbay.dogsbayaieditor.links.Reference;
import com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex;

/**
 * GUI-free core that turns a structural intent (set-attr / insert / remove / move)
 * into a formatting-preserving {@link MapEditPlan} — textual splices of the map's
 * exact source, never a DOM re-serialize. Moved subtrees are cut and pasted
 * verbatim (formatting + comments survive); cross-map moves rebase the moved
 * subtree's {@code @href}/{@code @conref} to the destination map's directory; removal
 * surfaces inbound-reference warnings via {@link ReverseLinkIndex}.
 */
public final class MapEditor {

    private MapEditor() {}

    private record Splice(int at, int delLen, String insert) {}

    // ── set-attr ─────────────────────────────────────────────────────────────

    /** Set ({@code value} non-empty) or remove ({@code value} null/blank) an
     *  attribute on the topicref selected by {@code selector}. */
    public static MapEditPlan setAttr(MapModel model, String selector, String name,
            String value) {
        MapRef ref = require(model, selector);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute name is required");
        }
        String text = model.text();
        int[] tag = startTagRange(text, ref.startOffset());
        String newTag = setAttrInTag(text.substring(tag[0], tag[1]), name, value);
        String newText = text.substring(0, tag[0]) + newTag + text.substring(tag[1]);
        return new MapEditPlan(
                List.of(new MapEditPlan.FileChange(model.file(), newText)), List.of());
    }

    // ── insert ───────────────────────────────────────────────────────────────

    /** Insert a new {@code type} element (with {@code attrs}) under {@code parentSelector}
     *  at {@code index} (>= child count ⇒ append; null/"/" parent ⇒ map root). */
    public static MapEditPlan insert(MapModel model, String parentSelector, int index,
            String type, Map<String, String> attrs) {
        MapRef parent = (parentSelector == null || parentSelector.isBlank())
                ? model.root() : requireIn(model, parentSelector);
        if (!MapContentModel.canNest(parent.type(), type)) {
            throw new IllegalArgumentException(
                    type + " cannot be nested under " + parent.type());
        }
        String text = model.text();
        String element = buildElement(type, attrs);
        Splice splice = insertionSplice(text, parent, index, element);
        return new MapEditPlan(
                List.of(new MapEditPlan.FileChange(model.file(), applySplices(text, splice))),
                List.of());
    }

    // ── remove ───────────────────────────────────────────────────────────────

    /** Remove the selected topicref and its subtree. When {@code projectRoot} is
     *  given, warn about inbound references to the removed topics. */
    public static MapEditPlan remove(MapModel model, String selector, File projectRoot) {
        MapRef ref = require(model, selector);
        String text = model.text();
        Splice splice = removalSplice(text, ref);
        List<String> warnings = inboundWarnings(model, ref, projectRoot);
        return new MapEditPlan(
                List.of(new MapEditPlan.FileChange(model.file(), applySplices(text, splice))),
                warnings);
    }

    // ── move ─────────────────────────────────────────────────────────────────

    /**
     * Move the selected topicref under a new parent at {@code index}. {@code destModel}
     * is the destination map (== {@code model} for an in-map reorder/reparent, or a
     * different map for a cross-map move, which rebases the subtree's hrefs).
     */
    public static MapEditPlan move(MapModel model, String selector, MapModel destModel,
            String parentSelector, int index) {
        MapRef ref = require(model, selector);
        MapModel dest = destModel != null ? destModel : model;
        MapRef parent = (parentSelector == null || parentSelector.isBlank())
                ? dest.root() : requireIn(dest, parentSelector);
        if (!MapContentModel.canNest(parent.type(), ref.type())) {
            throw new IllegalArgumentException(
                    ref.type() + " cannot be nested under " + parent.type());
        }
        boolean sameMap = sameFile(model.file(), dest.file());
        if (sameMap && ref.isAncestorOf(parent)) {
            throw new IllegalArgumentException("cannot move a node into its own subtree");
        }

        String srcText = model.text();
        String moved = srcText.substring(ref.startOffset(), ref.endOffset());
        // Rebase the moved subtree's hrefs/conrefs when the destination dir differs.
        File srcDir = model.file().getAbsoluteFile().getParentFile();
        File destDir = dest.file().getAbsoluteFile().getParentFile();
        if (!sameFile(srcDir, destDir)) {
            moved = rebaseSubtree(moved, ref, srcDir, destDir);
        }

        Splice removal = removalSplice(srcText, ref);
        if (sameMap) {
            // One file: apply the removal and the insertion of the moved text together.
            // `index` is the FINAL position in the parent. When moving within the same
            // parent to a later slot, removing the source first shifts the later
            // siblings down by one, so bump the insertion index to compensate.
            int effIndex = index;
            int srcIdx = indexAmongSiblings(parent, ref);
            if (srcIdx >= 0 && index > srcIdx && index < parent.children().size()) {
                effIndex = index + 1;
            }
            Splice insertion = insertionSplice(srcText, parent, effIndex, moved.strip());
            String newText = applySplices(srcText, removal, insertion);
            return new MapEditPlan(
                    List.of(new MapEditPlan.FileChange(model.file(), newText)), List.of());
        }
        // Cross-map: remove from source, insert into destination.
        String destText = dest.text();
        Splice insertion = insertionSplice(destText, parent, index, moved.strip());
        return new MapEditPlan(List.of(
                new MapEditPlan.FileChange(model.file(), applySplices(srcText, removal)),
                new MapEditPlan.FileChange(dest.file(), applySplices(destText, insertion))),
                List.of());
    }

    // ── helpers: locating ──────────────────────────────────────────────────────

    /** The index of {@code child} among {@code parent}'s children (by selector), or -1
     *  if it isn't a direct child of {@code parent}. */
    private static int indexAmongSiblings(MapRef parent, MapRef child) {
        MapRef p = child.parent();
        if (p == null || !p.selector().equals(parent.selector())) {
            return -1;
        }
        List<MapRef> kids = parent.children();
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i).selector().equals(child.selector())) {
                return i;
            }
        }
        return -1;
    }

    private static MapRef require(MapModel model, String selector) {
        MapRef ref = requireIn(model, selector);
        if (ref == model.root()) {
            throw new IllegalArgumentException("the map root is not a topicref");
        }
        return ref;
    }

    private static MapRef requireIn(MapModel model, String selector) {
        MapRef ref = model.find(selector);
        if (ref == null) {
            throw new IllegalArgumentException("no topicref for selector: " + selector);
        }
        return ref;
    }

    // ── helpers: splicing ────────────────────────────────────────────────────

    private static String applySplices(String text, Splice... splices) {
        List<Splice> list = new ArrayList<>(List.of(splices));
        list.sort((a, b) -> Integer.compare(b.at, a.at)); // apply right-to-left
        StringBuilder sb = new StringBuilder(text);
        for (Splice s : list) {
            sb.replace(s.at, s.at + s.delLen, s.insert);
        }
        return sb.toString();
    }

    /** Remove the element range plus its leading indentation + newline (so no blank
     *  line is left behind), mirroring the editor's whitespace-aware element delete. */
    private static Splice removalSplice(String text, MapRef ref) {
        int start = ref.startOffset();
        int end = ref.endOffset();
        int ls = lineStart(text, start);
        if (text.substring(ls, start).isBlank()) {
            int from = ls > 0 ? ls - 1 : ls; // also drop the preceding newline
            return new Splice(from, end - from, "");
        }
        return new Splice(start, end - start, "");
    }

    /** Compute where (and with what surrounding whitespace) to splice {@code element}
     *  as a child of {@code parent} at {@code index}. */
    private static Splice insertionSplice(String text, MapRef parent, int index,
            String element) {
        List<MapRef> kids = parent.children();
        if (!kids.isEmpty()) {
            if (index < 0) {
                index = 0;
            }
            if (index >= kids.size()) {
                MapRef last = kids.get(kids.size() - 1);
                String indent = lineIndent(text, last.startOffset());
                return new Splice(last.endOffset(), 0, "\n" + indent + element);
            }
            MapRef before = kids.get(index);
            String indent = lineIndent(text, before.startOffset());
            int at = lineStart(text, before.startOffset());
            return new Splice(at, 0, indent + element + "\n");
        }
        // Empty container: insert inside it (expanding a self-closing parent).
        String parentIndent = lineIndent(text, parent.startOffset());
        String childIndent = parentIndent + "  ";
        int[] tag = startTagRange(text, parent.startOffset());
        String tagStr = text.substring(tag[0], tag[1]);
        if (tagStr.endsWith("/>")) {
            String open = tagStr.substring(0, tagStr.length() - 2).stripTrailing() + ">";
            String replacement = open + "\n" + childIndent + element + "\n"
                    + parentIndent + "</" + parent.type() + ">";
            return new Splice(tag[0], tag[1] - tag[0], replacement);
        }
        // Open/close form: insert just before the closing tag (at the element end).
        int closeTagStart = text.lastIndexOf("</", parent.endOffset());
        int at = lineStart(text, closeTagStart);
        return new Splice(at, 0, childIndent + element + "\n");
    }

    // ── helpers: tags / attributes ─────────────────────────────────────────────

    /** [start, endExclusive) of the start tag beginning at {@code start} ('<' … '>'). */
    private static int[] startTagRange(String text, int start) {
        boolean sq = false;
        boolean dq = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && !sq) {
                dq = !dq;
            } else if (c == '\'' && !dq) {
                sq = !sq;
            } else if (c == '>' && !sq && !dq) {
                return new int[] {start, i + 1};
            }
        }
        throw new IllegalStateException("unterminated start tag at " + start);
    }

    private static String setAttrInTag(String tag, String name, String value) {
        Pattern p = Pattern.compile(
                "\\s+" + Pattern.quote(name) + "\\s*=\\s*(\"[^\"]*\"|'[^']*')");
        Matcher m = p.matcher(tag);
        boolean present = m.find();
        if (value == null || value.isBlank()) {
            return present ? m.replaceFirst("") : tag; // remove (or no-op)
        }
        String attr = name + "=\"" + escapeAttr(value.trim()) + "\"";
        if (present) {
            return tag.substring(0, m.start()) + " " + attr + tag.substring(m.end());
        }
        boolean selfClose = tag.endsWith("/>");
        int close = selfClose ? tag.length() - 2 : tag.length() - 1;
        return tag.substring(0, close).stripTrailing() + " " + attr
                + (selfClose ? "/>" : ">");
    }

    private static String buildElement(String type, Map<String, String> attrs) {
        StringBuilder sb = new StringBuilder("<").append(type);
        if (attrs != null) {
            for (Map.Entry<String, String> e : attrs.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    sb.append(' ').append(e.getKey()).append("=\"")
                      .append(escapeAttr(e.getValue().trim())).append('"');
                }
            }
        }
        return sb.append("/>").toString();
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ── helpers: cross-map href rebase ─────────────────────────────────────────

    private record Rebase(int localStart, String name, String value) {}

    /** Rewrite each {@code @href}/{@code @conref} in the moved subtree text so it
     *  resolves the same target relative to the destination directory. */
    private static String rebaseSubtree(String moved, MapRef root, File srcDir, File destDir) {
        int base = root.startOffset();
        List<Rebase> rebases = new ArrayList<>();
        collectRebases(root, base, srcDir, destDir, rebases);
        // Apply right-to-left so earlier elements' offsets stay valid; same-element
        // edits recompute the tag each time, so href+conref both land.
        rebases.sort((a, b) -> Integer.compare(b.localStart, a.localStart));
        StringBuilder sb = new StringBuilder(moved);
        for (Rebase rb : rebases) {
            int[] tag = startTagRange(sb.toString(), rb.localStart);
            String newTag = setAttrInTag(sb.substring(tag[0], tag[1]), rb.name, rb.value);
            sb.replace(tag[0], tag[1], newTag);
        }
        return sb.toString();
    }

    private static void collectRebases(MapRef ref, int base, File srcDir, File destDir,
            List<Rebase> out) {
        for (String name : new String[] {"href", "conref"}) {
            String raw = ref.attr(name);
            if (raw == null || raw.isBlank() || raw.startsWith("#") || raw.contains("://")) {
                continue;
            }
            String path = raw;
            String frag = "";
            int hash = raw.indexOf('#');
            if (hash >= 0) {
                path = raw.substring(0, hash);
                frag = raw.substring(hash);
            }
            if (path.isBlank()) {
                continue;
            }
            File target = new File(srcDir, path).getAbsoluteFile().toPath()
                    .normalize().toFile();
            out.add(new Rebase(ref.startOffset() - base, name,
                    relativize(destDir, target) + frag));
        }
        for (MapRef child : ref.children()) {
            collectRebases(child, base, srcDir, destDir, out);
        }
    }

    // ── helpers: lines / paths ─────────────────────────────────────────────────

    private static int lineStart(String text, int offset) {
        int nl = text.lastIndexOf('\n', offset - 1);
        return nl < 0 ? 0 : nl + 1;
    }

    private static String lineIndent(String text, int offset) {
        int ls = lineStart(text, offset);
        StringBuilder sb = new StringBuilder();
        for (int i = ls; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static String relativize(File fromDir, File target) {
        String rel = fromDir.toPath().toAbsolutePath().normalize()
                .relativize(target.toPath().toAbsolutePath().normalize()).toString();
        return rel.replace(File.separatorChar, '/');
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getAbsoluteFile().toPath().normalize()
                .equals(b.getAbsoluteFile().toPath().normalize());
    }

    private static List<String> inboundWarnings(MapModel model, MapRef removed,
            File projectRoot) {
        if (projectRoot == null) {
            return List.of();
        }
        ReverseLinkIndex index;
        try {
            index = ReverseLinkIndex.build(projectRoot, null);
        } catch (RuntimeException e) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        List<MapRef> subtree = new ArrayList<>();
        flatten(removed, subtree);
        for (MapRef r : subtree) {
            File f = r.resolvedFile();
            if (f == null || !f.isFile()) {
                continue;
            }
            long external = index.usagesOf(f).stream()
                    .filter(u -> !sameFile(u.source(), model.file()))
                    .map(Reference::source)
                    .distinct().count();
            if (external > 0) {
                warnings.add(f.getName() + " is referenced by " + external
                        + " other file(s); removing it from the map may orphan them");
            }
        }
        return warnings;
    }

    private static void flatten(MapRef ref, List<MapRef> out) {
        out.add(ref);
        for (MapRef c : ref.children()) {
            flatten(c, out);
        }
    }
}
