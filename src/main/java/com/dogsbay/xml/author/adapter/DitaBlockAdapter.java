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

package com.dogsbay.xml.author.adapter;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Comment;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.ProcessingInstruction;
import org.dom4j.Text;
import org.dom4j.io.SAXReader;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.BlockTypeRegistry;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;
import com.dogsbay.xml.author.model.XmlAuxNode;

/**
 * Lossless DITA topic <-> block tree conversion.
 *
 * <p>Import rules, in order of preference:
 * <ul>
 * <li>Known element in a valid position becomes a structured block. Text-bearing
 *     blocks accept content matching {@code [inline*, block*]}: an inline prefix
 *     (text, b/i/u, semantic inlines, xref) followed by block children.</li>
 * <li>Anything else — unknown elements, interleaved mixed content, inline markup
 *     the run model cannot hold — becomes a raw-XML block, re-emitted verbatim.</li>
 * <li>Comments and processing instructions between blocks ride along as leading
 *     ("attach to the following block") or trailing aux nodes.</li>
 * </ul>
 *
 * <p>Whitespace in non-{@code preserveSpace} blocks is collapsed to single
 * spaces and trimmed at the block edges; {@code codeblock} text is verbatim.
 */
public final class DitaBlockAdapter implements BlockAdapter {

    /** Reserved attribute carrying the {@code <alt>} text of an image block. */
    public static final String IMAGE_ALT_ATTR = "#alt";

    /** Reserved attribute carrying the {@code <linktext>} of a link block. */
    public static final String LINK_TEXT_ATTR = "#linktext";

    /** Prefix for unmapped attributes preserved from an {@code <xref>}. */
    private static final String XREF_ATTR_PREFIX = "xref:";

    /**
     * Reserved attribute prefix for namespace declarations on a block:
     * {@code #xmlns:ditaarch} (or {@code #xmlns} for the default namespace)
     * holds the URI, and export re-declares it on the element. Nearly every
     * topic written by Oxygen or DITA-OT carries {@code xmlns:ditaarch}.
     */
    static final String XMLNS_ATTR_PREFIX = "#xmlns";

    /** Numbers plain {@code <ph>} elements within the block being imported. */
    private int phSequence;

    private int instanceSequence;

    private final BlockTypeRegistry registry;

    public DitaBlockAdapter() {
        this(BlockTypeRegistry.ditaProfile());
    }

    public DitaBlockAdapter(BlockTypeRegistry registry) {
        this.registry = registry;
    }

    public BlockTypeRegistry getRegistry() {
        return registry;
    }

    /** Whether a root element name is a DITA topic type this adapter handles. */
    public static boolean isDitaTopic(String rootName) {
        return "concept".equals(rootName) || "task".equals(rootName)
                || "reference".equals(rootName) || "topic".equals(rootName)
                || "glossentry".equals(rootName) || "glossgroup".equals(rootName)
                || "troubleshooting".equals(rootName);
    }

    // ==================================================================
    // Import
    // ==================================================================

    @Override
    public AuthorDocument importDocument(Element root) {
        AuthorDocument doc = new AuthorDocument(registry);
        BlockType rootType = registry.get(root.getName());
        AuthorBlock rootBlock;
        if (rootType != null && rootType.getCategory() == BlockType.Category.CONTAINER) {
            rootBlock = importElement(doc, root, rootType);
        } else {
            rootBlock = null;
        }
        if (rootBlock == null) {
            rootBlock = rawBlock(doc, root);   // not representable as a whole: verbatim
        }
        doc.setRoot(rootBlock);
        return doc;
    }

    @Override
    public AuthorBlock importFragment(AuthorDocument doc, Element element, AuthorBlock parent) {
        boolean saved = inheritedPreserve;
        inheritedPreserve = parent.preservesSpace();   // whitespace handling follows the future ancestors
        try {
            // by element name alone: whether it may go under this parent is the
            // caller's decision, so markup that simply does not belong here can
            // be refused rather than pasted as an opaque chip
            BlockType type = registry.get(element.getName());
            AuthorBlock block = type == null ? null : importElement(doc, element, type);
            return block != null ? block : rawBlock(doc, element);
        } finally {
            inheritedPreserve = saved;
        }
    }

