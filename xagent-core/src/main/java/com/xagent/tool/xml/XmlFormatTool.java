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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.operations.FileOperations;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.xml.sax.InputSource;

/**
 * Pretty-print an XML file with consistent indentation.
 */
public class XmlFormatTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations fileOps;

	public XmlFormatTool(Path cwd, FileOperations fileOps) {
		this.cwd = cwd;
		this.fileOps = fileOps;
	}

	@Override
	public String name() {
		return "xml_format";
	}

	@Override
	public String description() {
		return "Pretty-print an XML file with consistent indentation. "
			+ "Can write the formatted output back to the file or return it.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		ObjectNode props = schema.putObject("properties");

		ObjectNode path = props.putObject("path");
		path.put("type", "string");
		path.put("description", "Path to the XML file to format");

		ObjectNode indent = props.putObject("indent");
		indent.put("type", "integer");
		indent.put("description", "Indentation spaces (default: 2)");

		ObjectNode writeBack = props.putObject("write");
		writeBack.put("type", "boolean");
		writeBack.put("description", "Write formatted output back to file (default: false)");

		schema.putArray("required").add("path");
		return schema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
		Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {

		String pathStr = params.path("path").asText("");
		if (pathStr.isEmpty()) return AgentToolResult.error("Missing required parameter: path");

		int indent = params.has("indent") ? params.path("indent").asInt(2) : 2;
		boolean write = params.has("write") && params.path("write").asBoolean(false);

		try {
			Path xmlPath = cwd.resolve(pathStr);
			String content = fileOps.readFile(xmlPath);

			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			var builder = factory.newDocumentBuilder();
			var doc = builder.parse(new InputSource(new StringReader(content)));

			// Remove whitespace-only text nodes for clean formatting
			doc.normalize();

			var transformerFactory = TransformerFactory.newInstance();
			var transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
				content.startsWith("<?xml") ? "no" : "yes");

			var writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			String formatted = writer.toString();

			if (write) {
				fileOps.writeFile(xmlPath, formatted);
				return AgentToolResult.success("Formatted and saved: " + pathStr);
			} else {
				return AgentToolResult.success(formatted);
			}
		} catch (Exception e) {
			return AgentToolResult.error("Format failed: " + e.getMessage());
		}
	}
}
