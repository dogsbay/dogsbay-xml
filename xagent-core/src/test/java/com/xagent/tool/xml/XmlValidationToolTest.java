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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.operations.DefaultFileOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation modes beyond XSD: DTD, RelaxNG, Schematron, catalogs.
 * Basic well-formedness and XSD cases live in XmlToolsTest.
 */
class XmlValidationToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;

	private XmlValidationTool tool;

	@BeforeEach
	void setUp() {
		tool = new XmlValidationTool(tempDir, new DefaultFileOperations());
	}

	private ObjectNode params(String path) {
		var params = MAPPER.createObjectNode();
		params.put("path", path);
		return params;
	}

	private void write(String name, String content) throws IOException {
		Files.writeString(tempDir.resolve(name), content);
	}

	private void writeNoteDtd() throws IOException {
		write("note.dtd", """
			<!ELEMENT note (to)>
			<!ELEMENT to (#PCDATA)>
			""");
	}

	// --- DOCTYPE DTD validation (dtd=true) ---

	@Test
	void doctypeDtdValid() throws IOException {
		writeNoteDtd();
		write("note.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note SYSTEM "note.dtd">
			<note><to>Alice</to></note>
			""");
		var p = params("note.xml");
		p.put("dtd", true);

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid against its DOCTYPE DTD");
	}

	@Test
	void doctypeDtdInvalid() throws IOException {
		writeNoteDtd();
		write("bad-note.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note SYSTEM "note.dtd">
			<note><from>Bob</from></note>
			""");
		var p = params("bad-note.xml");
		p.put("dtd", true);

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Validation errors");
		// structured path:line: error: message format for fix loops
		assertThat(result.content()).containsPattern("bad-note\\.xml:\\d+:\\d+: error:");
	}

	@Test
	void doctypeDtdWithoutDoctypeReportsActionableError() throws IOException {
		write("plain.xml", "<note><to>x</to></note>");
		var p = params("plain.xml");
		p.put("dtd", true);

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("no DOCTYPE");
	}

	// --- explicit .dtd schema ---

	@Test
	void explicitDtdValid() throws IOException {
		writeNoteDtd();
		write("note.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note SYSTEM "ignored.dtd">
			<note><to>Alice</to></note>
			""");
		var p = params("note.xml");
		p.put("schema", "note.dtd");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid against note.dtd");
	}

	@Test
	void explicitDtdInvalid() throws IOException {
		writeNoteDtd();
		write("bad.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note SYSTEM "ignored.dtd">
			<note><other/></note>
			""");
		var p = params("bad.xml");
		p.put("schema", "note.dtd");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
	}

	// --- RelaxNG ---

	@Test
	void relaxNgXmlSyntaxValid() throws IOException {
		write("note.rng", """
			<element name="note" xmlns="http://relaxng.org/ns/structure/1.0">
			  <element name="to"><text/></element>
			</element>
			""");
		write("note.xml", "<note><to>Alice</to></note>");
		var p = params("note.xml");
		p.put("schema", "note.rng");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid against note.rng");
	}

	@Test
	void relaxNgXmlSyntaxInvalid() throws IOException {
		write("note.rng", """
			<element name="note" xmlns="http://relaxng.org/ns/structure/1.0">
			  <element name="to"><text/></element>
			</element>
			""");
		write("bad.xml", "<note><unexpected/></note>");
		var p = params("bad.xml");
		p.put("schema", "note.rng");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Validation errors");
	}

	@Test
	void relaxNgCompactSyntax() throws IOException {
		write("note.rnc", "element note { element to { text } }");
		write("note.xml", "<note><to>Alice</to></note>");
		write("bad.xml", "<note><nope/></note>");

		var good = params("note.xml");
		good.put("schema", "note.rnc");
		assertThat(tool.execute("id", good, () -> false, null).isError()).isFalse();

		var bad = params("bad.xml");
		bad.put("schema", "note.rnc");
		assertThat(tool.execute("id", bad, () -> false, null).isError()).isTrue();
	}

	// --- Schematron ---

	@Test
	void schematronValid() throws IOException {
		write("rules.sch", """
			<schema xmlns="http://purl.oclc.org/dsdl/schematron">
			  <pattern>
			    <rule context="price">
			      <assert test="number(.) &gt; 0">price must be positive</assert>
			    </rule>
			  </pattern>
			</schema>
			""");
		write("order.xml", "<order><price>5</price></order>");
		var p = params("order.xml");
		p.put("schema", "rules.sch");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid against rules.sch");
	}

	@Test
	void schematronFailedAssertReportsRuleText() throws IOException {
		write("rules.sch", """
			<schema xmlns="http://purl.oclc.org/dsdl/schematron">
			  <pattern>
			    <rule context="price">
			      <assert test="number(.) &gt; 0">price must be positive</assert>
			    </rule>
			  </pattern>
			</schema>
			""");
		write("bad-order.xml", "<order><price>-1</price></order>");
		var p = params("bad-order.xml");
		p.put("schema", "rules.sch");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("price must be positive");
	}

	// --- catalogs ---

	@Test
	void catalogResolvesPublicIdToLocalDtd() throws IOException {
		writeNoteDtd();
		write("catalog.xml", """
			<catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
			  <public publicId="-//TEST//DTD note//EN" uri="note.dtd"/>
			</catalog>
			""");
		write("note.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note PUBLIC "-//TEST//DTD note//EN" "nonexistent/note.dtd">
			<note><to>Alice</to></note>
			""");
		var p = params("note.xml");
		p.put("dtd", true);
		p.put("catalog", "catalog.xml");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("valid against its DOCTYPE DTD");
	}

	// --- misc ---

	@Test
	void unsupportedSchemaTypeIsRejected() throws IOException {
		write("note.xml", "<note/>");
		write("schema.foo", "whatever");
		var p = params("note.xml");
		p.put("schema", "schema.foo");

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Unsupported schema type");
	}

	@Test
	void detailsCarryStructuredIssues() throws IOException {
		writeNoteDtd();
		write("bad.xml", """
			<?xml version="1.0"?>
			<!DOCTYPE note SYSTEM "note.dtd">
			<note><from>Bob</from></note>
			""");
		var p = params("bad.xml");
		p.put("dtd", true);

		var result = tool.execute("id", p, () -> false, null);

		assertThat(result.details()).isNotNull();
		assertThat(result.details().get("mode").asText()).isEqualTo("dtd");
		assertThat(result.details().get("issues").size()).isGreaterThan(0);
		var issue = result.details().get("issues").get(0);
		assertThat(issue.get("severity").asText()).isEqualTo("error");
		assertThat(issue.has("line")).isTrue();
	}
}
