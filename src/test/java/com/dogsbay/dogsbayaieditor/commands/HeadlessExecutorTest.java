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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.*;

class HeadlessExecutorTest {

    private static final HeadlessExecutor executor = new HeadlessExecutor();
    private static Path testResources;

    @BeforeAll
    static void findResources() {
        testResources = Path.of("src/test/resources/commands");
        assertTrue(Files.isDirectory(testResources), "Test resources directory must exist");
    }

    // ── Validate ────────────────────────────────────────────────────

    @Test
    void validateWellFormedXml() throws CommandException {
        var result = executor.execute(new ValidateCommand(
            testResources.resolve("valid.xml"), null
        ));
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateMalformedXml() throws CommandException {
        var result = executor.execute(new ValidateCommand(
            testResources.resolve("invalid.xml"), null
        ));
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
        assertEquals("error", result.errors().get(0).severity());
    }

    @Test
    void validateWithSchema() throws CommandException {
        var result = executor.execute(new ValidateCommand(
            testResources.resolve("valid-with-schema.xml"),
            testResources.resolve("test.xsd")
        ));
        assertTrue(result.valid(), "Document should be valid against schema");
    }

    @Test
    void validateAgainstSchemaFails() throws CommandException {
        var result = executor.execute(new ValidateCommand(
            testResources.resolve("invalid-schema.xml"),
            testResources.resolve("test.xsd")
        ));
        assertFalse(result.valid(), "Document should fail schema validation");
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void validateFileNotFound() {
        var ex = assertThrows(CommandException.class, () ->
            executor.execute(new ValidateCommand(Path.of("/nonexistent.xml"), null))
        );
        assertEquals(CommandException.ErrorCode.FILE_NOT_FOUND, ex.getCode());
    }

    // ── Parse ───────────────────────────────────────────────────────

    @Test
    void parseWellFormedXml() throws CommandException {
        var result = executor.execute(new ParseCommand(
            testResources.resolve("valid.xml")
        ));
        assertTrue(result.wellFormed());
        assertEquals("root", result.rootElement());
        assertEquals("http://example.com/test", result.rootNamespace());
        assertNotNull(result.encoding());
    }

    @Test
    void parseMalformedXml() throws CommandException {
        var result = executor.execute(new ParseCommand(
            testResources.resolve("invalid.xml")
        ));
        assertFalse(result.wellFormed());
        assertFalse(result.errors().isEmpty());
    }

    // ── Info ────────────────────────────────────────────────────────

    @Test
    void infoOnValidXml() throws CommandException {
        var result = executor.execute(new InfoCommand(
            testResources.resolve("valid.xml")
        ));
        assertEquals("root", result.rootElement());
        assertEquals("http://example.com/test", result.rootNamespace());
        assertTrue(result.sizeBytes() > 0);
        assertTrue(result.lineCount() > 0);
        assertNotNull(result.encoding());
    }

    // ── Format ──────────────────────────────────────────────────────

    @Test
    void formatXml() throws CommandException {
        var result = executor.execute(new FormatCommand(
            testResources.resolve("valid-with-schema.xml"), 4, null
        ));
        assertNotNull(result.content());
        assertTrue(result.content().contains("<root>"));
    }

    @Test
    void formatXmlToFile(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("formatted.xml");
        var result = executor.execute(new FormatCommand(
            testResources.resolve("valid-with-schema.xml"), 2, output
        ));
        assertTrue(Files.exists(output));
        assertTrue(Files.readString(output).contains("<root>"));
    }

    @Test
    void formatPreservesCrlfLineEndings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("crlf.xml");
        Files.writeString(file, "<root>\r\n<a>x</a>\r\n</root>");
        var result = executor.execute(new FormatCommand(file, null, null));
        // CRLF must be preserved, not silently rewritten to LF (no churn, --check stable)
        assertTrue(result.content().contains("\r\n"));
        assertFalse(result.content().replace("\r\n", "").contains("\n"));
    }

    // ── Transform ───────────────────────────────────────────────────

    @Test
    void transformXslt() throws CommandException {
        var result = executor.execute(new TransformCommand(
            testResources.resolve("valid-with-schema.xml"),
            testResources.resolve("test.xslt"),
            null,
            Map.of()
        ));
        assertNotNull(result.content());
        assertTrue(result.content().contains("<h1>"));
        assertTrue(result.content().contains("Test"));
    }

    @Test
    void transformXsltToFile(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("result.html");
        var result = executor.execute(new TransformCommand(
            testResources.resolve("valid-with-schema.xml"),
            testResources.resolve("test.xslt"),
            output,
            Map.of()
        ));
        assertNull(result.content());
        assertEquals(output, result.outputFile());
        assertTrue(Files.exists(output));
    }

    // ── Query (XPath) ───────────────────────────────────────────────

    @Test
    void queryXPath() throws CommandException {
        var results = executor.execute(new QueryCommand(
            testResources.resolve("valid-with-schema.xml").toString(),
            "/root/item"
        ));
        assertFalse(results.isEmpty());
        QueryResult qr = results.get(0);
        assertEquals(2, qr.matches().size());
        assertEquals("item", qr.matches().get(0).nodeName());
    }

    @Test
    void queryXPathNoMatches() throws CommandException {
        var results = executor.execute(new QueryCommand(
            testResources.resolve("valid-with-schema.xml").toString(),
            "/root/nonexistent"
        ));
        // File matched but no XPath results → empty list
        assertTrue(results.isEmpty() || results.get(0).matches().isEmpty());
    }

    @Test
    void queryInvalidXPath() {
        assertThrows(CommandException.class, () ->
            executor.execute(new QueryCommand(
                testResources.resolve("valid.xml").toString(),
                "///invalid[["
            ))
        );
    }

    // ── Editor commands throw ───────────────────────────────────────

    @Test
    void editorCommandsThrow() {
        var ex = assertThrows(CommandException.class, () ->
            executor.execute(new ListDocumentsCommand())
        );
        assertEquals(CommandException.ErrorCode.EDITOR_NOT_RUNNING, ex.getCode());
    }
}
