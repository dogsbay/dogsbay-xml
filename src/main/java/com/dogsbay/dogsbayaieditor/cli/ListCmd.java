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

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;

@Command(name = "list", description = "List open documents in the editor")
class ListCmd implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }

        JsonNode result = client.call("listDocuments", null);
        if (!result.isArray() || result.isEmpty()) {
            System.out.println("No documents open.");
            return 0;
        }

        for (JsonNode doc : result) {
            String marker = doc.has("active") && doc.get("active").asBoolean() ? " *" : "";
            String modified = doc.has("modified") && doc.get("modified").asBoolean() ? " [modified]" : "";
            String title = doc.has("title") ? doc.get("title").asText() : "(untitled)";
            String path = doc.has("file") && !doc.get("file").isNull()
                ? doc.get("file").asText() : "";

            System.out.printf("  %s%s%s%n", title + marker, modified,
                path.isEmpty() ? "" : "  (" + path + ")");
        }
        return 0;
    }
}
