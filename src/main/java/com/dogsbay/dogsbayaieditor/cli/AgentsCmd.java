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

import com.fasterxml.jackson.databind.JsonNode;

@Command(name = "agents", description = "List the ACP registry agents and whether this machine can run them")
class AgentsCmd implements Callable<Integer> {
    @Option(names = "--all", description = "Include agents this machine cannot run")
    private boolean all;

    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }
        JsonNode result = client.call("agents", null);
        int shown = 0;
        for (JsonNode a : result) {
            String readiness = a.path("readiness").asText();
            if (!all && "UNAVAILABLE".equals(readiness)) {
                continue;
            }
            shown++;
            System.out.printf("  %-22s %-12s %s%n", a.path("id").asText(), readiness.toLowerCase(), a.path("name").asText());
        }
        System.out.println(shown + " agent(s)" + (all ? "" : " runnable here; --all for the rest") + ".");
        return 0;
    }
}
