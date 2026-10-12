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
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Execute XPath queries against an XML file.
 */
public class XPathTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_RESULTS = 200;
	private final Path cwd;
	private final FileOperations fileOps;

	public XPathTool(Path cwd, FileOperations fileOps) {
		this.cwd = cwd;
		this.fileOps = fileOps;
	}

	@Override
	public String name() {
		return "xpath";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "Execute an XPath expression against an XML file. "
			+ "Returns matching nodes as text content.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		ObjectNode props = schema.putObject("properties");

		ObjectNode path = props.putObject("path");
		path.put("type", "string");
		path.put("description", "Path to the XML file");

		ObjectNode expression = props.putObject("expression");
		expression.put("type", "string");
		expression.put("description", "XPath expression to evaluate");

		schema.putArray("required").add("path").add("expression");
		return schema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
		Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {

		String pathStr = params.path("path").asText("");
		String expression = params.path("expression").asText("");

		if (pathStr.isEmpty()) return AgentToolResult.error("Missing required parameter: path");
		if (expression.isEmpty()) return AgentToolResult.error("Missing required parameter: expression");

		try {
			Path xmlPath = cwd.resolve(pathStr);
			String content = fileOps.readFile(xmlPath);

			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			var builder = factory.newDocumentBuilder();
			var doc = builder.parse(new InputSource(new StringReader(content)));

			var xpath = XPathFactory.newInstance().newXPath();
			var result = xpath.evaluate(expression, doc, XPathConstants.NODESET);

			if (result instanceof NodeList nodeList) {
				var sb = new StringBuilder();
				int count = Math.min(nodeList.getLength(), MAX_RESULTS);
				sb.append("Found ").append(nodeList.getLength()).append(" match(es)");
				if (nodeList.getLength() > MAX_RESULTS) {
					sb.append(" (showing first ").append(MAX_RESULTS).append(")");
				}
				sb.append(":\n\n");

				for (int i = 0; i < count; i++) {
					var node = nodeList.item(i);
					sb.append("[").append(i + 1).append("] ");
					String textContent = node.getTextContent();
					if (textContent != null && !textContent.isBlank()) {
						sb.append(textContent.strip());
					} else {
						sb.append(node.getNodeName());
						if (node.getNodeValue() != null) {
							sb.append("=").append(node.getNodeValue());
						}
					}
					sb.append("\n");
				}
				return AgentToolResult.success(sb.toString().strip());
			} else {
				// String result
				String strResult = xpath.evaluate(expression, doc);
				return AgentToolResult.success("Result: " + strResult);
			}
		} catch (javax.xml.xpath.XPathExpressionException e) {
			return AgentToolResult.error("Invalid XPath expression: " + e.getMessage());
		} catch (Exception e) {
			return AgentToolResult.error("XPath failed: " + e.getMessage());
		}
	}
}
