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

@Command(name = "selection", description = "Get the currently selected text in the editor")
class SelectionCmd implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }

        var result = client.call("getSelection", null);
        if (result.isNull() || (result.isTextual() && result.asText().isEmpty())) {
            System.err.println("Nothing selected.");
            return 1;
        }
        System.out.print(result.asText());
        return 0;
    }
}
