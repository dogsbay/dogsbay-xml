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

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Apply an XSLT transformation to an XML file.
 */
public class XsltTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations fileOps;

	public XsltTool(Path cwd, FileOperations fileOps) {
		this.cwd = cwd;
		this.fileOps = fileOps;
	}

	@Override
	public String name() {
		return "xslt";
	}

	@Override
	public String description() {
		return "Apply an XSLT stylesheet to an XML file and return the result. "
			+ "Optionally write the output to a file.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		ObjectNode props = schema.putObject("properties");

		ObjectNode path = props.putObject("path");
		path.put("type", "string");
		path.put("description", "Path to the XML input file");

		ObjectNode xslt = props.putObject("xslt");
		xslt.put("type", "string");
		xslt.put("description", "Path to the XSLT stylesheet");

		ObjectNode output = props.putObject("output");
		output.put("type", "string");
		output.put("description", "Path to write output (optional, returns result if omitted)");

		schema.putArray("required").add("path").add("xslt");
		return schema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
		Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {

		String pathStr = params.path("path").asText("");
		String xsltStr = params.path("xslt").asText("");
		String outputStr = params.has("output") ? params.path("output").asText("") : "";

		if (pathStr.isEmpty()) return AgentToolResult.error("Missing required parameter: path");
		if (xsltStr.isEmpty()) return AgentToolResult.error("Missing required parameter: xslt");

		try {
			Path xmlPath = cwd.resolve(pathStr);
			Path xsltPath = cwd.resolve(xsltStr);

			String xmlContent = fileOps.readFile(xmlPath);
			String xsltContent = fileOps.readFile(xsltPath);

			var factory = TransformerFactory.newInstance();
			var transformer = factory.newTransformer(new StreamSource(new StringReader(xsltContent)));

			var writer = new StringWriter();
			transformer.transform(
				new StreamSource(new StringReader(xmlContent)),
				new StreamResult(writer)
			);
			String result = writer.toString();

			if (!outputStr.isEmpty()) {
				Path outputPath = cwd.resolve(outputStr);
				fileOps.writeFile(outputPath, result);
				return AgentToolResult.success("XSLT output written to: " + outputStr);
			}

			// Truncate if very large
			if (result.length() > 10000) {
				return AgentToolResult.success(result.substring(0, 10000)
					+ "\n... (truncated, " + result.length() + " chars total)");
			}
			return AgentToolResult.success(result);
		} catch (Exception e) {
			return AgentToolResult.error("XSLT transformation failed: " + e.getMessage());
		}
	}
}
