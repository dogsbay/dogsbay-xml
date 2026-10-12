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
import picocli.CommandLine.Parameters;

@Command(name = "wrap", description = "Wrap the current selection with an XML tag")
class WrapCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Tag name (e.g., 'note', 'p', 'uicontrol')")
    private String tagName;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }

        // Get current selection
        var selResult = client.call("getSelection", null);
        if (selResult.isNull() || (selResult.isTextual() && selResult.asText().isEmpty())) {
            System.err.println("Nothing selected. Select text in the editor first.");
            return 1;
        }

        String selection = selResult.asText();
        String wrapped = "<%s>%s</%s>".formatted(tagName, selection, tagName);

        // Replace selection with wrapped text
        var params = client.getMapper().createObjectNode();
        params.put("text", wrapped);
        client.call("replaceSelection", params);

        System.out.println("Wrapped with <" + tagName + ">");
        return 0;
    }
}
