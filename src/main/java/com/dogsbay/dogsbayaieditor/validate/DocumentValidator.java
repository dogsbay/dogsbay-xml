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

package com.dogsbay.dogsbayaieditor.validate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationResult;

/**
 * The shared, catalog-aware single-file validation core: well-formedness, DTD,
 * XSD, and RelaxNG, with OASIS catalog support for entity resolution.
 *
 * <p>Extracted from {@code HeadlessExecutor} so single-file commands, the
 * project-wide batch layer, and the code-mode CLI all validate DITA
 * <em>identically</em> — in particular, a {@code .dita}/{@code .ditamap} with no
 * explicit schema/catalog defaults to the editor's bundled DITA catalog, so its
 * PUBLIC DOCTYPE resolves the same way it does when the editor opens it (rather
 * than failing to find {@code task.dtd} next to the file).
 */
public final class DocumentValidator {

    private DocumentValidator() {}

    /** Thrown for genuine setup/configuration failures (not validation errors). */
    public static final class ValidationSetupException extends RuntimeException {
        public ValidationSetupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Validate one document. Validation problems are returned as
     * {@link ValidationError}s inside the result (never thrown); only genuine
     * setup failures throw {@link ValidationSetupException}.
     *
     * @param file     the document to validate
     * @param schema   schema (.dtd/.xsd/.rng/.rnc), or null to use the document's
     *                 own DOCTYPE (with catalogs) / well-formedness
     * @param catalogs OASIS catalogs for entity resolution (may be null/empty)
     */
    public static ValidationResult validate(Path file, Path schema, List<Path> catalogs) {
        List<Path> cats = catalogs == null ? List.of() : catalogs;

        // DITA topics/maps declare a PUBLIC DOCTYPE that only resolves through a
        // catalog. With no explicit schema/catalog, default to the bundled DITA
        // catalog so validation matches how the editor resolves the DOCTYPE.
        if (schema == null && cats.isEmpty() && isDitaFile(file)) {
            Path ditaCatalog = builtinDitaCatalog();
            if (ditaCatalog != null) {
                cats = List.of(ditaCatalog);
            }
        }

        List<ValidationError> errors = new ArrayList<>();
        try {
            if (schema != null) {
                String sp = schema.toString().toLowerCase();
                if (sp.endsWith(".dtd")) {
                    validateWithDtd(file, schema, cats, errors);
                } else if (sp.endsWith(".rng") || sp.endsWith(".rnc")) {
                    validateWithRelaxNG(file, schema, errors);
                } else {
                    validateWithXsd(file, schema, cats, errors);
                }
            } else if (!cats.isEmpty()) {
                validateWithDoctype(file, cats, errors);
            } else {
                // A DITA file here means no catalog was available (the default
                // above found none). Don't try to fetch its PUBLIC DTD — that
                // would fail with "task.dtd not found" on every file in a headless
                // run; disable external-DTD loading and check well-formedness only.
                validateWellFormedness(file, cats, errors, isDitaFile(file));
            }
        } catch (SAXParseException e) {
            errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(),
                    "error", e.getMessage(), "parser"));
        } catch (SAXException e) {
            errors.add(new ValidationError(-1, -1, "error", e.getMessage(), "parser"));
        } catch (ValidationSetupException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationSetupException("Validation failed: " + e.getMessage(), e);
        }

