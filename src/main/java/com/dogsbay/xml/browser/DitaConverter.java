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
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Converts a DITA topic or map to a styled HTML document for the preview
 * panel. Best-effort rendering, not a DITA-OT replacement — but
 * context-aware: keyref/conkeyref resolve against the context map's key
 * space, DITAVAL filtering applies, and conref/conkeyref content is
 * transcluded in place ({@link ConrefTranscluder}; unresolvable
 * references render as visible badges).
 *
 * <p>The transform is the bundled {@code dita-preview.xsl}, compiled once
 * with Saxon (XSLT 2.0), using the s9api directly rather than going through
 * the JAXP factory. Parsing suppresses all
 * external DTD/entity fetching, so preview works without catalogs and never
 * touches the network — same hardening as {@code DitaMapParser}.
 *
 * @author DogsBay Ltd
 */
public class DitaConverter {

    /** Root element local names treated as DITA documents. */
    private static final java.util.Set<String> DITA_ROOTS = java.util.Set.of(
            "concept", "task", "reference", "topic", "troubleshooting",
            "glossentry", "glossgroup", "dita", "map", "bookmap");

    private static final Processor PROCESSOR;
    private static final XsltExecutable EXECUTABLE;
    private static final Exception INIT_ERROR;

    static {
        Processor processor = null;
        XsltExecutable executable = null;
        Exception initError = null;
        try {
            processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            executable = compiler.compile(new javax.xml.transform.stream.StreamSource(
                    DitaConverter.class.getResourceAsStream("dita-preview.xsl")));
        } catch (Exception e) {
            initError = e;
        }
        PROCESSOR = processor;
        EXECUTABLE = executable;
        INIT_ERROR = initError;
    }

    /**
     * Cheap sniff: is this XML a DITA topic or map? True when the DOCTYPE
     * names an OASIS DITA DTD, or the root element is a known DITA root.
     * Never throws; malformed input returns false.
     */
    public static boolean isDita(String xml) {
        if (xml == null || xml.isEmpty()) {
            return false;
        }
        String head = xml.substring(0, Math.min(xml.length(), 4096));
        if (head.contains("-//OASIS//DTD DITA")) {
            return true;
        }
        String root = sniffRootElement(head);
        return root != null && DITA_ROOTS.contains(root);
    }

    /** The document as if every review proposal were accepted; an odd document comes back as it is. */
    static String acceptedView(String ditaXml) {
        if (!com.dogsbay.xml.review.ProposalIndex.mayHaveProposals(ditaXml)) {
            return ditaXml;
        }
        try {
            return com.dogsbay.xml.review.ProposalOps.acceptedView(ditaXml);
        } catch (RuntimeException e) {
            return ditaXml;
        }
    }

    /**
     * Converts DITA source text to a complete, styled HTML document.
     * Parse or transform failures return a styled error document (never
     * null, never throws) so live preview degrades gracefully mid-edit.
     *
     * @param ditaXml the DITA source text
     * @param baseDir directory of the source file, for resolving relative
     *                image hrefs; may be null (images left unresolved)
     * @return a full HTML document string with embedded CSS
     */
    public static String convert(String ditaXml, File baseDir) {
        return convert(ditaXml, baseDir, null);
    }

