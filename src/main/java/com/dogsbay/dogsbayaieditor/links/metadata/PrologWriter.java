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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * Field-preserving writer for a topic's {@code <prolog>} metadata. Uses the parsed DOM
 * only to <em>decide</em> the structural change (locate-or-create the prolog / wrapper /
 * field element in DITA content-model order), then <em>applies it as a surgical edit on the
 * original source text</em> via {@link SourcePositions} — splicing the new node(s) in,
 * replacing just a changed value, or deleting a removed element. Everything else is copied
 * byte-for-byte: the XML declaration, DOCTYPE, the DOCTYPE↔root whitespace, unrelated
 * elements, and the trailing newline. Inserted nodes are indented to match their siblings;
 * the file's newline (CRLF-safe) and encoding are preserved.
 *
 * <p>v1 targets topic prologs (maps' {@code <topicmeta>} is a follow-up); the default
 * {@code metadata_set} only feeds it {@code .dita} files.
 */
public final class PrologWriter {

    /** How a change is applied. */
    public enum Mode { SET, FILL, APPEND, REMOVE }

    /** One requested change to a field. */
    public record Change(MetadataField field, String value, Mode mode) {}

    /** DITA topic child order — a new {@code <prolog>} is inserted to precede the body. */
    private static final List<String> TOPIC_ORDER = List.of(
            "title", "titlealts", "shortdesc", "abstract", "prolog",
            "conbody", "taskbody", "refbody", "body", "glossBody", "glossdef");

    /**
     * glossentry / glossgroup child order. Their content model is
     * {@code (glossterm, glossdef?, prolog?, glossBody?, …)}, so {@code <prolog>}
     * must follow {@code glossterm}/{@code glossdef} — unlike a generic topic
     * where it precedes the body. Using {@link #TOPIC_ORDER} here would insert
     * the prolog before {@code glossdef} and produce an invalid glossentry.
     */
    private static final List<String> GLOSSENTRY_ORDER = List.of(
            "glossterm", "glossdef", "prolog", "glossBody", "related-links");

    /** DITA {@code <prolog>} child order (a new child is inserted to keep this). */
    private static final List<String> PROLOG_ORDER = List.of(
            "author", "source", "publisher", "copyright", "critdates",
            "permissions", "metadata", "resourceid", "data");

    /** Map child order — a new {@code <topicmeta>}/{@code <bookmeta>} follows the title. */
    private static final List<String> MAP_ORDER = List.of(
            "title", "topicmeta", "bookmeta", "anchor", "data", "keydef", "mapref",
            "navref", "reltable", "topicgroup", "topichead", "topicref",
            "frontmatter", "chapter", "part", "appendix", "backmatter");

    /** {@code <topicmeta>}/{@code <bookmeta>} child order (flattened — no {@code <metadata>}). */
    private static final List<String> TOPICMETA_ORDER = List.of(
            "navtitle", "linktext", "searchtitle", "shortdesc", "author", "source",
            "publisher", "copyright", "critdates", "permissions", "audience",
            "category", "keywords", "prodinfo", "othermeta", "resourceid", "data");

    private PrologWriter() {}

    /**
     * Apply the changes to {@code file}. Returns the changes that actually took
     * effect (empty if none); the file is rewritten only when non-empty and
     * {@code dryRun} is false. Throws on parse/IO failure or if the result would not
     * be well-formed (the file is left untouched in that case).
     */
    public static List<Change> apply(File file, List<Change> changes, boolean dryRun)
            throws Exception {
        // Newline sniffed from bytes so a non-UTF-8 file isn't rejected just to detect it.
        // The XML declaration, DOCTYPE, and all unedited content are preserved verbatim (we
        // splice into the original text rather than re-serializing), so nothing else to detect.
        byte[] sourceBytes = Files.readAllBytes(file.toPath());
        String nl = hasCRLF(sourceBytes) ? "\r\n" : "\n";

        // Parse the DOM from the same bytes we'll splice into (one read; the DOM and the source
        // text then agree). Xerces strips a leading UTF-8 BOM, so strip it from our decoded
        // String too — and re-prepend it on write — so element offsets line up with the tree.
        Document doc = hardenedReader().read(new java.io.ByteArrayInputStream(sourceBytes));
        Element root = doc.getRootElement();
        String rootName = root.getName();
        // Topics get a <prolog>; a plain map gets a <topicmeta>. A <dita> ditabase has no single
        // metadata home, and a <bookmap>'s <bookmeta> has a materially different content model —
        // leave both alone. (The metadata_set contract is enforced at the .dita filename filter in
        // HeadlessExecutor; PrologWriter itself stays map-capable for direct callers.)
        if ("dita".equals(rootName) || "bookmap".equals(rootName)) {
            return List.of();
        }

        String encoding = doc.getXMLEncoding() != null ? doc.getXMLEncoding() : "UTF-8";
        String decoded = new String(sourceBytes, java.nio.charset.Charset.forName(encoding));
        String bom = decoded.startsWith("\uFEFF") ? "\uFEFF" : "";
        String source = decoded.substring(bom.length());

        // Correlate each source element offset to the (pre-mutation) DOM by pre-order, so a
        // metadata edit can splice into the original text instead of re-serializing the whole
        // DOM (which would drop the DOCTYPE↔root whitespace and the trailing newline, and emit
        // inserted nodes inline). The N-th SAX element == the N-th DOM pre-order element.
        SourcePositions positions = SourcePositions.of(source);
        java.util.Map<Element, Integer> preIndex = new java.util.IdentityHashMap<>();
        indexPreorder(root, new int[] {0}, preIndex);
        java.util.Set<Element> existing =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        existing.addAll(preIndex.keySet());
        String unit = detectIndentUnit(positions, preIndex, root);

        List<Change> applied = new ArrayList<>();
        List<Edit> edits = new ArrayList<>();
        // Several changes can target the SAME freshly-created wrapper (e.g. two keywords, or
        // copyrholder+copyryear); it's serialized once with all its values, so insert it once.
        java.util.Set<Element> insertedSubtrees =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Change ch : changes) {
            List<Edit> chEdits = applyOne(root, ch, existing);
            boolean took = false;
            for (Edit ed : chEdits) {
                took = true;
                if (ed instanceof InsertEdit ie && !insertedSubtrees.add(ie.subtree())) {
                    continue;   // value already folded into a shared subtree's single insert
                }
                edits.add(ed);
            }
            if (took) {
                applied.add(ch);
            }
        }
        if (applied.isEmpty()) {
            return applied;
        }

        // Translate DOM edits → text edits, then apply high-offset-first so earlier offsets
        // stay valid. Everything outside the touched spans is copied verbatim.
        List<TextEdit> textEdits = new ArrayList<>();
        for (Edit e : edits) {
            textEdits.add(toTextEdit(e, source, positions, preIndex, existing, unit, nl));
        }
        textEdits.sort((a, b) -> Integer.compare(b.start(), a.start()));
        StringBuilder sb = new StringBuilder(source);
        for (TextEdit te : textEdits) {
            sb.replace(te.start(), te.end(), te.replacement());
        }
        byte[] out = (bom + sb.toString()).getBytes(java.nio.charset.Charset.forName(encoding));

        // Safety: the result must be well-formed before we overwrite.
        hardenedReader().read(new java.io.ByteArrayInputStream(out));

        if (!dryRun) {
            Files.write(file.toPath(), out);
        }
        return applied;
    }

    // ── source-text edit model ──────────────────────────────────────────────────────────

    /** A DOM-level change to translate into a source-text edit. */
    private sealed interface Edit {}
    /** A new subtree whose parent already existed in the source — splice it in. */
    private record InsertEdit(Element subtree) implements Edit {}
    /** A value set on an element that already existed — re-serialize just that element. */
    private record ReplaceEdit(Element element) implements Edit {}
    /** Elements removed from the DOM — delete their source spans. */
    private record RemoveEdit(List<Element> elements) implements Edit {}

    /** A raw text replacement: {@code source[start,end)} → {@code replacement}. */
    private record TextEdit(int start, int end, String replacement) {}

    /** Assign each element a pre-order index matching {@link SourcePositions} order. */
    private static void indexPreorder(Element el, int[] counter, java.util.Map<Element, Integer> out) {
        out.put(el, counter[0]++);
        for (Object o : el.elements()) {
            indexPreorder((Element) o, counter, out);
        }
    }

    /** The source span of an existing element (via its pre-order index). */
    private static SourcePositions.Span spanOf(Element el, SourcePositions pos,
                                               java.util.Map<Element, Integer> preIndex) {
        Integer i = preIndex.get(el);
        return i == null ? null : pos.get(i);
    }

    /** The file's indent unit, inferred from any existing parent→child indent step (default 2 spaces). */
    private static String detectIndentUnit(SourcePositions pos, java.util.Map<Element, Integer> preIndex,
                                           Element root) {
        for (Object o : root.elements()) {
            Element child = (Element) o;
            SourcePositions.Span ps = spanOf(root, pos, preIndex);
            SourcePositions.Span cs = spanOf(child, pos, preIndex);
            if (ps != null && cs != null && cs.indent().startsWith(ps.indent())
                    && cs.indent().length() > ps.indent().length()) {
                return cs.indent().substring(ps.indent().length());
            }
        }
        return "  ";
    }

    /** Build the source-text edit for one DOM edit. */
    private static TextEdit toTextEdit(Edit e, String source, SourcePositions pos,
                                       java.util.Map<Element, Integer> preIndex,
                                       java.util.Set<Element> existing, String unit, String nl) {
        if (e instanceof ReplaceEdit re) {
            // Re-serialize just the changed element at full fidelity (no pretty-print reflow),
            // so a leaf's value changes byte-for-byte and any children/text are preserved.
            SourcePositions.Span s = spanOf(re.element(), pos, preIndex);
            int end = s.selfClosing() ? s.openTagEnd() : s.closeTagEnd();
            String text = reindent(serializeRaw(re.element()), s.indent(), nl, false);
            return new TextEdit(s.openTagStart(), end, text);
        }
        if (e instanceof RemoveEdit rm) {
            // Single removal expected per change; delete the element's line(s) incl. indent + EOL.
            Element el = rm.elements().get(0);
            SourcePositions.Span s = spanOf(el, pos, preIndex);
            int end = s.selfClosing() ? s.openTagEnd() : s.closeTagEnd();
            int start = s.openTagStart() - s.indent().length();        // line start
            while (end < source.length() && (source.charAt(end) == '\r' || source.charAt(end) == '\n')) {
                end++;                                                  // swallow the trailing EOL
            }
            return new TextEdit(start, end, "");
        }
        InsertEdit ins = (InsertEdit) e;
        Element subtree = ins.subtree();
        Element parent = subtree.getParent();
        SourcePositions.Span parentSpan = spanOf(parent, pos, preIndex);
        // A self-closing parent (<prolog/>) has no body to splice into — re-serialize the
        // whole parent (now containing the new subtree) in place of its `<parent/>` tag.
        if (parentSpan.selfClosing()) {
            String text = reindent(serializePretty(parent, unit), parentSpan.indent(), nl, false);
            return new TextEdit(parentSpan.openTagStart(), parentSpan.openTagEnd(), text);
        }
        // Indent the new node to match its siblings: the next existing sibling's indent, else
        // the parent's indent + one unit.
        Element anchor = nextExistingSibling(parent, subtree, existing);
        String childIndent = anchor != null
                ? spanOf(anchor, pos, preIndex).indent()
                : parentSpan.indent() + unit;
        String block = reindent(serializePretty(subtree, unit), childIndent, nl, true) + nl;
        int at;
        if (anchor != null) {
            SourcePositions.Span as = spanOf(anchor, pos, preIndex);
            at = as.openTagStart() - as.indent().length();             // start of the anchor's line
        } else {
            // append as last child: before the parent's close tag line
            at = parentSpan.closeTagStart()
                    - lineIndentBefore(source, parentSpan.closeTagStart()).length();
        }
        return new TextEdit(at, at, block);
    }

    /** The first element sibling after {@code child} (in the mutated DOM) that already existed. */
    private static Element nextExistingSibling(Element parent, Element child,
                                               java.util.Set<Element> existing) {
        List<?> content = parent.content();
        int idx = content.indexOf(child);
        for (int i = idx + 1; i < content.size(); i++) {
            if (content.get(i) instanceof Element sib && existing.contains(sib)) {
                return sib;
            }
        }
        return null;
    }

    /** Pretty-print one new element subtree (no declaration), with {@code unit} indentation. */
    private static String serializePretty(Element el, String unit) {
        OutputFormat fmt = OutputFormat.createPrettyPrint();
        fmt.setIndent(unit);
        return serialize(el, fmt);
    }

    /** Serialize one element verbatim (no pretty-print reflow), so a leaf is byte-exact and an
     *  element's existing children/text are preserved. */
    private static String serializeRaw(Element el) {
        OutputFormat fmt = new OutputFormat();
        fmt.setNewlines(false);
        fmt.setTrimText(false);
        return serialize(el, fmt);
    }

    private static String serialize(Element el, OutputFormat fmt) {
        try {
            fmt.setSuppressDeclaration(true);
            fmt.setExpandEmptyElements(false);
            fmt.setLineSeparator("\n");
            java.io.StringWriter sw = new java.io.StringWriter();
            XMLWriter w = new XMLWriter(sw, fmt);
            w.write(el.createCopy());   // copy so writing doesn't disturb the live tree
            w.close();
            return sw.toString().strip();
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);   // StringWriter never throws
        }
    }

    /** Prefix each line with {@code indent} (continuation lines only when {@code firstLineToo} is false). */
    private static String reindent(String fragment, String indent, String nl, boolean firstLineToo) {
        String[] lines = fragment.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append(nl);
            }
            if (i > 0 || firstLineToo) {
                sb.append(indent);
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** The whitespace run immediately before {@code pos} on its line. */
    private static String lineIndentBefore(String s, int pos) {
        int i = pos;
        while (i > 0 && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
            i--;
        }
        return s.substring(i, pos);
    }

    private static SAXReader hardenedReader() throws org.xml.sax.SAXException {
        SAXReader reader = new SAXReader();
        reader.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setStripWhitespaceText(false);
        return reader;
    }

    private static boolean hasCRLF(byte[] b) {
        for (int i = 0; i + 1 < b.length; i++) {
            if (b[i] == '\r' && b[i + 1] == '\n') {
                return true;
            }
        }
        return false;
    }


    private static List<Edit> applyOne(Element root, Change ch, java.util.Set<Element> existing) {
        MetadataField field = ch.field();
        if (ch.mode() == Mode.REMOVE) {
            return removeEdits(root, field, ch.value());
        }
        // Elements of the field's name, and those that actually carry its value.
        // (A field's "value" may be an attribute, so the element existing isn't
        // the same as the value being present — e.g. <audience type=…> has no @job.)
        List<Element> named = findFields(root, field);
        boolean valuePresent = named.stream().anyMatch(e -> hasValue(field, e));

        if (ch.mode() == Mode.FILL && valuePresent) {
            return List.of(); // already has a value
        }
        // APPEND always creates a fresh element (list fields); SET/FILL create only when
        // the element is absent. A created element (and any wrappers) is an INSERT of the
        // top-most new node; setting a value on an existing element is a REPLACE of just it.
        if (ch.mode() == Mode.APPEND || named.isEmpty()) {
            Element created = newFieldElement(root, containerFor(root, field), field.element());
            setValue(created, field, ch.value());
            return List.of(new InsertEdit(topMostNew(created, existing)));
        }
        Element target = named.get(0);
        setValue(target, field, ch.value());
        return List.of(new ReplaceEdit(target));
    }

    /** The highest ancestor of {@code created} whose parent already existed in the source. */
    private static Element topMostNew(Element created, java.util.Set<Element> existing) {
        Element top = created;
        while (top.getParent() != null && !existing.contains(top.getParent())) {
            top = top.getParent();
        }
        return top;
    }

    /**
     * Create a new field element in {@code container}. For a map the metadata lands
     * directly in {@code <topicmeta>}, whose child order is strict, so keep that
     * order; a topic's container ({@code <metadata>}/{@code <prolog>}) is order-
     * permissive, so a plain append is fine there.
     */
    private static Element newFieldElement(Element root, Element container, String name) {
        if ("map".equals(root.getName())) {
            Element el = org.dom4j.DocumentHelper.createElement(name);
            insertOrdered(container, el, TOPICMETA_ORDER);
            return el;
        }
        return container.addElement(name);
    }

    private static List<Edit> removeEdits(Element root, MetadataField field, String value) {
        List<Edit> out = new ArrayList<>();
        for (Element e : findFields(root, field)) {
            if (value != null && !value.isBlank() && !value.equals(currentValue(field, e))) {
                continue;
            }
            if (field.kind() == MetadataField.ValueKind.ATTR) {
                org.dom4j.Attribute a = e.attribute(field.attr());
                if (a != null) {           // drop just the attribute; re-serialize the element
                    e.remove(a);
                    out.add(new ReplaceEdit(e));
                }
            } else {
                e.getParent().remove(e);   // text / name-pair field → delete the element's span
                out.add(new RemoveEdit(List.of(e)));
            }
        }
        return out;
    }

    /** True when {@code field}'s value is actually present on the element. */
    private static boolean hasValue(MetadataField field, Element e) {
        return switch (field.kind()) {
            case ATTR -> notBlank(e.attributeValue(field.attr()));
            case TEXT -> !e.getTextTrim().isEmpty();
            case NAME_CONTENT, NAME_VALUE -> notBlank(e.attributeValue("name"));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** All elements for {@code field} within the prolog (empty if no prolog). */
    private static List<Element> findFields(Element root, MetadataField field) {
        List<Element> out = new ArrayList<>();
        String containerName = "map".equals(root.getName()) ? "topicmeta" : "prolog";
        Element container = root.element(containerName);
        if (container != null) {
            collect(container, field.element(), out);
        }
        return out;
    }

    private static void collect(Element el, String name, List<Element> out) {
        for (Object o : el.elements()) {
            Element c = (Element) o;
            if (c.getName().equals(name)) {
                out.add(c);
            }
            collect(c, name, out);
        }
    }

    /** The container a new field element should be created in, creating wrappers.
     *  Keyed by the DITA element (so fields sharing an element share a container). */
    private static Element containerFor(Element root, MetadataField field) {
        // A map stores metadata in <topicmeta>, not a <prolog>.
        if ("map".equals(root.getName())) {
            return mapContainerFor(root, field);
        }
        // glossentry/glossgroup place <prolog> after glossterm/glossdef, not before
        // the body as a generic topic does.
        List<String> topicOrder =
                ("glossentry".equals(root.getName()) || "glossgroup".equals(root.getName()))
                        ? GLOSSENTRY_ORDER : TOPIC_ORDER;
        Element prolog = ensureChild(root, "prolog", topicOrder);
        return switch (field.element()) {
            case "copyrholder", "copyryear" -> ensureChild(prolog, "copyright", PROLOG_ORDER);
            case "created", "revised" -> ensureChild(prolog, "critdates", PROLOG_ORDER);
            // these sit directly in prolog
            case "author", "source", "publisher", "permissions", "data", "resourceid" -> prolog;
            // keyword/indexterm live under <metadata><keywords>
            case "keyword", "indexterm" ->
                    ensureChild(metadata(prolog), "keywords", null);
            // product-info leaves under <metadata><prodinfo>
            case "prodname", "brand", "component", "featnum", "platform", "prognum", "series" ->
                    ensureChild(metadata(prolog), "prodinfo", null);
            case "vrm" -> ensureChild(
                    ensureChild(metadata(prolog), "prodinfo", null), "vrmlist", null);
            // audience, category, othermeta, and any other metadata child
            default -> metadata(prolog);
        };
    }

    private static Element metadata(Element prolog) {
        return ensureChild(prolog, "metadata", PROLOG_ORDER);
    }

    /** The container for a field in a map: under {@code <topicmeta>}, whose
     *  children are flat (no {@code <metadata>} wrapper as in a topic prolog). */
    private static Element mapContainerFor(Element root, MetadataField field) {
        Element meta = ensureChild(root, "topicmeta", MAP_ORDER);
        return switch (field.element()) {
            case "copyrholder", "copyryear" -> ensureChild(meta, "copyright", TOPICMETA_ORDER);
            case "created", "revised" -> ensureChild(meta, "critdates", TOPICMETA_ORDER);
            case "keyword", "indexterm" -> ensureChild(meta, "keywords", TOPICMETA_ORDER);
            case "prodname", "brand", "component", "featnum", "platform", "prognum", "series" ->
                    ensureChild(meta, "prodinfo", TOPICMETA_ORDER);
            case "vrm" -> ensureChild(ensureChild(meta, "prodinfo", TOPICMETA_ORDER),
                    "vrmlist", null);
            // author, source, publisher, audience, category, othermeta, data,
            // resourceid sit directly in topicmeta/bookmeta.
            default -> meta;
        };
    }

    /**
     * Return the first child named {@code name}, creating it if absent. When
     * {@code order} is given, a new child is inserted to keep that element order;
     * otherwise it's appended (for order-permissive containers like {@code metadata}).
     */
    private static Element ensureChild(Element parent, String name, List<String> order) {
        Element existing = parent.element(name);
        if (existing != null) {
            return existing;
        }
        Element created = org.dom4j.DocumentHelper.createElement(name);
        if (order != null) {
            insertOrdered(parent, created, order);
        } else {
            parent.add(created);
        }
        return created;
    }

    /** Insert {@code child} into {@code parent} keeping {@code order} by element name. */
    private static void insertOrdered(Element parent, Element child, List<String> order) {
        int rank = order.indexOf(child.getName());
        List<?> content = parent.content();
        for (int i = 0; i < content.size(); i++) {
            Object node = content.get(i);
            if (node instanceof Element e) {
                int r = order.indexOf(e.getName());
                if (r >= 0 && r > rank) {
                    parent.content().add(i, child);
                    return;
                }
            }
        }
        parent.add(child);
    }

    private static void setValue(Element el, MetadataField field, String value) {
        switch (field.kind()) {
            case TEXT -> el.setText(value == null ? "" : value);
            case ATTR -> el.addAttribute(field.attr(), value == null ? "" : value);
            case NAME_CONTENT -> setPair(el, "content", value);
            case NAME_VALUE -> setPair(el, "value", value);
        }
    }

    /** Set a name/content (or name/value) pair from a {@code "name=value"} string. */
    private static void setPair(Element el, String valueAttr, String pair) {
        String name = pair;
        String val = "";
        int eq = pair == null ? -1 : pair.indexOf('=');
        if (eq >= 0) {
            name = pair.substring(0, eq);
            val = pair.substring(eq + 1);
        }
        el.addAttribute("name", name == null ? "" : name);
        el.addAttribute(valueAttr, val);
    }

    private static String currentValue(MetadataField field, Element el) {
        return switch (field.kind()) {
            case TEXT -> el.getTextTrim();
            case ATTR -> el.attributeValue(field.attr());
            case NAME_CONTENT -> el.attributeValue("name") + "=" + el.attributeValue("content");
            case NAME_VALUE -> el.attributeValue("name") + "=" + el.attributeValue("value");
        };
    }
}
