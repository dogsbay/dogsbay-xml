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

package com.dogsbay.dogsbayaieditor.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/** The MCP/agent surface: validate_project -> "validate-project" rpc dispatch. */
class JsonRpcValidateProjectTest {

    @SuppressWarnings("unchecked")
    @Test
    void dispatchesValidateProjectAndReportsFailures(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ok.xml"), "<root/>");
        Files.writeString(dir.resolve("bad.xml"), "<root><a></root>");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode args = mapper.createObjectNode();
        args.put("root", dir.toString());
        args.put("scope", "glob:*.xml");

        Object out = new JsonRpcHandler(new HeadlessExecutor())
                .dispatch("validate-project", args);

        BatchResult<FileValidation> r = (BatchResult<FileValidation>) out;
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.findings()).singleElement()
                .satisfies(fv -> assertThat(fv.file()).endsWith("bad.xml"));
    }

    @Test
    void mapParamBecomesPublicationSetScope(@TempDir Path dir) throws Exception {
        // map + one referenced topic; dispatch with "map" (not "scope")
        Files.writeString(dir.resolve("guide.ditamap"),
                "<map><topicref href=\"t.xml\"/></map>");
        Files.writeString(dir.resolve("t.xml"), "<topic id=\"t\"><body><p>ok</p></body></topic>");
        Files.writeString(dir.resolve("orphan.xml"), "<topic id=\"o\"/>");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode args = mapper.createObjectNode();
        args.put("root", dir.toString());
        args.put("map", dir.resolve("guide.ditamap").toString());

        @SuppressWarnings("unchecked")
        BatchResult<FileValidation> r = (BatchResult<FileValidation>)
                new JsonRpcHandler(new HeadlessExecutor()).dispatch("validate-project", args);

        // The "map" param resolves to the publication set: map + t.xml, with
        // orphan.xml excluded (= 2 files validated). Validity itself is not
        // asserted here — DITA validity depends on the bundled catalog being
        // present, which is environment-dependent; scope resolution is the point.
        assertThat(r.total()).isEqualTo(2);
    }
}