    /**
     * Context-aware conversion: when {@code options} carries a context map,
     * keyref/conkeyref resolve against its key space; when it carries a
     * ditaval, excluded profiled content is filtered out before rendering.
     *
     * @param options rendering context; null or empty behaves like
     *                {@link #convert(String, File)}
     */
    public static String convert(String ditaXml, File baseDir, PreviewOptions options) {
        if (ditaXml == null || ditaXml.isEmpty()) {
            return wrapHtml("");
        }
        if (EXECUTABLE == null) {
            return errorHtml("DITA preview stylesheet failed to load", INIT_ERROR);
        }
        // Open review proposals render as if accepted: inserted text as text,
        // struck text and draft comments gone. With showChanges the marks stay
        // and the stylesheet draws them as insertions, deletions and comments.
        boolean showChanges = options != null && options.isShowChanges();
        if (!showChanges) {
            ditaXml = acceptedView(ditaXml);
        }
        try {
            javax.xml.transform.Source source;
            DitavalFilter filter = options != null && options.getDitaval() != null
                    ? DitavalFilter.parse(options.getDitaval())
                    : DitavalFilter.empty();
            // Cheap test for whether the transclusion pass is needed at all.
            // NB "conkeyref" does NOT contain the substring "conref".
            boolean hasConref = ditaXml.contains("conref")
                    || ditaXml.contains("conkeyref");
            if (!filter.isEmpty() || hasConref) {
                // Pre-transform DOM passes: DITAVAL filtering (the
                // all-tokens-excluded rule can't be expressed downstream in
                // CSS), then conref transclusion.
                org.w3c.dom.Document dom = DitavalFilter.hardenedBuilder()
                        .parse(new InputSource(new StringReader(ditaXml)));
                if (!filter.isEmpty()) {
                    filter.apply(dom);
                }
                if (hasConref) {
                    com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                            options != null && options.getDitaContextMap() != null
                                    ? cachedKeySpace(options.getDitaContextMap(),
                                            options.getDitaval())
                                    : com.dogsbay.dogsbayaieditor.links.KeySpace.empty();
                    ConrefTranscluder.apply(dom, baseDir, keySpace, filter, showChanges);
                }
                source = new javax.xml.transform.dom.DOMSource(dom);
            } else {
                source = new SAXSource(createHardenedReader(),
                        new InputSource(new StringReader(ditaXml)));
            }

            Xslt30Transformer transformer = EXECUTABLE.load30();

            java.util.Map<QName, net.sf.saxon.s9api.XdmValue> params = new java.util.HashMap<>();
            if (baseDir != null) {
                String base = baseDir.toURI().toString();
                if (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                params.put(new QName("base-uri"), new XdmAtomicValue(base));
            }
            if (options != null && options.getDitaContextMap() != null) {
                // The ditaval also conditions the KEY SPACE (profiled keydefs
                // and keyword variants are filtered before resolution, as in
                // DITA-OT) — not just the previewed document.
                net.sf.saxon.s9api.XdmNode keyspaceDoc = keySpaceDocument(
                        options.getDitaContextMap(), options.getDitaval());
                if (keyspaceDoc != null) {
                    params.put(new QName("keyspace"), keyspaceDoc);
                }
            }
            if (showChanges) {
                params.put(new QName("show-changes"), new XdmAtomicValue(true));
            }
            if (!params.isEmpty()) {
                transformer.setStylesheetParameters(params);
            }

            StringWriter out = new StringWriter();
            Serializer serializer = PROCESSOR.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.METHOD, "html");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            transformer.transform(source, serializer);

            return wrapHtml(out.toString());
        } catch (Exception e) {
            return errorHtml("Cannot render DITA preview", e);
        }
    }

    // -------------------------------------------------------------------------
    // Key space (cached per root map)
    // -------------------------------------------------------------------------

