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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "save", description = "Save a document or all documents")
class SaveCmd implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "File to save (active document if omitted)")
    private Path file;

    @Option(names = "--all", description = "Save all open documents")
    private boolean all;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }

        var params = client.getMapper().createObjectNode();
        if (file != null) params.put("file", file.toAbsolutePath().toString());
        params.put("all", all);
        var result = client.call("save", params);
        System.out.println(result.has("message") ? result.get("message").asText() : "Saved");
        return 0;
    }
}
