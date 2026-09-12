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

package com.dogsbay.dogsbayaieditor.services;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLError;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SchemaManager's pure (non-UI) operations: parse, validate, loadSchema.
 *
 * These tests exercise the headless API that does not depend on DogsBayAIEditor
 * for the actual document processing -- they use DogsBayDocument directly
 * and the SchemaManager with a null editor for the pure operations.
 */
class SchemaManagerTest {

	/**
	 * Helper: create a SchemaManager that does not require a live editor.
	 * The pure operation methods (parse, validate, loadSchema) do not
	 * reference the editor field, so passing null is safe for them.
	 */
	private SchemaManager createHeadlessManager() {
		return new SchemaManager(null);
	}

	@Test
	void parse_wellFormedDocument_returnsNoErrors() throws Exception {
		File tempFile = File.createTempFile("test-wellformed", ".xml");
		tempFile.deleteOnExit();
		try (FileWriter w = new FileWriter(tempFile)) {
			w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><child>text</child></root>");
		}

		DogsBayDocument doc = new DogsBayDocument(tempFile.toURI().toURL());
		doc.load();

		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.parse(doc);

		assertTrue(errors.isEmpty(), "Well-formed document should have no parse errors");
	}

	@Test
	void parse_malformedDocument_returnsErrors() throws Exception {
		File tempFile = File.createTempFile("test-malformed", ".xml");
		tempFile.deleteOnExit();
		try (FileWriter w = new FileWriter(tempFile)) {
			w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><unclosed>");
		}

		DogsBayDocument doc = new DogsBayDocument(tempFile.toURI().toURL());
		try {
			doc.load();
		} catch (Exception e) {
			// Expected -- malformed XML
		}

		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.parse(doc);

		assertFalse(errors.isEmpty(), "Malformed document should have parse errors");
	}

	@Test
	void parse_nullDocument_returnsEmptyList() {
		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.parse(null);

		assertNotNull(errors);
		assertTrue(errors.isEmpty());
	}

	@Test
	void validate_wellFormedDocumentWithoutSchema_returnsResults() throws Exception {
		File tempFile = File.createTempFile("test-valid", ".xml");
		tempFile.deleteOnExit();
		try (FileWriter w = new FileWriter(tempFile)) {
			w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><child>text</child></root>");
		}

		DogsBayDocument doc = new DogsBayDocument(tempFile.toURI().toURL());
		doc.load();

		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.validate(doc);

		// validate() should return a list (possibly with errors if no schema) but not throw
		assertNotNull(errors, "validate should return a non-null list");
	}

	@Test
	void validate_nullDocument_returnsEmptyList() {
		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.validate(null);

		assertNotNull(errors);
		assertTrue(errors.isEmpty());
	}

	@Test
	void validate_documentWithInternalDTD_returnsErrors() throws Exception {
		File tempFile = File.createTempFile("test-dtd-invalid", ".xml");
		tempFile.deleteOnExit();
		try (FileWriter w = new FileWriter(tempFile)) {
			w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<!DOCTYPE root [\n"
					+ "  <!ELEMENT root (child)>\n"
					+ "  <!ELEMENT child (#PCDATA)>\n"
					+ "]>\n"
					+ "<root><wrongelement>text</wrongelement></root>");
		}

		DogsBayDocument doc = new DogsBayDocument(tempFile.toURI().toURL());
		doc.load();

		SchemaManager manager = createHeadlessManager();
		List<XMLError> errors = manager.validate(doc);

		assertFalse(errors.isEmpty(), "Document violating its DTD should have validation errors");
	}

}
