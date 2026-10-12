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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xagent.tool.operations.DefaultFileOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XmlToolsTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;

	private DefaultFileOperations fileOps;

	@BeforeEach
	void setUp() throws IOException {
		fileOps = new DefaultFileOperations();
		// Copy fixture files to temp dir
		copyFixture("sample.xml");
		copyFixture("sample.xsd");
		copyFixture("transform.xsl");
	}

	private void copyFixture(String name) throws IOException {
		var input = getClass().getResourceAsStream("/fixtures/xml/" + name);
		if (input != null) {
			Files.write(tempDir.resolve(name), input.readAllBytes());
		}
	}

	// --- XmlValidationTool ---

	@Test
	void validateWellFormedXml() {
		var tool = new XmlValidationTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("well-formed");
	}

	@Test
	void validateAgainstXsd() {
		var tool = new XmlValidationTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("schema", "sample.xsd");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid");
	}

	@Test
	void validateMalformedXml() throws IOException {
		Files.writeString(tempDir.resolve("bad.xml"), "<root><unclosed>");
		var tool = new XmlValidationTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "bad.xml");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isTrue();
	}

	@Test
	void validateAgainstXsdFails() throws IOException {
		Files.writeString(tempDir.resolve("invalid.xml"),
			"<?xml version=\"1.0\"?><books><book id=\"1\"><title>T</title><extra>bad</extra></book></books>");
		var tool = new XmlValidationTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "invalid.xml");
		params.put("schema", "sample.xsd");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Validation errors");
	}

	// --- XPathTool ---

	@Test
	void xpathFindsElements() {
		var tool = new XPathTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("expression", "//book/title");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("3 match");
		assertThat(result.content()).contains("DITA Best Practices");
		assertThat(result.content()).contains("Every Page is Page One");
	}

	@Test
	void xpathWithPredicate() {
		var tool = new XPathTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("expression", "//book[@id='2']/title");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("Every Page is Page One");
	}

	@Test
	void xpathInvalidExpression() {
		var tool = new XPathTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("expression", "///[invalid");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Invalid XPath");
	}

	// --- XmlFormatTool ---

	@Test
	void formatXmlReturnsFormatted() throws IOException {
		Files.writeString(tempDir.resolve("ugly.xml"),
			"<?xml version=\"1.0\"?><root><child>text</child><child>more</child></root>");
		var tool = new XmlFormatTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "ugly.xml");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("<root>");
		assertThat(result.content()).contains("  <child>"); // indented
	}

	@Test
	void formatXmlWritesBack() throws IOException {
		Files.writeString(tempDir.resolve("ugly.xml"),
			"<root><child>text</child></root>");
		var tool = new XmlFormatTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "ugly.xml");
		params.put("write", true);

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("Formatted and saved");
		String content = Files.readString(tempDir.resolve("ugly.xml"));
		assertThat(content).contains("\n"); // has newlines now
	}

	// --- XsltTool ---

	@Test
	void xsltTransformsXml() {
		var tool = new XsltTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("xslt", "transform.xsl");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("DITA Best Practices (2011)");
		assertThat(result.content()).contains("Every Page is Page One (2013)");
	}

	@Test
	void xsltWritesToOutputFile() {
		var tool = new XsltTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("xslt", "transform.xsl");
		params.put("output", "result.txt");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("written to");
	}

	@Test
	void xsltInvalidStylesheet() throws IOException {
		Files.writeString(tempDir.resolve("bad.xsl"), "<not-a-stylesheet/>");
		var tool = new XsltTool(tempDir, fileOps);
		var params = MAPPER.createObjectNode();
		params.put("path", "sample.xml");
		params.put("xslt", "bad.xsl");

		var result = tool.execute("id", params, () -> false, null);

		assertThat(result.isError()).isTrue();
	}
}