    private record CachedKeySpace(long mapStamp, long ditavalStamp,
                                  net.sf.saxon.s9api.XdmNode doc) {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedKeySpace>
            KEYSPACE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedKeySpaceObject(long mapStamp, long ditavalStamp,
                                        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace) {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedKeySpaceObject>
            KEYSPACE_OBJECT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The {@link com.dogsbay.dogsbayaieditor.links.KeySpace} of
     * {@code rootMap}, ditaval-conditioned and cached by mtimes like the
     * serialized variant — the transcluder needs the object form for
     * conkeyref resolution.
     */
    private static com.dogsbay.dogsbayaieditor.links.KeySpace cachedKeySpace(
            File rootMap, File ditaval) {
        try {
            String cacheKey = rootMap.getCanonicalPath() + "|"
                    + (ditaval != null ? ditaval.getCanonicalPath() : "-");
            long mapStamp = rootMap.lastModified();
            long ditavalStamp = ditaval != null ? ditaval.lastModified() : 0;
            CachedKeySpaceObject cached = KEYSPACE_OBJECT_CACHE.get(cacheKey);
            if (cached != null && cached.mapStamp() == mapStamp
                    && cached.ditavalStamp() == ditavalStamp) {
                return cached.keySpace();
            }
            DitavalFilter filter = ditaval != null
                    ? DitavalFilter.parse(ditaval) : DitavalFilter.empty();
            com.dogsbay.dogsbayaieditor.links.ElementExclusion exclusion =
                    filter.isEmpty() ? null : filter::isExcluded;
            com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                    com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(rootMap, exclusion);
            if (KEYSPACE_OBJECT_CACHE.size() > 8) {
                KEYSPACE_OBJECT_CACHE.clear();
            }
            KEYSPACE_OBJECT_CACHE.put(cacheKey,
                    new CachedKeySpaceObject(mapStamp, ditavalStamp, keySpace));
            return keySpace;
        } catch (Exception e) {
            return com.dogsbay.dogsbayaieditor.links.KeySpace.empty();
        }
    }

    /**
     * The key space of {@code rootMap}, conditioned by {@code ditaval}
     * (may be null), serialized as a small XML document the stylesheet can
     * query: {@code <keys><key name href text linktext/></keys>}. Cached by
     * map + ditaval path and lastModified so per-keystroke refreshes don't
     * re-walk the map tree. Null when the map yields no keys.
     */
    private static net.sf.saxon.s9api.XdmNode keySpaceDocument(File rootMap, File ditaval) {
        try {
            String cacheKey = rootMap.getCanonicalPath() + "|"
                    + (ditaval != null ? ditaval.getCanonicalPath() : "-");
            long mapStamp = rootMap.lastModified();
            long ditavalStamp = ditaval != null ? ditaval.lastModified() : 0;
            CachedKeySpace cached = KEYSPACE_CACHE.get(cacheKey);
            if (cached != null && cached.mapStamp() == mapStamp
                    && cached.ditavalStamp() == ditavalStamp) {
                return cached.doc();
            }

            DitavalFilter filter = ditaval != null
                    ? DitavalFilter.parse(ditaval) : DitavalFilter.empty();
            com.dogsbay.dogsbayaieditor.links.ElementExclusion exclusion =
                    filter.isEmpty() ? null : filter::isExcluded;

            com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                    com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(rootMap, exclusion);
            if (keySpace.isEmpty()) {
                return null;
            }

            StringBuilder xml = new StringBuilder("<keys>");
            // Keys are stored under their fully-qualified (scoped) name; emit those
            // plus a bare alias for any key unique to a single scope, so a bare keyref
            // authored inside a @keyscope (whose scope we can't see cross-file) still
            // resolves in preview. The alias rule lives in KeySpace (one source).
            keySpace.entriesWithBareAliases().forEach((name, def) -> {
                xml.append("<key name=\"").append(escapeAttr(name)).append('"');
                if (def.href() != null) {
                    xml.append(" href=\"").append(escapeAttr(def.href())).append('"');
                }
                // Resolved absolute file URL — keydef hrefs are relative to
                // the DEFINING MAP's directory, which the stylesheet can't
                // know. Lets keyref images render and key targets resolve.
                java.io.File targetFile = keySpace.resolveHrefFileLenient(name, "");
                if (targetFile != null && targetFile.isFile()) {
                    xml.append(" fileurl=\"")
                            .append(escapeAttr(targetFile.toURI().toString()))
                            .append('"');
                }
                if (def.keywordText() != null) {
                    xml.append(" text=\"").append(escapeAttr(def.keywordText())).append('"');
                }
                if (def.linkText() != null) {
                    xml.append(" linktext=\"").append(escapeAttr(def.linkText())).append('"');
                }
                xml.append("/>");
            });
            xml.append("</keys>");

            net.sf.saxon.s9api.XdmNode doc = PROCESSOR.newDocumentBuilder().build(
                    new javax.xml.transform.stream.StreamSource(
                            new StringReader(xml.toString())));
            if (KEYSPACE_CACHE.size() > 8) {
                KEYSPACE_CACHE.clear();
            }
            KEYSPACE_CACHE.put(cacheKey, new CachedKeySpace(mapStamp, ditavalStamp, doc));
            return doc;
        } catch (Exception e) {
            return null;        // no key space beats a broken preview
        }
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Returns the local name of the document's root element, or null.
     * Skips the XML declaration, comments, PIs and the DOCTYPE without
     * a full parse.
     */
    static String sniffRootElement(String xml) {
        int i = 0;
        int len = xml.length();
        while (i < len) {
            int lt = xml.indexOf('<', i);
            if (lt < 0 || lt + 1 >= len) {
                return null;
            }
            char next = xml.charAt(lt + 1);
            if (next == '?') {                       // <?xml ... ?> or PI
                i = xml.indexOf("?>", lt);
                if (i < 0) return null;
                i += 2;
            } else if (next == '!') {
                if (xml.startsWith("<!--", lt)) {     // comment
                    i = xml.indexOf("-->", lt);
                    if (i < 0) return null;
                    i += 3;
                } else {                              // DOCTYPE — skip to its closing '>'
                    int depth = 0;
                    int j = lt;
                    while (j < len) {
                        char c = xml.charAt(j);
                        if (c == '[') depth++;
                        else if (c == ']') depth--;
                        else if (c == '>' && depth == 0) break;
                        j++;
                    }
                    if (j >= len) return null;
                    i = j + 1;
                }
            } else if (Character.isLetter(next) || next == '_') {
                int end = lt + 1;
                while (end < len) {
                    char c = xml.charAt(end);
                    if (Character.isWhitespace(c) || c == '>' || c == '/') break;
                    end++;
                }
                String name = xml.substring(lt + 1, end);
                int colon = name.indexOf(':');
                return colon >= 0 ? name.substring(colon + 1) : name;
            } else {
                return null;                          // not well-formed enough to sniff
            }
        }
        return null;
    }

    /**
     * An XMLReader that never fetches external DTDs or entities (preview
     * must work offline and without catalogs).
     */
    private static XMLReader createHardenedReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver((publicId, systemId) ->
                new InputSource(new StringReader("")));
        return reader;
    }

    private static void trySetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // parser doesn't know this feature — the EntityResolver still guards
        }
    }

