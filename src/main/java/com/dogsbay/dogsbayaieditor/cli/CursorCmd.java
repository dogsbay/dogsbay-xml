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

@Command(name = "cursor",
         description = "Show or move the editor caret. With no options, prints the position.")
class CursorCmd implements Callable<Integer> {

    @Option(names = "--set", description = "Move the caret to this character offset")
    Integer set;

    @Option(names = "--move", description = "Move the caret by this signed delta")
    Integer move;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }
        if (set != null && move != null) {
            System.err.println("Use --set or --move, not both.");
            return 2;
        }
        if (set != null || move != null) {
            boolean relative = move != null;
            var params = client.getMapper().createObjectNode();
            params.put("position", relative ? move : set);
            params.put("relative", relative);
            client.call("setCursor", params);
        }
        var pos = client.call("getCursor", null);
        System.out.printf("offset %d, line %d, column %d%n",
            pos.get("offset").asInt(), pos.get("line").asInt(), pos.get("column").asInt());
        return 0;
    }
}
