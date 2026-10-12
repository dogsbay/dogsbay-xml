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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DocumentInfo;
import com.dogsbay.dogsbayaieditor.commands.results.FormatResult;
import com.dogsbay.dogsbayaieditor.commands.results.ParseResult;
import com.dogsbay.dogsbayaieditor.commands.results.QueryResult;

/**
 * A3 regression: the raw-XML toolbox (query/parse/format/info) must work on DITA
 * topics whose DOCTYPE references a relative DTD ("task.dtd") that isn't on disk.
 * External DTD loading is disabled, so these read-only tools parse without a
 * catalog. Before the fix they failed with "Failed to parse: …/task.dtd".
 */
class HeadlessRawXmlDtdTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private Path ditaFile;

    private static String previousCatalog;

    @BeforeAll
    static void useBundledCatalog() throws Exception {
        // Mirror an installed editor/CLI: point the catalog resolver at the bundled
        // DITA catalog so DITA DOCTYPEs resolve in the test JVM too.
        java.net.URL u = HeadlessRawXmlDtdTest.class.getResource(
            "/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/dtd/catalog.xml");
        org.junit.jupiter.api.Assumptions.assumeTrue(u != null,
            "bundled DITA catalog resource not on the test classpath");
        previousCatalog = System.getProperty("xml.catalog.files");
        System.setProperty("xml.catalog.files", new java.io.File(u.toURI()).getAbsolutePath());
    }

    @AfterAll
    static void restoreCatalog() {
        if (previousCatalog == null) {
            System.clearProperty("xml.catalog.files");
        } else {
            System.setProperty("xml.catalog.files", previousCatalog);
        }
    }

    @BeforeEach
    void writeDitaTopic() throws Exception {
        ditaFile = dir.resolve("trim.dita");
        Files.writeString(ditaFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
            <task id="trim">
              <title>Trim</title>
              <taskbody>
                <steps>
                  <step>
                    <cmd>Press <uicontrol>Delete</uicontrol> now.</cmd>
                    <info>
                      <note type="tipp"><p>planted bad type</p></note>
                      <note type="warning"><p>be careful</p></note>
                    </info>
                  </step>
                </steps>
              </taskbody>
            </task>
            """);
    }

    @Test
    void queryReturnsNoteTypesNotADtdError() throws Exception {
        List<QueryResult> results = executor.execute(
            new QueryCommand(ditaFile.toString(), "//note/@type"));

        assertThat(results).hasSize(1);
        var matches = results.get(0).matches();
        // No "error" match (the old failure mode), and both note types are found.
        assertThat(matches).noneMatch(m -> "error".equals(m.nodeType()));
        assertThat(matches).extracting("textContent")
            .containsExactlyInAnyOrder("tipp", "warning");
    }

    @Test
    void parseSucceedsOnDoctypeTopic() throws Exception {
        ParseResult r = executor.execute(new ParseCommand(ditaFile));
        assertThat(r.wellFormed()).isTrue();
        assertThat(r.rootElement()).isEqualTo("task");
    }

    @Test
    void infoSucceedsOnDoctypeTopic() throws Exception {
        DocumentInfo info = executor.execute(new InfoCommand(ditaFile));
        assertThat(info.rootElement()).isEqualTo("task");
    }

    @Test
    void formatSucceedsOnDoctypeTopic() throws Exception {
        FormatResult r = executor.execute(new FormatCommand(ditaFile, 2, null));
        assertThat(r.content()).contains("<task");
        // No DTD-default attributes injected (e.g. @class).
        assertThat(r.content()).doesNotContain("class=\"- topic/topic");
        // Mixed content is preserved — the editor formatter does not split the
        // inline <uicontrol> out of the surrounding prose (the JAXP indenter could).
        assertThat(r.content()).contains("Press <uicontrol>Delete</uicontrol> now.");
    }
}