        return new ValidationResult(file, errors.isEmpty(), errors);
    }

    /** A DITA topic/map whose DOCTYPE only resolves through the DITA catalog. */
    public static boolean isDitaFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".dita") || name.endsWith(".ditamap");
    }

    /** The editor's bundled DITA DTD catalog (~/.dogsbay/builtin/dita), or null if absent. */
    public static Path builtinDitaCatalog() {
        // Self-refresh the bundle for headless use (CLI/MCP/batch): if the editor
        // hasn't launched since a bundle version bump, the extracted copy can be
        // stale (e.g. missing a newly added subjectScheme.dtd). Idempotent + cheap.
        com.dogsbay.dogsbayaieditor.plugin.dita.DitaBuiltInAssets.ensureExtracted();
        Path catalog = Path.of(System.getProperty("user.home"),
                ".dogsbay", "builtin", "dita", "dtd", "catalog.xml");
        return java.nio.file.Files.exists(catalog) ? catalog : null;
    }

    // ── validation modes ────────────────────────────────────────────────

    private static void validateWithDoctype(Path file, List<Path> catalogs,
            List<ValidationError> errors) throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(true);
        spf.setNamespaceAware(true);
        SAXParser parser = spf.newSAXParser();
        org.xml.sax.XMLReader reader = parser.getXMLReader();
        // Block external general entities (the classic file-read XXE) while keeping
        // DTD/parameter-entity resolution, which DTD validation legitimately needs and
        // which the catalog resolver below constrains to known grammars.
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
        reader.setErrorHandler(collectingHandler(errors));
        reader.setEntityResolver(createCatalogResolver(catalogs));
        reader.parse(new InputSource(file.toUri().toString()));
    }

    private static void validateWellFormedness(Path file, List<Path> catalogs,
            List<ValidationError> errors, boolean disableExternalDtd) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Block external general entities (the file-read XXE vector). Parameter
        // entities stay enabled so that when an external DTD is loaded (below) its
        // module includes still resolve for otherwise-well-formed documents.
        trySetFactoryFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        if (disableExternalDtd) {
            factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            trySetFactoryFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(collectingHandler(errors));
        if (!catalogs.isEmpty()) {
            builder.setEntityResolver(createCatalogResolver(catalogs));
        }
        builder.parse(file.toFile());
    }

    private static void validateWithXsd(Path file, Path schema, List<Path> catalogs,
            List<ValidationError> errors) throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        if (!catalogs.isEmpty()) {
            sf.setResourceResolver(new CatalogLSResourceResolver(catalogs));
        }
        Schema xsd = sf.newSchema(schema.toFile());
        Validator validator = xsd.newValidator();
        validator.setErrorHandler(collectingHandler(errors));
        if (!catalogs.isEmpty()) {
            validator.setResourceResolver(new CatalogLSResourceResolver(catalogs));
        }
        validator.validate(new StreamSource(file.toFile()));
    }

    private static void validateWithDtd(Path file, Path schema, List<Path> catalogs,
            List<ValidationError> errors) throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(true);
        spf.setNamespaceAware(true);
        SAXParser parser = spf.newSAXParser();
        org.xml.sax.XMLReader reader = parser.getXMLReader();
        // Block external general entities (XXE) while keeping the explicit DTD resolution below.
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
        reader.setErrorHandler(collectingHandler(errors));
        if (!catalogs.isEmpty()) {
            reader.setEntityResolver(createCatalogResolver(catalogs));
        } else {
            Path dtdPath = schema.toAbsolutePath();
            reader.setEntityResolver((publicId, systemId) ->
                new InputSource(dtdPath.toUri().toString()));
        }
        reader.parse(new InputSource(file.toUri().toString()));
    }

    private static void validateWithRelaxNG(Path file, Path schema,
            List<ValidationError> errors) throws Exception {
        try {
            SchemaFactory sf = SchemaFactory.newInstance("http://relaxng.org/ns/structure/1.0");
            Schema rng = sf.newSchema(schema.toFile());
            Validator validator = rng.newValidator();
            validator.setErrorHandler(collectingHandler(errors));
            validator.validate(new StreamSource(file.toFile()));
        } catch (IllegalArgumentException e) {
            throw new ValidationSetupException(
                "RelaxNG validation not available. Ensure Jing is on the classpath.", e);
        }
    }

    // ── XXE hardening helpers ────────────────────────────────────────────

    /** Best-effort feature set on a reader — not every parser supports every feature. */
    private static void trySetFeature(org.xml.sax.XMLReader reader, String feature, boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (Exception ignored) {
            // feature unsupported — the entity resolver / other features still guard
        }
    }

    /** Best-effort feature set on a factory. */
    private static void trySetFactoryFeature(DocumentBuilderFactory factory, String feature,
            boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // feature unsupported by this parser implementation
        }
    }

    // ── catalog + error plumbing ─────────────────────────────────────────

    /**
     * A resolver over the bundled DITA catalog, or {@code null} when the bundle
     * is absent. Headless commands that parse rather than validate (such as
     * {@code info}) need this too: a DITA DOCTYPE names {@code map.dtd}
     * relative to the document, which exists only in the catalog, so a parse
     * without it fails on a perfectly good map.
     */
    public static CatalogResolver builtinDitaResolver() {
        Path catalog = builtinDitaCatalog();
        return catalog == null ? null : createCatalogResolver(List.of(catalog));
    }

    private static CatalogResolver createCatalogResolver(List<Path> catalogs) {
        return new CatalogResolver(catalogManager(catalogs));
    }

    private static CatalogManager catalogManager(List<Path> catalogs) {
        CatalogManager manager = new CatalogManager();
        manager.setIgnoreMissingProperties(true);
        manager.setPreferPublic(true);
        manager.setUseStaticCatalog(false);
        manager.setVerbosity(0);
        manager.setCatalogFiles(catalogs.stream()
            .map(p -> p.toAbsolutePath().toString())
            .reduce((a, b) -> a + ";" + b)
            .orElse(""));
        return manager;
    }

    private static ErrorHandler collectingHandler(List<ValidationError> errors) {
        return new ErrorHandler() {
            @Override public void warning(SAXParseException e) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(),
                        "warning", e.getMessage(), "parser"));
            }
            @Override public void error(SAXParseException e) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(),
                        "error", e.getMessage(), "parser"));
            }
            @Override public void fatalError(SAXParseException e) throws SAXException {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(),
                        "error", e.getMessage(), "parser"));
                throw e;
            }
        };
    }

    /** LSResourceResolver that delegates to a CatalogResolver for XSD validation. */
    private static final class CatalogLSResourceResolver implements LSResourceResolver {
        private final CatalogResolver resolver;

        CatalogLSResourceResolver(List<Path> catalogs) {
            this.resolver = new CatalogResolver(catalogManager(catalogs));
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI,
                String publicId, String systemId, String baseURI) {
            try {
                InputSource source = resolver.resolveEntity(publicId, systemId);
                if (source != null) {
                    return new LSInputImpl(source);
                }
            } catch (Exception e) {
                // fall through to default resolution
            }
            return null;
        }
    }

    /** Minimal LSInput wrapping an InputSource. */
    private static final class LSInputImpl implements LSInput {
        private final InputSource source;

        LSInputImpl(InputSource source) { this.source = source; }

        public java.io.Reader getCharacterStream() { return source.getCharacterStream(); }
        public void setCharacterStream(java.io.Reader r) {}
        public java.io.InputStream getByteStream() { return source.getByteStream(); }
        public void setByteStream(java.io.InputStream is) {}
        public String getStringData() { return null; }
        public void setStringData(String s) {}
        public String getSystemId() { return source.getSystemId(); }
        public void setSystemId(String s) {}
        public String getPublicId() { return source.getPublicId(); }
        public void setPublicId(String s) {}
        public String getBaseURI() { return null; }
        public void setBaseURI(String s) {}
        public String getEncoding() { return source.getEncoding(); }
        public void setEncoding(String s) {}
        public boolean getCertifiedText() { return false; }
        public void setCertifiedText(boolean b) {}
    }
}
