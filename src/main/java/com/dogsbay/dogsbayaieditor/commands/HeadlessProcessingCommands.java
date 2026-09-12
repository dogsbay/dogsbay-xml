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

package com.dogsbay.dogsbayaieditor.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import com.dogsbay.dogsbayaieditor.commands.results.*;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.dogsbayaieditor.ditaproject.FileSet;
import com.dogsbay.dogsbayaieditor.validate.DocumentValidator;

/**
 * Core headless XML-processing command handlers (validate, parse, transform, query,
 * format, reflow, info), extracted from {@link HeadlessExecutor} (Slice 3 of the
 * large-file split). Stateless static handlers dispatched from HeadlessExecutor.execute,
 * fully self-contained (own private helpers, no shared state). Behavior-preserving move.
 */
final class HeadlessProcessingCommands {

    private HeadlessProcessingCommands() {
    }

    // ── Validate ────────────────────────────────────────────────────────

    static ValidationResult executeValidate(ValidateCommand cmd) throws CommandException {
        requireFile(cmd.file());
        if (cmd.schema() != null) {
            requireFile(cmd.schema());
        }
        // Catalog-aware validation (incl. the bundled-DITA-catalog default) lives
        // in the shared DocumentValidator so single-file, batch, and CLI agree.
        try {
            return DocumentValidator.validate(cmd.file(), cmd.schema(), cmd.catalogs());
        } catch (DocumentValidator.ValidationSetupException e) {
            throw new CommandException(
                CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
        }
    }

    /**
     * A catalog-aware {@link DocumentBuilder} for the raw-XML tools (parse, query,
     * info, and the transform input). DITA DOCTYPEs resolve through the editor's
     * bundled catalog — the same mechanism {@code validate} uses — so PUBLIC
     * doctypes and DTD-defined entities resolve. Installed editors and the
     * {@code dogsbay} CLI set {@code xml.catalog.files} at startup; see
     * {@link com.dogsbay.xml.XMLUtilities#getCatalogResolver()}.
     *
     * <p>{@code format} does not use this builder — it serializes the source back
     * via {@link com.dogsbay.xml.XMLUtilities#format} (mixed-content preserving),
     * which parses with the external DTD off so no DTD-default attributes leak
     * into the pretty-printed output.
     */
    private static DocumentBuilder newToolingDocumentBuilder()
            throws javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // DOCTYPE stays allowed (DITA needs catalog resolution) but external
        // general/parameter entities are blocked — see SecureXml.
        com.dogsbay.xml.SecureXml.hardenDom(factory, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(toolingEntityResolver());
        return builder;
    }

    /**
     * The bundled DITA catalog first, the system catalogs after it. Without the
     * DITA half, parsing a {@code .ditamap} headlessly fails on the
     * {@code map.dtd} its DOCTYPE names, which is resolvable only through that
     * catalog — the same catalog {@code validate} already uses.
     */
    private static org.xml.sax.EntityResolver toolingEntityResolver() {
        org.xml.sax.EntityResolver dita =
                com.dogsbay.dogsbayaieditor.validate.DocumentValidator.builtinDitaResolver();
        org.xml.sax.EntityResolver system = com.dogsbay.xml.XMLUtilities.getCatalogResolver();
        if (dita == null) {
            return system;
        }
        return (publicId, systemId) -> {
            org.xml.sax.InputSource resolved = dita.resolveEntity(publicId, systemId);
            return resolved != null ? resolved : system.resolveEntity(publicId, systemId);
        };
    }

    /**
     * The encoding declared in the XML prolog (e.g. {@code <?xml … encoding="…"?>}),
     * or {@code UTF-8} when absent or unknown. Used so {@code format} reads and
     * re-emits a non-UTF-8 document in its own encoding rather than corrupting it.
     */
    private static String sniffXmlEncoding(byte[] bytes) {
        int n = Math.min(bytes.length, 256);
        String head = new String(bytes, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']").matcher(head);
        if (m.find()) {
            try {
                java.nio.charset.Charset.forName(m.group(1));
                return m.group(1);
            } catch (Exception ignore) {
                // unknown/unsupported encoding name → fall back to UTF-8
            }
        }
        return "UTF-8";
    }

    // ── Parse ───────────────────────────────────────────────────────────

    static ParseResult executeParse(ParseCommand cmd) throws CommandException {
        requireFile(cmd.file());

        List<ValidationError> errors = new ArrayList<>();

        try {
            DocumentBuilder builder = newToolingDocumentBuilder();
            builder.setErrorHandler(collectingHandler(errors));

            Document doc = builder.parse(cmd.file().toFile());

            String rootName = doc.getDocumentElement() != null
                ? doc.getDocumentElement().getLocalName() : null;
            String rootNs = doc.getDocumentElement() != null
                ? doc.getDocumentElement().getNamespaceURI() : null;
            String encoding = doc.getXmlEncoding() != null
                ? doc.getXmlEncoding() : "UTF-8";

            return new ParseResult(
                cmd.file(), errors.isEmpty(),
                rootName, rootNs, encoding, errors
            );
        } catch (SAXParseException e) {
            errors.add(new ValidationError(
                e.getLineNumber(), e.getColumnNumber(),
                "error", e.getMessage(), "parser"
            ));
            return new ParseResult(cmd.file(), false, null, null, null, errors);
        } catch (SAXException e) {
            errors.add(new ValidationError(-1, -1, "error", e.getMessage(), "parser"));
            return new ParseResult(cmd.file(), false, null, null, null, errors);
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.PARSE_ERROR,
                "Parse failed: " + e.getMessage(), e
            );
        }
    }

    // ── Transform ───────────────────────────────────────────────────────

    static TransformResult executeTransform(TransformCommand cmd) throws CommandException {
        requireFile(cmd.input());
        requireFile(cmd.xslt());

        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            com.dogsbay.xml.SecureXml.hardenTransformerFactory(factory);
            Transformer transformer = factory.newTransformer(
                new StreamSource(cmd.xslt().toFile())
            );

            if (cmd.parameters() != null) {
                for (Map.Entry<String, String> entry : cmd.parameters().entrySet()) {
                    transformer.setParameter(entry.getKey(), entry.getValue());
                }
            }

            List<String> warnings = new ArrayList<>();

            // Parse the input through the catalog-aware builder (resolves DITA
            // PUBLIC doctypes), then feed the transformer a DOMSource. Carry the
            // input's base URI so a stylesheet can still resolve relative
            // references (document(), base-uri(), …) as it did with a StreamSource.
            DOMSource inputSource = new DOMSource(
                newToolingDocumentBuilder().parse(cmd.input().toFile()));
            inputSource.setSystemId(cmd.input().toUri().toString());

            if (cmd.output() != null) {
                transformer.transform(
                    inputSource,
                    new StreamResult(cmd.output().toFile())
                );
                return new TransformResult(
                    cmd.input(), cmd.xslt(), cmd.output(), null, warnings
                );
            } else {
                StringWriter writer = new StringWriter();
                transformer.transform(
                    inputSource,
                    new StreamResult(writer)
                );
                return new TransformResult(
                    cmd.input(), cmd.xslt(), null, writer.toString(), warnings
                );
            }
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.TRANSFORM_ERROR,
                "Transform failed: " + e.getMessage(), e
            );
        }
    }

    // ── Query (XPath) ───────────────────────────────────────────────────

    static List<QueryResult> executeQuery(QueryCommand cmd) throws CommandException {
        List<Path> files = resolveGlob(cmd.filePattern());
        if (files.isEmpty()) {
            throw new CommandException(
                CommandException.ErrorCode.FILE_NOT_FOUND,
                "No files match pattern: " + cmd.filePattern()
            );
        }

        List<QueryResult> results = new ArrayList<>();
        XPathFactory xpathFactory = XPathFactory.newInstance();

        for (Path file : files) {
            try {
                Document doc = newToolingDocumentBuilder().parse(file.toFile());

                XPath xpath = xpathFactory.newXPath();
                NodeList nodeList = (NodeList) xpath.evaluate(
                    cmd.xpath(), doc, XPathConstants.NODESET
                );

                List<XPathMatch> matches = new ArrayList<>();
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    matches.add(new XPathMatch(
                        -1, -1,
                        node.getNodeName(),
                        nodeTypeString(node.getNodeType()),
                        node.getTextContent(),
                        null
                    ));
                }

                if (!matches.isEmpty()) {
                    results.add(new QueryResult(file, cmd.xpath(), matches));
                }
            } catch (javax.xml.xpath.XPathExpressionException e) {
                throw new CommandException(
                    CommandException.ErrorCode.XPATH_ERROR,
                    "Invalid XPath expression: " + cmd.xpath(), e
                );
            } catch (Exception e) {
                // Skip files that can't be parsed
                results.add(new QueryResult(file, cmd.xpath(), List.of(
                    new XPathMatch(-1, -1, null, "error",
                        "Failed to parse: " + e.getMessage(), null)
                )));
            }
        }

        return results;
    }

    // ── Format ──────────────────────────────────────────────────────────

    static FormatResult executeFormat(FormatCommand cmd) throws CommandException {
        requireFile(cmd.file());

        try {
            byte[] bytes = Files.readAllBytes(cmd.file());
            String encoding = sniffXmlEncoding(bytes);
            java.nio.charset.Charset cs = java.nio.charset.Charset.forName(encoding);
            String original = new String(bytes, cs);
            String systemId = cmd.file().toUri().toString();

            // Resolve the project house style (.dogsbay/config.xml), else the built-in
            // default; an explicit --indent overrides the indent size. The canonical
            // FormatEngine preserves mixed content + DITA verbatim blocks (codeblock,
            // pre, …) and never hard-wraps prose, so it won't mangle significant
            // whitespace — and it's idempotent, so format-on-save stays a no-op.
            com.dogsbay.xml.format.FormatStyle style =
                com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.forFile(cmd.file());
            if (cmd.indent() != null && cmd.indent() > 0) {
                style = style.withIndent(
                    com.dogsbay.xml.format.FormatStyle.IndentUnit.SPACES, cmd.indent());
            }
            // Preserve the file's existing line endings, so formatting doesn't silently
            // rewrite a CRLF file to LF (which would churn the whole file and make
            // --check flag it forever). Matches the GUI save path's CRLF handling.
            style = style.withNewline(original.contains("\r\n")
                ? com.dogsbay.xml.format.FormatStyle.NewlineStyle.CRLF
                : com.dogsbay.xml.format.FormatStyle.NewlineStyle.LF);
            String formatted = com.dogsbay.xml.format.FormatEngine.format(
                original, systemId, encoding, style);

            if (cmd.output() != null) {
                Files.write(cmd.output(), formatted.getBytes(cs));
            }

            return new FormatResult(cmd.file(), formatted, !formatted.equals(original));
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.PARSE_ERROR,
                "Format failed: " + e.getMessage(), e
            );
        }
    }

    static FormatResult executeReflow(ReflowCommand cmd) throws CommandException {
        requireFile(cmd.file());

        try {
            byte[] bytes = Files.readAllBytes(cmd.file());
            String encoding = sniffXmlEncoding(bytes);
            java.nio.charset.Charset cs = java.nio.charset.Charset.forName(encoding);
            String original = new String(bytes, cs);
            String systemId = cmd.file().toUri().toString();

            // Reflow prose to one sentence per line (verbatim blocks untouched) and
            // canonically format, preserving the file's line endings.
            com.dogsbay.xml.format.FormatStyle style =
                com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.forFile(cmd.file())
                    .withNewline(original.contains("\r\n")
                        ? com.dogsbay.xml.format.FormatStyle.NewlineStyle.CRLF
                        : com.dogsbay.xml.format.FormatStyle.NewlineStyle.LF);
            String reflowed = com.dogsbay.xml.XMLUtilities.reflowSentences(
                original, systemId, encoding, style);

            if (cmd.output() != null) {
                Files.write(cmd.output(), reflowed.getBytes(cs));
            }

            return new FormatResult(cmd.file(), reflowed, !reflowed.equals(original));
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.PARSE_ERROR,
                "Reflow failed: " + e.getMessage(), e
            );
        }
    }

    // ── Info ────────────────────────────────────────────────────────────

    static DocumentInfo executeInfo(InfoCommand cmd) throws CommandException {
        requireFile(cmd.file());

        try {
            long size = Files.size(cmd.file());
            int lineCount = (int) Files.lines(cmd.file()).count();

            Document doc = newToolingDocumentBuilder().parse(cmd.file().toFile());

            String rootName = doc.getDocumentElement() != null
                ? doc.getDocumentElement().getLocalName() : null;
            String rootNs = doc.getDocumentElement() != null
                ? doc.getDocumentElement().getNamespaceURI() : null;
            String encoding = doc.getXmlEncoding() != null
                ? doc.getXmlEncoding() : "UTF-8";

            // Detect grammar type from processing instructions or schema references
            String grammarType = "none";
            String grammarLocation = null;

            if (doc.getDocumentElement() != null) {
                String xsi = doc.getDocumentElement().getAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation"
                );
                String noNsXsi = doc.getDocumentElement().getAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance", "noNamespaceSchemaLocation"
                );

                if (xsi != null && !xsi.isEmpty()) {
                    grammarType = "XSD";
                    grammarLocation = xsi;
                } else if (noNsXsi != null && !noNsXsi.isEmpty()) {
                    grammarType = "XSD";
                    grammarLocation = noNsXsi;
                }
            }

            if ("none".equals(grammarType) && doc.getDoctype() != null) {
                grammarType = "DTD";
                grammarLocation = doc.getDoctype().getSystemId();
            }

            return new DocumentInfo(
                cmd.file(), encoding, rootName, rootNs,
                grammarType, grammarLocation, size, lineCount
            );
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.PARSE_ERROR,
                "Info failed: " + e.getMessage(), e
            );
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static void requireFile(Path file) throws CommandException {
        if (file == null) {
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT, "File path is required"
            );
        }
        if (!Files.exists(file)) {
            throw new CommandException(
                CommandException.ErrorCode.FILE_NOT_FOUND,
                "File not found: " + file
            );
        }
    }

    private static List<Path> resolveGlob(String pattern) throws CommandException {
        Path path = Path.of(pattern);

        // If it's a direct file path, return it
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }

        // Try as a glob pattern
        try {
            String glob = pattern;
            Path base = Path.of(".");

            // If pattern contains path separator, split into base + glob
            if (pattern.contains("/")) {
                int firstWild = pattern.indexOf('*');
                if (firstWild >= 0) {
                    String basePart = pattern.substring(0, pattern.lastIndexOf('/', firstWild) + 1);
                    if (!basePart.isEmpty()) {
                        base = Path.of(basePart);
                    }
                }
            }

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<Path> result = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base, 20)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p))
                    .forEach(result::add);
            }

            return result;
        } catch (IOException e) {
            throw new CommandException(
                CommandException.ErrorCode.FILE_NOT_FOUND,
                "Failed to resolve pattern: " + pattern, e
            );
        }
    }

    private static ErrorHandler collectingHandler(List<ValidationError> errors) {
        return new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                errors.add(new ValidationError(
                    e.getLineNumber(), e.getColumnNumber(),
                    "warning", e.getMessage(), "parser"
                ));
            }

            @Override
            public void error(SAXParseException e) {
                errors.add(new ValidationError(
                    e.getLineNumber(), e.getColumnNumber(),
                    "error", e.getMessage(), "parser"
                ));
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                errors.add(new ValidationError(
                    e.getLineNumber(), e.getColumnNumber(),
                    "error", e.getMessage(), "parser"
                ));
                throw e;
            }
        };
    }

    private static String nodeTypeString(short nodeType) {
        return switch (nodeType) {
            case Node.ELEMENT_NODE -> "element";
            case Node.ATTRIBUTE_NODE -> "attribute";
            case Node.TEXT_NODE -> "text";
            case Node.COMMENT_NODE -> "comment";
            case Node.PROCESSING_INSTRUCTION_NODE -> "processing-instruction";
            default -> "node";
        };
    }
}
