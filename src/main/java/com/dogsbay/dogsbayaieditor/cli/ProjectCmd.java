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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "project", description = "Manage editor projects",
    subcommands = {
        ProjectCmd.Create.class,
        ProjectCmd.Open.class,
        ProjectCmd.List.class,
        ProjectCmd.Info.class,
    })
class ProjectCmd implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "create", description = "Create a new project")
    static class Create implements Callable<Integer> {

        @Parameters(index = "0", description = "Project name")
        private String name;

        @Parameters(index = "1", description = "Project folder path")
        private Path folder;

        @Option(names = "--type", description = "Project type: dita, docbook")
        private String type;

        @Option(names = "--root-map", description = "Default root map (for DITA projects)")
        private Path rootMap;

        @Override
        public Integer call() throws Exception {
            var client = EditorClient.connect();
            if (client == null) {
                System.err.println("Editor is not running. Start the editor first.");
                return 1;
            }

            var params = client.getMapper().createObjectNode();
            params.put("name", name);
            params.put("folder", folder.toAbsolutePath().toString());
            if (type != null) params.put("type", type);
            if (rootMap != null) params.put("rootMap", rootMap.toString());

            JsonNode result = client.call("createProject", params);
            System.out.println("Created project: " + result.get("name").asText());
            System.out.println("  Folder: " + result.get("folderPath").asText());
            if (result.has("projectType") && !result.get("projectType").isNull()) {
                System.out.println("  Type: " + result.get("projectType").asText());
            }
            return 0;
        }
    }

    @Command(name = "open", description = "Switch to a project by name")
    static class Open implements Callable<Integer> {

        @Parameters(index = "0", description = "Project name")
        private String name;

        @Override
        public Integer call() throws Exception {
            var client = EditorClient.connect();
            if (client == null) {
                System.err.println("Editor is not running. Start the editor first.");
                return 1;
            }

            var params = client.getMapper().createObjectNode();
            params.put("name", name);
            client.call("openProject", params);
            System.out.println("Switched to project: " + name);
            return 0;
        }
    }

    @Command(name = "list", description = "List all projects")
    static class List implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            var client = EditorClient.connect();
            if (client == null) {
                System.err.println("Editor is not running. Start the editor first.");
                return 1;
            }

            JsonNode result = client.call("listProjects", client.getMapper().createObjectNode());
            if (result.isArray()) {
                for (JsonNode project : result) {
                    String marker = project.get("active").asBoolean() ? "* " : "  ";
                    System.out.println(marker + project.get("name").asText()
                        + " (" + project.get("folderPath").asText() + ")");
                }
            }
            if (!result.isArray() || result.isEmpty()) {
                System.out.println("No projects configured.");
            }
            return 0;
        }
    }

    @Command(name = "info", description = "Show current project info")
    static class Info implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            var client = EditorClient.connect();
            if (client == null) {
                System.err.println("Editor is not running. Start the editor first.");
                return 1;
            }

            JsonNode result = client.call("getProject", client.getMapper().createObjectNode());
            System.out.println("Project: " + result.get("name").asText());
            System.out.println("  Folder: " + result.get("folderPath").asText());
            if (result.has("projectType") && !result.get("projectType").isNull()) {
                System.out.println("  Type: " + result.get("projectType").asText());
            }
            if (result.has("defaultRootMap") && !result.get("defaultRootMap").isNull()) {
                System.out.println("  Root Map: " + result.get("defaultRootMap").asText());
            }
            return 0;
        }
    }
}