    private static String errorHtml(String message, Exception cause) {
        String detail = cause != null && cause.getMessage() != null
                ? cause.getMessage() : "";
        return wrapHtml("<div class=\"preview-error\">"
                + "<p class=\"preview-error-title\">" + escape(message) + "</p>"
                + (detail.isEmpty() ? "" : "<pre>" + escape(detail) + "</pre>")
                + "<p class=\"preview-error-hint\">The preview updates as you type — "
                + "fix the XML and it will recover.</p>"
                + "</div>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String wrapHtml(String bodyContent) {
        return "<!DOCTYPE html>\n"
                + "<html>\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<style>\n" + CSS + "</style>\n"
                + "</head>\n<body>\n"
                + bodyContent
                + "\n</body>\n</html>";
    }

    /** DITA preview CSS — same visual family as the Markdown preview. */
    private static final String CSS =
            "body {\n"
            + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n"
            + "  font-size: 14px;\n"
            + "  line-height: 1.6;\n"
            + "  color: #24292e;\n"
            + "  max-width: 880px;\n"
            + "  margin: 0 auto;\n"
            + "  padding: 20px 30px;\n"
            + "}\n"
            + "h1, h2, h3 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; line-height: 1.25; }\n"
            + "h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: .3em; }\n"
            + "h2 { font-size: 1.5em; }\n"
            + "p { margin-top: 0; margin-bottom: 16px; }\n"
            + "a.xref { color: #0366d6; text-decoration: none; border-bottom: 1px dashed #0366d6; cursor: default; }\n"
            + ".shortdesc { font-size: 1.05em; color: #57606a; margin-bottom: 20px; }\n"
            + ".section { margin-bottom: 8px; }\n"
            + ".task-label { font-weight: 600; margin-bottom: 4px; color: #57606a;\n"
            + "  text-transform: uppercase; font-size: 0.78em; letter-spacing: 0.04em; }\n"
            + "ol.steps { padding-left: 1.6em; }\n"
            + "ol.steps > li.step { margin-bottom: 10px; }\n"
            + ".cmd { font-weight: 600; }\n"
            + ".step-info { color: #444c56; margin: 4px 0 4px 2px; }\n"
            + "ins.review-new { background: #dcf5dc; text-decoration: none; }\n"
            + "del.review-deleted { background: #f8d7d7; color: #9a2f2f; }\n"
            + "mark.review-changed { background: #fff3cd; color: inherit; }\n"
            + ".review-comment { display: inline-block; background: #fff8c5; border: 1px solid #e6d98a;\n"
            + "  border-radius: 3px; padding: 0 6px; margin: 0 4px; font-size: 0.85em; color: #5c4d00; }\n"
            + ".review-comment .who { font-weight: 600; margin-right: 4px; }\n"
            + ".note { border-left: 4px solid #888; background: #f6f8fa; padding: 10px 14px;\n"
            + "  margin: 0 0 16px 0; border-radius: 0 4px 4px 0; }\n"
            + ".note p:last-child { margin-bottom: 0; }\n"
            + ".note-label { font-weight: 700; font-size: 0.8em; text-transform: uppercase;\n"
            + "  letter-spacing: 0.05em; margin-bottom: 4px; }\n"
            + ".note-note, .note-other { border-color: #0969da; } .note-note .note-label { color: #0969da; }\n"
            + ".note-tip, .note-remember { border-color: #1a7f37; } .note-tip .note-label, .note-remember .note-label { color: #1a7f37; }\n"
            + ".note-important, .note-attention, .note-restriction { border-color: #9a6700; }\n"
            + ".note-important .note-label, .note-attention .note-label, .note-restriction .note-label { color: #9a6700; }\n"
            + ".note-warning, .note-caution, .note-trouble { border-color: #bc4c00; background: #fff8f0; }\n"
            + ".note-warning .note-label, .note-caution .note-label, .note-trouble .note-label { color: #bc4c00; }\n"
            + ".note-danger { border-color: #cf222e; background: #fff5f5; } .note-danger .note-label { color: #cf222e; }\n"
            + "pre.codeblock { background-color: #f6f8fa; border-radius: 4px; font-size: 85%;\n"
            + "  line-height: 1.45; overflow: auto; padding: 14px; }\n"
            + "code, kbd, samp, var {\n"
            + "  background-color: rgba(27,31,35,.05); border-radius: 3px; font-size: 85%;\n"
            + "  padding: .2em .4em;\n"
            + "  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;\n"
            + "}\n"
            + "pre code { background: transparent; padding: 0; font-size: 100%; }\n"
            + "kbd { border: 1px solid #d0d7de; border-bottom-width: 2px; background: #f6f8fa; }\n"
            + ".uicontrol { font-weight: 600; }\n"
            + ".menucascade .menu-sep { color: #57606a; }\n"
            + ".term { font-style: italic; }\n"
            + ".fig { margin: 0 0 16px 0; }\n"
            + ".fig-title, .table-title { font-weight: 600; font-size: 0.9em; color: #57606a; }\n"
            + ".image-block { margin: 12px 0; }\n"
            + ".image-unresolved { display: inline-block; border: 1px dashed #8c959f;\n"
            + "  color: #57606a; border-radius: 4px; padding: 2px 8px; font-size: 0.85em; }\n"
            + "img { max-width: 100%; }\n"
            + ".table-wrap { overflow-x: auto; margin-bottom: 16px; }\n"
            + "table { border-collapse: collapse; border-spacing: 0; }\n"
            + "table th, table td { border: 1px solid #dfe2e5; padding: 6px 13px; }\n"
            + "table th { font-weight: 600; background-color: #f6f8fa; }\n"
            + "table tr:nth-child(2n) { background-color: #f6f8fa; }\n"
            + "dl dt { font-weight: 600; margin-top: 8px; }\n"
            + "dl dd { margin-left: 1.5em; }\n"
            + "blockquote { border-left: .25em solid #dfe2e5; color: #6a737d; margin: 0 0 16px 0; padding: 0 1em; }\n"
            + ".conref-badge {\n"
            + "  display: inline-block; border: 1px dashed #8250df; color: #8250df;\n"
            + "  border-radius: 4px; padding: 0 6px; font-size: 0.82em; background: #fbf6ff;\n"
            + "}\n"
            + ".conref-included { border-left: 3px solid #d8b9f8; padding-left: 10px;\n"
            + "  margin-bottom: 16px; }\n"
            + ".conref-included > *:last-child { margin-bottom: 0; }\n"
            + ".keyref { border-bottom: 1px dotted #8250df; color: #8250df; }\n"
            + ".keyref-resolved { border-bottom: 1px dotted #1a7f37; }\n"
            + "a.keyref-resolved { color: #0366d6; }\n"
            + ".related-links { margin-top: 28px; border-top: 1px solid #eaecef; padding-top: 10px; }\n"
            + ".map-title { margin-bottom: 4px; }\n"
            + ".map-note { color: #57606a; font-size: 0.85em; margin-bottom: 18px; }\n"
            + "ul.toc { list-style: none; padding-left: 1.2em; }\n"
            + "ul.toc > li.toc-entry { margin: 3px 0; }\n"
            + ".toc-submap { color: #57606a; }\n"
            + ".toc-badge { font-size: 0.72em; border: 1px solid #d0d7de; border-radius: 8px;\n"
            + "  padding: 0 6px; color: #57606a; vertical-align: middle; }\n"
            + ".toc-group { color: #8c959f; }\n"
            + ".preview-error { border: 1px solid #cf222e; border-radius: 6px; padding: 14px 18px;\n"
            + "  background: #fff5f5; }\n"
            + ".preview-error-title { color: #cf222e; font-weight: 600; }\n"
            + ".preview-error-hint { color: #57606a; font-size: 0.9em; margin-bottom: 0; }\n";
}
