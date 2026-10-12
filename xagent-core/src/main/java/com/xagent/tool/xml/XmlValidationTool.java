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
package com.xagent.tool.xml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.SVRLHelper;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.operations.FileOperations;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validate an XML file: well-formedness, DTD (from DOCTYPE or an explicit
 * .dtd file), XSD, RelaxNG (.rng/.rnc via Jing), or Schematron (.sch).
 * Supports XML catalogs for entity resolution. Errors are reported as
 * {@code path:line:column: severity: message} lines for fix loops.
 */
public class XmlValidationTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations fileOps;

	/** A single validation finding. */
	record Issue(int line, int column, String severity, String message) {}

	public XmlValidationTool(Path cwd, FileOperations fileOps) {
		this.cwd = cwd;
		this.fileOps = fileOps;
	}

	@Override
	public String name() {
		return "xml_validate";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "Validate an XML file. Checks well-formedness by default. "
			+ "Set dtd=true to validate against the document's DOCTYPE. "
			+ "Pass a schema file to validate against XSD (.xsd), RelaxNG (.rng/.rnc), "
			+ "Schematron (.sch), or a specific DTD (.dtd). "
			+ "Pass a catalog file to resolve public/system identifiers (e.g. DITA DTDs). "
			+ "Errors are reported as path:line:column: severity: message.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		ObjectNode props = schema.putObject("properties");

		ObjectNode path = props.putObject("path");
		path.put("type", "string");
		path.put("description", "Path to the XML file to validate");

		ObjectNode schemaPath = props.putObject("schema");
		schemaPath.put("type", "string");
		schemaPath.put("description",
			"Schema file: .xsd, .rng, .rnc, .sch, or .dtd (optional; type inferred from extension)");

		ObjectNode dtd = props.putObject("dtd");
		dtd.put("type", "boolean");
		dtd.put("description", "Validate against the DTD declared in the document's DOCTYPE (default false)");

		ObjectNode catalog = props.putObject("catalog");
		catalog.put("type", "string");
		catalog.put("description", "XML catalog file for resolving public/system identifiers (optional)");

		schema.putArray("required").add("path");
		return schema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
		Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {

		String pathStr = params.path("path").asText("");
		if (pathStr.isEmpty()) {
			return AgentToolResult.error("Missing required parameter: path");
		}

		Path xmlPath = cwd.resolve(pathStr);
		String schemaStr = params.path("schema").asText("");
		boolean useDoctype = params.path("dtd").asBoolean(false);
		String catalogStr = params.path("catalog").asText("");

		try {
			String xmlContent = fileOps.readFile(xmlPath);
			CatalogResolver catalogResolver = catalogStr.isEmpty()
				? null
				: CatalogManager.catalogResolver(
					CatalogFeatures.builder()
						.with(CatalogFeatures.Feature.RESOLVE, "continue")
						.build(),
					cwd.resolve(catalogStr).toUri());

			var issues = new ArrayList<Issue>();
			String mode;
			if (!schemaStr.isEmpty()) {
				Path schemaPath = cwd.resolve(schemaStr);
				String ext = extension(schemaStr);
				mode = switch (ext) {
					case "xsd" -> validateXsd(xmlContent, xmlPath, schemaPath, catalogResolver, issues);
					case "rng" -> validateRelaxNg(xmlPath, schemaPath, false, issues);
					case "rnc" -> validateRelaxNg(xmlPath, schemaPath, true, issues);
					case "sch" -> validateSchematron(xmlContent, xmlPath, schemaPath, issues);
					case "dtd" -> validateExplicitDtd(xmlContent, xmlPath, schemaPath, issues);
					default -> null;
				};
				if (mode == null) {
					return AgentToolResult.error("Unsupported schema type: ." + ext
						+ " (supported: .xsd, .rng, .rnc, .sch, .dtd)");
				}
			} else if (useDoctype) {
				mode = validateDoctype(xmlContent, xmlPath, catalogResolver, issues);
			} else {
				mode = checkWellFormed(xmlContent, xmlPath, catalogResolver, issues);
			}

			return buildResult(pathStr, mode, schemaStr, issues);
		} catch (Exception e) {
			return AgentToolResult.error("Failed to validate: " + e.getMessage());
		}
	}

	// --- validation modes ---

	private String checkWellFormed(String xmlContent, Path xmlPath,
		CatalogResolver catalogResolver, List<Issue> issues) throws Exception {
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		var builder = factory.newDocumentBuilder();
		if (catalogResolver != null) {
			builder.setEntityResolver(catalogResolver);
		}
		builder.setErrorHandler(collectingHandler(issues));
		try {
			builder.parse(inputSource(xmlContent, xmlPath));
		} catch (org.xml.sax.SAXException e) {
			addIfEmpty(issues, e);
		}
		return "well-formed";
	}

	private String validateDoctype(String xmlContent, Path xmlPath,
		CatalogResolver catalogResolver, List<Issue> issues) throws Exception {
		if (!xmlContent.contains("<!DOCTYPE")) {
			issues.add(new Issue(-1, -1, "error",
				"Document has no DOCTYPE declaration; dtd=true requires one "
					+ "(or pass an explicit .dtd schema file)"));
			return "dtd";
		}
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(true);
		var builder = factory.newDocumentBuilder();
		if (catalogResolver != null) {
			builder.setEntityResolver(catalogResolver);
		}
		builder.setErrorHandler(collectingHandler(issues));
		try {
			builder.parse(inputSource(xmlContent, xmlPath));
		} catch (org.xml.sax.SAXException e) {
			addIfEmpty(issues, e);
		}
		return "dtd";
	}

	private String validateExplicitDtd(String xmlContent, Path xmlPath,
		Path dtdPath, List<Issue> issues) throws Exception {
		if (!xmlContent.contains("<!DOCTYPE")) {
			issues.add(new Issue(-1, -1, "error",
				"Document has no DOCTYPE declaration; DTD validation requires one. "
					+ "Add e.g. <!DOCTYPE rootElement SYSTEM \"" + dtdPath.getFileName() + "\">"));
			return "dtd";
		}
		// ensure the DTD file is readable before redirecting resolution to it
		fileOps.readFile(dtdPath);
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(true);
		var builder = factory.newDocumentBuilder();
		// resolve every external entity to the given DTD, overriding the DOCTYPE target
		builder.setEntityResolver((publicId, systemId) -> {
			var source = new InputSource(dtdPath.toUri().toString());
			return source;
		});
		builder.setErrorHandler(collectingHandler(issues));
		try {
			builder.parse(inputSource(xmlContent, xmlPath));
		} catch (org.xml.sax.SAXException e) {
			addIfEmpty(issues, e);
		}
		return "dtd";
	}

	private String validateXsd(String xmlContent, Path xmlPath, Path schemaPath,
		CatalogResolver catalogResolver, List<Issue> issues) throws Exception {
		String schemaContent = fileOps.readFile(schemaPath);
		var schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		if (catalogResolver != null) {
			schemaFactory.setResourceResolver(catalogResolver);
		}
		var source = new StreamSource(new StringReader(schemaContent));
		source.setSystemId(schemaPath.toUri().toString());
		var xsdSchema = schemaFactory.newSchema(source);
		var validator = xsdSchema.newValidator();
		if (catalogResolver != null) {
			validator.setResourceResolver(catalogResolver);
		}
		var collected = new ArrayList<Issue>();
		validator.setErrorHandler(collectingHandler(collected));
		try {
			var xmlSource = new StreamSource(new StringReader(xmlContent));
			xmlSource.setSystemId(xmlPath.toUri().toString());
			validator.validate(xmlSource);
		} catch (org.xml.sax.SAXException e) {
			addIfEmpty(collected, e);
		}
		issues.addAll(collected);
		return "xsd";
	}

	private String validateRelaxNg(Path xmlPath, Path schemaPath,
		boolean compact, List<Issue> issues) throws Exception {
		// verify readability through the ops layer first
		fileOps.readFile(schemaPath);
		var properties = new PropertyMapBuilder();
		properties.put(ValidateProperty.ERROR_HANDLER, collectingHandler(issues));
		var driver = compact
			? new ValidationDriver(properties.toPropertyMap(), CompactSchemaReader.getInstance())
			: new ValidationDriver(properties.toPropertyMap());
		try {
			if (!driver.loadSchema(new InputSource(schemaPath.toUri().toString()))) {
				issues.add(new Issue(-1, -1, "error", "Failed to load RelaxNG schema: " + schemaPath));
				return compact ? "relaxng-compact" : "relaxng";
			}
			driver.validate(new InputSource(xmlPath.toUri().toString()));
		} catch (org.xml.sax.SAXException e) {
			addIfEmpty(issues, e);
		}
		return compact ? "relaxng-compact" : "relaxng";
	}

	private String validateSchematron(String xmlContent, Path xmlPath,
		Path schemaPath, List<Issue> issues) throws Exception {
		fileOps.readFile(schemaPath);
		var schematron = SchematronResourcePure.fromFile(schemaPath.toFile());
		if (!schematron.isValidSchematron()) {
			issues.add(new Issue(-1, -1, "error", "Invalid Schematron schema: " + schemaPath));
			return "schematron";
		}
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		var doc = factory.newDocumentBuilder().parse(inputSource(xmlContent, xmlPath));
		var output = schematron.applySchematronValidationToSVRL(doc, xmlPath.toUri().toString());
		for (var failed : SVRLHelper.getAllFailedAssertions(output)) {
			String text = failed.getText() != null ? failed.getText().strip() : "assertion failed";
			String location = failed.getLocation();
			issues.add(new Issue(-1, -1, "error",
				text + (location != null && !location.isEmpty() ? " (at " + location + ")" : "")));
		}
		return "schematron";
	}

	// --- helpers ---

	private static InputSource inputSource(String content, Path file) {
		var source = new InputSource(new StringReader(content));
		// relative DTD/entity references resolve from the file's location
		source.setSystemId(file.toUri().toString());
		return source;
	}

	private static ErrorHandler collectingHandler(List<Issue> issues) {
		return new ErrorHandler() {
			@Override
			public void warning(SAXParseException e) {
				issues.add(toIssue(e, "warning"));
			}

			@Override
			public void error(SAXParseException e) {
				issues.add(toIssue(e, "error"));
			}

			@Override
			public void fatalError(SAXParseException e) {
				issues.add(toIssue(e, "error"));
			}
		};
	}

	private static Issue toIssue(SAXParseException e, String severity) {
		return new Issue(e.getLineNumber(), e.getColumnNumber(), severity, e.getMessage());
	}

	private static void addIfEmpty(List<Issue> issues, org.xml.sax.SAXException e) {
		if (issues.isEmpty()) {
			issues.add(new Issue(-1, -1, "error", e.getMessage()));
		}
	}

	private static String extension(String path) {
		int dot = path.lastIndexOf('.');
		return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
	}

	private AgentToolResult buildResult(String pathStr, String mode, String schemaStr, List<Issue> issues) {
		boolean hasErrors = issues.stream().anyMatch(i -> "error".equals(i.severity()));

		ObjectNode details = MAPPER.createObjectNode();
		details.put("mode", mode);
		ArrayNode issueArray = details.putArray("issues");
		for (var issue : issues) {
			ObjectNode node = issueArray.addObject();
			if (issue.line() > 0) node.put("line", issue.line());
			if (issue.column() > 0) node.put("column", issue.column());
			node.put("severity", issue.severity());
			node.put("message", issue.message());
		}

		if (!hasErrors) {
			String what = schemaStr.isEmpty()
				? ("dtd".equals(mode) ? "valid against its DOCTYPE DTD" : "well-formed")
				: "valid against " + schemaStr + " (" + mode + ")";
			String suffix = issues.isEmpty() ? "" : "\n" + formatIssues(pathStr, issues);
			return AgentToolResult.success("XML is " + what + ": " + pathStr + suffix, details);
		}

		long errorCount = issues.stream().filter(i -> "error".equals(i.severity())).count();
		return AgentToolResult.error(
			errorCount + " validation error" + (errorCount == 1 ? "" : "s")
				+ " in " + pathStr + " (mode: " + mode + ")."
				+ " Validation errors:\n" + formatIssues(pathStr, issues),
			details);
	}

	private static String formatIssues(String pathStr, List<Issue> issues) {
		var sb = new StringBuilder();
		for (var issue : issues) {
			sb.append(pathStr);
			if (issue.line() > 0) {
				sb.append(':').append(issue.line());
				if (issue.column() > 0) sb.append(':').append(issue.column());
			}
			sb.append(": ").append(issue.severity()).append(": ").append(issue.message()).append('\n');
		}
		return sb.toString().strip();
	}
}
