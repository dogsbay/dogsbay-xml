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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "select-element",
         description = "Select the XML element surrounding the caret")
class SelectElementCmd implements Callable<Integer> {

    @Option(names = "--content",
            description = "Select only the element content, excluding its tags")
    boolean contentOnly;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }
        var params = client.getMapper().createObjectNode();
        params.put("contentOnly", contentOnly);
        client.call("selectElement", params);
        var selection = client.call("getSelection", null);
        if (!selection.isNull()) {
            System.out.print(selection.asText());
        }
        return 0;
    }
}
