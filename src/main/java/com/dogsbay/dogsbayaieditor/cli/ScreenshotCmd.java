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

package com.dogsbay.dogsbayaieditor.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Paints the running editor's window into a PNG (visual verification). */
@Command(name = "screenshot", description = "Capture the editor window to a PNG file")
class ScreenshotCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Output PNG path")
    Path output;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("output", output.toAbsolutePath().toString());
        JsonNode result = client.call("screenshot", params);
        System.out.println(result.has("message") ? result.get("message").asText() : result);
        return result.has("success") && result.get("success").asBoolean() ? 0 : 1;
    }
}