    @Override
    public Element exportFragment(AuthorBlock block) {
        return exportBlock(block);
    }

    /** Imports one element as a structured block, falling back to raw if needed. */
    private AuthorBlock importChild(AuthorDocument doc, Element element, BlockType parentType) {
        BlockType type = registry.get(element.getName());
        if (type == null || !registry.isValidChild(parentType, type)) {
            return rawBlock(doc, element);
        }
        AuthorBlock block = importElement(doc, element, type);
        return block != null ? block : rawBlock(doc, element);
    }

    /**
     * Attempts a structured import of {@code element} as {@code type}.
     * Returns null when the content does not fit the block model.
     */
    private AuthorBlock importElement(AuthorDocument doc, Element element, BlockType type) {
        return importElement(doc, element, type, inheritedPreserve);
    }

    /** {@code xml:space="preserve"} in force from the ancestors of the element being imported. */
    private boolean inheritedPreserve;

    private AuthorBlock importElement(AuthorDocument doc, Element element, BlockType type, boolean outerPreserve) {
        AuthorBlock block = doc.createBlock(type.getName());

        for (Object nsObj : element.declaredNamespaces()) {
            Namespace ns = (Namespace) nsObj;
            if (ns.getPrefix() == null || ns.getPrefix().isEmpty()) {
                return null;   // a default namespace would move the children out of theirs on export
            }
            block.setAttribute(xmlnsKey(ns.getPrefix()), ns.getURI());
        }
        for (Object attrObj : element.attributes()) {
            org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
            block.setAttribute(attr.getQualifiedName(), attr.getValue());
        }

        if (type.getCategory() == BlockType.Category.VOID) {
            return importVoidContent(element, block) ? block : null;
        }

        List<Node> content = contentOf(element);
        phSequence = 0;
        instanceSequence = 0;
        int blockStart = type.hasText() ? inlinePrefixEnd(content) : 0;
        if (blockStart < 0) {
            return null; // inline content not representable
        }

        String space = block.getAttribute(AuthorBlock.XML_SPACE);
        boolean preserve = type.isPreserveSpace() || (space == null ? outerPreserve : "preserve".equals(space));
        if (type.hasText()) {
            List<InlineRun> runs = collectRuns(content.subList(0, blockStart), preserve);
            if (runs == null) {
                return null;
            }
            block.setText(runs);
        }
        boolean savedPreserve = inheritedPreserve;
        inheritedPreserve = preserve;
        try {
            return importBlockChildren(doc, element, type, block, content, blockStart);
        } finally {
            inheritedPreserve = savedPreserve;
        }
    }

    private AuthorBlock importBlockChildren(AuthorDocument doc, Element element, BlockType type, AuthorBlock block,
            List<Node> content, int blockStart) {

        // remaining content must be block children with optional aux nodes between
        List<XmlAuxNode> pendingAux = new ArrayList<>();
        int childIndex = 0;
        for (int i = blockStart; i < content.size(); i++) {
            Node node = content.get(i);
            if (node instanceof Text) {
                if (!node.getText().isBlank()) {
                    return null; // significant text between block children
                }
            } else if (node instanceof Comment c) {
                pendingAux.add(XmlAuxNode.comment(c.getText()));
            } else if (node instanceof ProcessingInstruction pi) {
                pendingAux.add(XmlAuxNode.pi(pi.getTarget(), pi.getText()));
            } else if (node instanceof Element child) {
                AuthorBlock childBlock = importChild(doc, child, type);
                if (!pendingAux.isEmpty()) {
                    childBlock.setLeadingAux(pendingAux);
                    pendingAux = new ArrayList<>();
                }
                doc.attach(block, childIndex++, childBlock);
            } else {
                return null; // entity refs, CDATA outside text context, ...
            }
        }
        if (!pendingAux.isEmpty()) {
            block.setTrailingAux(pendingAux);
        }
        return block;
    }

