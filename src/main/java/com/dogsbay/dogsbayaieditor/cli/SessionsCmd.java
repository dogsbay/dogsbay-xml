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

import com.fasterxml.jackson.databind.JsonNode;

@Command(name = "sessions", description = "List the agent sessions connected to the running editor")
class SessionsCmd implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
            return 1;
        }
        JsonNode result = client.call("sessions", null);
        for (JsonNode s : result) {
            System.out.printf("  %-28s %-13s %-10s %-24s %s%n", s.path("id").asText(), s.path("kind").asText(),
                    s.path("tier").asText(), s.path("identity").asText(), s.path("name").asText());
        }
        System.out.println(result.size() + " session(s).");
        return 0;
    }
}
