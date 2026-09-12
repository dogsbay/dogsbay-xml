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

import com.dogsbay.dogsbayaieditor.ipc.DiscoveryFile;

@Command(name = "status", description = "Show editor connection status")
class StatusCmd implements Callable<Integer> {

    @Option(names = "--mcp-config", description = "Print MCP server config for Claude Code/Desktop")
    private boolean mcpConfig;

    @Option(names = "--mcp-install", description = "Print the claude mcp add command")
    private boolean mcpInstall;

    @Option(names = "--reset-token", description = "Generate a new auth token (requires editor restart)")
    private boolean resetToken;

    @Override
    public Integer call() {
        var info = DiscoveryFile.read();
        if (info == null) {
            System.out.println("Editor is not running.");
            return 1;
        }

        if (resetToken) {
            DiscoveryFile.resetToken();
            System.out.println("Auth token reset. Restart the editor to use the new token.");
            System.out.println("Then re-run: bin/dogsbay-xml status --mcp-install");
            return 0;
        }

        if (mcpInstall) {
            System.out.println("claude mcp add --transport http dogsbay-editor http://localhost:"
                + info.httpPort() + "/mcp --header \"Authorization: Bearer "
                + info.authToken() + "\"");
            return 0;
        }

        if (mcpConfig) {
            System.out.println("""
                Add this to your Claude Code or Claude Desktop MCP config:

                {
                  "mcpServers": {
                    "dogsbay-editor": {
                      "url": "http://localhost:%d/mcp",
                      "headers": {
                        "Authorization": "Bearer %s"
                      }
                    }
                  }
                }

                Or run:
                  claude mcp add --transport http dogsbay-editor http://localhost:%d/mcp --header "Authorization: Bearer %s"

                The auth token is persistent across editor restarts.
                """.formatted(info.httpPort(), info.authToken(), info.httpPort(), info.authToken()));
            return 0;
        }

        System.out.println("Editor is running:");
        System.out.println("  PID:     " + info.pid());
        System.out.println("  Port:    " + info.httpPort());
        System.out.println("  Version: " + info.version());
        if (info.ipcSocket() != null) {
            System.out.println("  Socket:  " + info.ipcSocket());
        }
        System.out.println("  RPC:     http://localhost:" + info.httpPort() + "/rpc");
        System.out.println("  MCP:     http://localhost:" + info.httpPort() + "/mcp");
        return 0;
    }
}