    /**
     * Void blocks are attribute-only, except for one well-known text-bearing
     * child carried as a reserved attribute: {@code <alt>} on images,
     * {@code <linktext>} on links.
     */
    private boolean importVoidContent(Element element, AuthorBlock block) {
        String textChild = switch (block.getType().getName()) {
            case "image" -> "alt";
            case "link" -> "linktext";
            default -> null;
        };
        String reservedAttr = "image".equals(block.getType().getName())
                ? IMAGE_ALT_ATTR : LINK_TEXT_ATTR;
        for (Node node : contentOf(element)) {
            if (node instanceof Text) {
                if (!node.getText().isBlank()) {
                    return false;
                }
            } else if (node instanceof Element child
                    && child.getName().equals(textChild)
                    && contentOf(child).stream().allMatch(n -> n instanceof Text)
                    && child.attributes().isEmpty()) {
                block.setAttribute(reservedAttr, collapse(child.getText()).strip());
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Index where the inline prefix ends and block-level content begins.
     * The representable shape is {@code [inline*, (block|aux)*]}; returns -1
     * when content interleaves inline material after a block child, or mixes
     * comments/PIs into running text (caller falls back to raw).
     */
    private int inlinePrefixEnd(List<Node> content) {
        int prefixEnd = content.size();
        for (int i = 0; i < content.size(); i++) {
            Node node = content.get(i);
            boolean blockLevel = (node instanceof Element e && !isInlineElement(e))
                    || node instanceof Comment
                    || node instanceof ProcessingInstruction;
            if (blockLevel) {
                prefixEnd = i;
                break;
            }
        }
        if (prefixEnd < content.size() && content.get(prefixEnd) instanceof Comment
                || prefixEnd < content.size() && content.get(prefixEnd) instanceof ProcessingInstruction) {
            // aux node ends the prefix — only safe if no inline text precedes it
            // (a comment inside running text is not representable)
            for (int i = 0; i < prefixEnd; i++) {
                Node n = content.get(i);
                if (n instanceof Element || (n instanceof Text && !n.getText().isBlank())) {
                    return -1;
                }
            }
        }
        // nothing inline may follow the prefix; an element that is also a block type is a block here
        for (int i = prefixEnd; i < content.size(); i++) {
            Node node = content.get(i);
            if (node instanceof Text && !node.getText().isBlank()) {
                return -1;
            }
            if (node instanceof Element e && isInlineElement(e) && !isBlockToo(e)) {
                return -1;
            }
        }
        return prefixEnd;
    }

    private boolean isInlineElement(Element e) {
        String name = e.getName();
        return switch (name) {
            case "b", "i", "u", "xref", "ph" -> true;
            default -> InlineStyle.DITA_INLINE_NAMES.contains(name);
        };
    }

    /** Elements that are inline in running text and blocks among blocks (draft-comment, fn, indexterm). */
    private boolean isBlockToo(Element e) {
        BlockType t = registry.get(e.getName());
        return t != null && t.getCategory() != BlockType.Category.RAW;
    }

    /**
     * Flattens inline content into runs; returns null when something cannot be
     * represented (comments inside text, elements with unsupported shapes).
     */
    private List<InlineRun> collectRuns(List<Node> inlineContent, boolean preserveSpace) {
        List<InlineRun> runs = new ArrayList<>();
        if (!collectRuns(inlineContent, Map.of(), runs)) {
            return null;
        }
        return coalesce(preserveSpace ? normalizePreserved(runs) : normalizeCollapsed(runs));
    }

    /**
     * Joins neighbouring runs with the same attributes: a parser may hand one
     * text node over in pieces, and two runs would export as two elements.
     * Zero-length reference runs are content of their own and stay apart.
     */
    private static List<InlineRun> coalesce(List<InlineRun> runs) {
        List<InlineRun> out = new ArrayList<>(runs.size());
        for (InlineRun run : runs) {
            if (!out.isEmpty()) {
                InlineRun last = out.get(out.size() - 1);
                if (last.attrs().equals(run.attrs()) && !last.text().isEmpty() && !run.text().isEmpty()) {
                    out.set(out.size() - 1, new InlineRun(last.text() + run.text(), last.attrs()));
                    continue;
                }
            }
            out.add(run);
        }
        return out;
    }

    /** Inline elements whose content is elements only, so whitespace between them is layout, not text. */
    private static final java.util.Set<String> ELEMENT_ONLY_INLINES = java.util.Set.of("menucascade");

    private boolean collectRuns(List<Node> nodes, Map<String, String> attrs, List<InlineRun> out) {
        String semantic = attrs.get(InlineStyle.DITA_INLINE);
        boolean dropBlankText = semantic != null && ELEMENT_ONLY_INLINES.contains(semantic)
                && !attrs.containsKey(InlineStyle.DITA_INLINE_OUTER);
        for (Node node : nodes) {
            if (node instanceof Text) {
                if (dropBlankText && node.getText().isBlank()) {
                    continue;
                }
                out.add(new InlineRun(node.getText(), attrs));
            } else if (node instanceof Element e) {
                Map<String, String> merged = mergeInlineAttrs(attrs, e);
                if (merged == null) {
                    return false;
                }
                merged = new LinkedHashMap<>(merged);
                merged.put(InlineStyle.ELEMENT, Integer.toString(++instanceSequence));
                int before = out.size();
                if (!collectRuns(contentOf(e), merged, out)) {
                    return false;
                }
                // empty reference elements (<keyword keyref="x"/>, <xref href="y"/>)
                // must survive as zero-length runs — their display text comes
                // from the key/target resolution
                if (out.size() == before) {
                    out.add(new InlineRun("", merged));   // an empty element is still content
                }
            } else {
                return false; // comment/PI inside running text -> raw fallback
            }
        }
        return true;
    }

    /** Folds one inline element into the inherited attribute map; null = unsupported. */
    private Map<String, String> mergeInlineAttrs(Map<String, String> inherited, Element e) {
        Map<String, String> merged = new LinkedHashMap<>(inherited);
        String name = e.getName();
        switch (name) {
            // b/i/u keep any attributes under their own prefix (b:outputclass)
            case "b" -> {
                if (!ownAttributes(e, inherited, merged)) {
                    return null;
                }
                merged.put(InlineStyle.BOLD, InlineStyle.TRUE);
            }
            case "i" -> {
                if (!ownAttributes(e, inherited, merged)) {
                    return null;
                }
                merged.put(InlineStyle.ITALIC, InlineStyle.TRUE);
            }
            case "u" -> {
                if (!ownAttributes(e, inherited, merged)) {
                    return null;
                }
                merged.put(InlineStyle.UNDERLINE, InlineStyle.TRUE);
            }
            case "xref" -> {
                if (inherited.containsKey(InlineStyle.HREF) || inherited.containsKey(InlineStyle.KEYREF)) {
                    return null; // nested links are not representable
                }
                for (Object attrObj : e.attributes()) {
                    org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
                    switch (attr.getName()) {
                        case "href" -> merged.put(InlineStyle.HREF, attr.getValue());
                        case "scope" -> merged.put(InlineStyle.SCOPE, attr.getValue());
                        case "keyref" -> merged.put(InlineStyle.KEYREF, attr.getValue());
                        default -> merged.put(XREF_ATTR_PREFIX + attr.getQualifiedName(), attr.getValue());
                    }
                }
                if (!merged.containsKey(InlineStyle.HREF) && !merged.containsKey(InlineStyle.KEYREF)) {
                    return null; // xref without target — keep verbatim instead
                }
            }
            case "ph" -> {
                if (e.attributes().isEmpty()) {
                    // A plain phrase: a numbered run marker, written back as the outermost
                    // wrapper of the runs that carry it. It may hold styled text, but not sit
                    // inside another inline (the export could not put it back there).
                    if (!inherited.isEmpty()) {
                        return null;
                    }
                    merged.put(InlineStyle.PH, Integer.toString(++phSequence));
                    return merged;
                }
                // otherwise only the keyref form is representable
                for (Object attrObj : e.attributes()) {
                    org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
                    if ("keyref".equals(attr.getName())
                            && !inherited.containsKey(InlineStyle.KEYREF)
                            && !inherited.containsKey(InlineStyle.HREF)) {
                        merged.put(InlineStyle.KEYREF, attr.getValue());
                    } else {
                        return null;
                    }
                }
            }
            default -> {
                if (!InlineStyle.DITA_INLINE_NAMES.contains(name)) {
                    return null;
                }
                String innermost = inherited.get(InlineStyle.DITA_INLINE);
                if (innermost != null) {
                    // Nested semantic inlines: the outer ones form a chain, outermost first. Each
                    // entry carries its element instance ("menucascade#3") so the export groups
                    // the runs of one element together and keeps two siblings apart.
                    String outer = inherited.get(InlineStyle.DITA_INLINE_OUTER);
                    String entry = innermost + "#" + inherited.getOrDefault(InlineStyle.ELEMENT, "0");
                    String chain = outer == null ? entry : outer + "/" + entry;
                    for (String link : chain.split("/")) {
                        // The same element inside itself (an indexterm hierarchy) cannot be
                        // represented by a flat chain: the export would emit the two levels
                        // as siblings. Keep the whole block verbatim instead.
                        if (InlineStyle.chainName(link).equals(name)) {
                            return null;
                        }
                    }
                    merged.put(InlineStyle.DITA_INLINE_OUTER, chain);
                    if (inherited.containsKey(InlineStyle.KEYREF) && !inherited.containsKey(InlineStyle.HREF)
                            && !inherited.containsKey(InlineStyle.PH)) {
                        // the key belongs to the outer element, not to whatever ends up innermost
                        merged.remove(InlineStyle.KEYREF);
                        merged.put(innermost + InlineStyle.INLINE_ATTR_SEPARATOR + "keyref", inherited.get(InlineStyle.KEYREF));
                    }
                }
                // a keyref keeps its own key (<keyword keyref="..."/>); other attributes ride under the element's prefix
                for (Object attrObj : e.attributes()) {
                    org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
                    if ("keyref".equals(attr.getName()) && innermost == null
                            && !inherited.containsKey(InlineStyle.KEYREF)
                            && !inherited.containsKey(InlineStyle.HREF)) {
                        merged.put(InlineStyle.KEYREF, attr.getValue());
                    } else {
                        String key = name + InlineStyle.INLINE_ATTR_SEPARATOR + attr.getQualifiedName();
                        if (inherited.containsKey(key)) {
                            return null;
                        }
                        merged.put(key, attr.getValue());
                    }
                }
                merged.put(InlineStyle.DITA_INLINE, name);
            }
        }
        return merged;
    }

    /**
     * Records {@code e}'s attributes as {@code name:attr} entries. False when
     * the same element already occurs in the inherited chain with attributes,
     * which the export could not tell apart.
     */
    private static boolean ownAttributes(Element e, Map<String, String> inherited, Map<String, String> merged) {
        for (Object attrObj : e.attributes()) {
            org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
            String key = e.getName() + InlineStyle.INLINE_ATTR_SEPARATOR + attr.getQualifiedName();
            if (inherited.containsKey(key)) {
                return false;
            }
            merged.put(key, attr.getValue());
        }
        return true;
    }

    /** Collapses whitespace runs to single spaces across run boundaries; trims edges. */
    private List<InlineRun> normalizeCollapsed(List<InlineRun> runs) {
        List<InlineRun> out = new ArrayList<>(runs.size());
        boolean lastWasSpace = true; // trims leading whitespace
        for (InlineRun run : runs) {
            StringBuilder sb = new StringBuilder(run.text().length());
            for (int i = 0; i < run.text().length(); i++) {
                char c = run.text().charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!lastWasSpace) {
                        sb.append(' ');
                        lastWasSpace = true;
                    }
                } else {
                    sb.append(c);
                    lastWasSpace = false;
                }
            }
            boolean reference = run.attrs().containsKey(InlineStyle.KEYREF)
                    || run.attrs().containsKey(InlineStyle.HREF)
                    || (run.text().isEmpty() && (run.attrs().containsKey(InlineStyle.PH)
                            || run.attrs().containsKey(InlineStyle.DITA_INLINE)));
            if (sb.length() > 0 || reference) {
                out.add(new InlineRun(sb.toString(), run.attrs()));
                if (sb.length() == 0) {
                    // an empty reference run is content (its text comes from the
                    // key/target), so whitespace after it is significant
                    lastWasSpace = false;
                }
            }
        }
        // trim trailing space from the last run (reference runs are kept verbatim)
        for (int i = out.size() - 1; i >= 0; i--) {
            InlineRun last = out.get(i);
            if (last.attrs().containsKey(InlineStyle.KEYREF)
                    || last.attrs().containsKey(InlineStyle.HREF)) {
                break;
            }
            String trimmed = stripTrailing(last.text());
            if (trimmed.isEmpty()) {
                out.remove(i);
                continue;
            }
            if (trimmed.length() != last.text().length()) {
                out.set(i, new InlineRun(trimmed, last.attrs()));
            }
            break;
        }
        return out;
    }

    private List<InlineRun> normalizePreserved(List<InlineRun> runs) {
        return runs.stream().filter(r -> !r.text().isEmpty() || r.attrs().containsKey(InlineStyle.KEYREF)
                || r.attrs().containsKey(InlineStyle.HREF) || r.attrs().containsKey(InlineStyle.PH)
                || r.attrs().containsKey(InlineStyle.DITA_INLINE)).toList();
    }

    private String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private AuthorBlock rawBlock(AuthorDocument doc, Element element) {
        return doc.createRawBlock(element.asXML());
    }

    /** The element's content without namespace declarations, which dom4j lists as nodes. */
    @SuppressWarnings("unchecked")
    private static List<Node> contentOf(Element element) {
        List<Node> all = (List<Node>) (List<?>) element.content();
        boolean hasNs = false;
        for (Node n : all) {
            if (n instanceof Namespace) {
                hasNs = true;
                break;
            }
        }
        if (!hasNs) {
            return all;
        }
        List<Node> out = new ArrayList<>(all.size());
        for (Node n : all) {
            if (!(n instanceof Namespace)) {
                out.add(n);
            }
        }
        return out;
    }

    static String xmlnsKey(String prefix) {
        return prefix == null || prefix.isEmpty() ? XMLNS_ATTR_PREFIX : XMLNS_ATTR_PREFIX + ":" + prefix;
    }

    /** The URI a prefix is bound to on {@code block} or an ancestor, or null. */
    private static String namespaceUri(AuthorBlock block, String prefix) {
        for (AuthorBlock b = block; b != null; b = b.getParent()) {
            String uri = b.getAttributes().get(xmlnsKey(prefix));
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }

    // ==================================================================
    // Export
    // ==================================================================

    @Override
    public Element exportDocument(AuthorDocument doc) {
        return exportBlock(doc.getRoot());
    }

    private Element exportBlock(AuthorBlock block) {
        if (block.getType().getCategory() == BlockType.Category.RAW) {
            return parseRaw(block.getRawXml());
        }
        DocumentFactory factory = DocumentFactory.getInstance();
        Element element = factory.createElement(block.getType().getName());

        String alt = null;
        String linkText = null;
        for (Map.Entry<String, String> attr : block.getAttributes().entrySet()) {
            String key = attr.getKey();
            if (key.startsWith(XMLNS_ATTR_PREFIX)) {
                String prefix = key.length() > XMLNS_ATTR_PREFIX.length()
                        ? key.substring(XMLNS_ATTR_PREFIX.length() + 1) : "";
                element.addNamespace(prefix, attr.getValue());
            }
        }
        for (Map.Entry<String, String> attr : block.getAttributes().entrySet()) {
            String key = attr.getKey();
            if (IMAGE_ALT_ATTR.equals(key)) {
                alt = attr.getValue();
            } else if (LINK_TEXT_ATTR.equals(key)) {
                linkText = attr.getValue();
            } else if (key.startsWith(XMLNS_ATTR_PREFIX)) {
                // declared above
            } else {
                int colon = key.indexOf(':');
                String uri = colon > 0 ? namespaceUri(block, key.substring(0, colon)) : null;
                if (colon > 0 && "xml".equals(key.substring(0, colon))) {
                    element.addAttribute(QName.get(key.substring(colon + 1), Namespace.XML_NAMESPACE), attr.getValue());
                } else if (uri != null) {
                    element.addAttribute(QName.get(key.substring(colon + 1),
                            Namespace.get(key.substring(0, colon), uri)), attr.getValue());
                } else {
                    element.addAttribute(key, attr.getValue());
                }
            }
        }

        emitRuns(element, block.getText());

        if (alt != null) {
            element.addElement("alt").setText(alt);
        }
        if (linkText != null) {
            element.addElement("linktext").setText(linkText);
        }

        for (AuthorBlock child : block.getChildren()) {
            for (XmlAuxNode aux : child.getLeadingAux()) {
                emitAux(element, aux);
            }
            element.add(exportBlock(child));
        }
        for (XmlAuxNode aux : block.getTrailingAux()) {
            emitAux(element, aux);
        }
        return element;
    }

    private void emitAux(Element parent, XmlAuxNode aux) {
        DocumentFactory factory = DocumentFactory.getInstance();
        if (aux.kind() == XmlAuxNode.Kind.COMMENT) {
            parent.add(factory.createComment(aux.text()));
        } else {
            parent.add(factory.createProcessingInstruction(aux.target(), aux.text()));
        }
    }

    /**
     * Emits inline runs as text plus nested inline elements. Consecutive runs
     * sharing a plain-phrase marker go inside one {@code <ph>}, and runs
     * sharing an outer semantic chain ({@code menucascade} around several
     * {@code uicontrol}s) share those outer elements, so a phrase or a menu
     * cascade with several parts comes back as one element.
     */
    private void emitRuns(Element parent, List<InlineRun> runs) {
        Element phrase = null;
        String phraseId = null;
        Element chainEnd = null;
        String chainKey = null;
        for (InlineRun run : runs) {
            Map<String, String> attrs = run.attrs();
            String ph = attrs.get(InlineStyle.PH);
            Element into = parent;
            if (ph != null) {
                if (phrase == null || !ph.equals(phraseId)) {
                    phrase = parent.addElement("ph");
                    phraseId = ph;
                    chainEnd = null;
                }
                into = phrase;
            } else {
                phrase = null;
                phraseId = null;
            }
            String outer = attrs.get(InlineStyle.DITA_INLINE_OUTER);
            if (outer != null) {
                // Runs of one nested element share their wrapper stack: link and styles outside
                // (as they enclosed the whole element on import), then the outer chain itself.
                String key = wrapperKey(attrs);
                if (chainEnd == null || !key.equals(chainKey)) {
                    Map<String, String> wrappers = new LinkedHashMap<>(attrs);
                    wrappers.remove(InlineStyle.DITA_INLINE);
                    wrappers.remove(InlineStyle.DITA_INLINE_OUTER);
                    wrappers.remove(InlineStyle.PH);
                    chainEnd = wrapRun(into, wrappers);
                    for (String link : outer.split("/")) {
                        chainEnd = ownAttributesBack(chainEnd.addElement(InlineStyle.chainName(link)), attrs);
                    }
                    chainKey = key;
                }
                Element semantic = ownAttributesBack(chainEnd.addElement(attrs.get(InlineStyle.DITA_INLINE)), attrs);
                if (!run.text().isEmpty()) {
                    semantic.addText(run.text());
                }
                continue;
            }
            chainEnd = null;
            chainKey = null;
            if (ph != null) {
                attrs = new LinkedHashMap<>(attrs);
                attrs.remove(InlineStyle.PH);
            }
            Element target = wrapRun(into, attrs);
            if (!run.text().isEmpty()) {
                target.addText(run.text());
            }
        }
    }

    /** What decides whether two consecutive chain runs share their wrappers: everything but the innermost element and its own attributes. */
    private static String wrapperKey(Map<String, String> attrs) {
        StringBuilder sb = new StringBuilder();
        String inner = attrs.get(InlineStyle.DITA_INLINE);
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String k = e.getKey();
            if (k.equals(InlineStyle.DITA_INLINE) || k.equals(InlineStyle.ELEMENT)
                    || (inner != null && k.startsWith(inner + InlineStyle.INLINE_ATTR_SEPARATOR))) {
                continue;
            }
            sb.append(k).append('=').append(e.getValue()).append('\u0001');
        }
        return sb.toString();
    }

    /**
     * Builds the inline element chain for one run's attributes, outermost
     * first: xref/keyref, then b, i, u, then the semantic inline. Returns the
     * innermost element (or {@code parent} for plain runs).
     */
    private Element wrapRun(Element parent, Map<String, String> attrs) {
        Element current = parent;
        String semantic = attrs.get(InlineStyle.DITA_INLINE);
        boolean isXref = attrs.containsKey(InlineStyle.HREF)
                || attrs.keySet().stream().anyMatch(k -> k.startsWith(XREF_ATTR_PREFIX));
        boolean keyrefOnSemantic = semantic != null && !isXref && attrs.containsKey(InlineStyle.KEYREF);

        if (isXref) {
            Element link = current.addElement("xref");
            addIfPresent(link, "href", attrs.get(InlineStyle.HREF));
            addIfPresent(link, "scope", attrs.get(InlineStyle.SCOPE));
            addIfPresent(link, "keyref", attrs.get(InlineStyle.KEYREF));
            for (Map.Entry<String, String> e : attrs.entrySet()) {
                if (e.getKey().startsWith(XREF_ATTR_PREFIX)) {
                    link.addAttribute(e.getKey().substring(XREF_ATTR_PREFIX.length()), e.getValue());
                }
            }
            current = link;
        } else if (attrs.containsKey(InlineStyle.KEYREF) && semantic == null) {
            Element ph = current.addElement("ph");
            ph.addAttribute("keyref", attrs.get(InlineStyle.KEYREF));
            current = ph;
        }
        if (InlineStyle.isBold(attrs)) {
            current = ownAttributesBack(current.addElement("b"), attrs);
        }
        if (InlineStyle.isItalic(attrs)) {
            current = ownAttributesBack(current.addElement("i"), attrs);
        }
        if (InlineStyle.isUnderline(attrs)) {
            current = ownAttributesBack(current.addElement("u"), attrs);
        }
        if (semantic != null) {
            current = ownAttributesBack(current.addElement(semantic), attrs);
            if (keyrefOnSemantic) {
                // <keyword keyref="..."/> style — keyref rides on the semantic element
                current.addAttribute("keyref", attrs.get(InlineStyle.KEYREF));
            }
        }
        return current;
    }

    /** Puts the {@code name:attr} entries for this element back on it. */
    private static Element ownAttributesBack(Element e, Map<String, String> attrs) {
        String prefix = e.getName() + InlineStyle.INLINE_ATTR_SEPARATOR;
        for (Map.Entry<String, String> a : attrs.entrySet()) {
            if (a.getKey().startsWith(prefix)) {
                e.addAttribute(a.getKey().substring(prefix.length()), a.getValue());
            }
        }
        return e;
    }

    private void addIfPresent(Element element, String name, String value) {
        if (value != null) {
            element.addAttribute(name, value);
        }
    }

    /** Re-parses a raw block's verbatim source back into an element. */
    private Element parseRaw(String rawXml) {
        try {
            SAXReader reader = new SAXReader();
            reader.setValidation(false);
            org.dom4j.Document parsed = reader.read(new StringReader(rawXml));
            Element root = parsed.getRootElement();
            root.detach();
            return root;
        } catch (DocumentException e) {
            throw new IllegalStateException("raw block is no longer well-formed: " + e.getMessage(), e);
        }
    }

    private String collapse(String s) {
        return s.replaceAll("\\s+", " ");
    }
}
