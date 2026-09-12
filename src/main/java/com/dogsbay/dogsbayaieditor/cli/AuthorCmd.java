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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * CLI access to the WYSIWYG Author view of the running editor: inspect the
 * block tree, insert blocks, set text, list validation issues, switch view.
 * The block ids printed by {@code author outline} feed the editing commands.
 */
@Command(name = "author",
        description = "Drive the WYSIWYG Author view (requires running editor)",
        subcommands = {
                AuthorCmd.OutlineCmd.class,
                AuthorCmd.SwitchCmd.class,
                AuthorCmd.InsertCmd.class,
                AuthorCmd.SetTextCmd.class,
                AuthorCmd.IssuesCmd.class,
        })
public class AuthorCmd implements Runnable {

    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }

    private static EditorClient requireEditor() {
        var client = EditorClient.connect();
        if (client == null) {
            System.err.println("Editor is not running. Start the editor first.");
        }
        return client;
    }

    private static ObjectNode params(Path file) {
        ObjectNode params = new ObjectMapper().createObjectNode();
        if (file != null) {
            params.put("file", file.toAbsolutePath().toString());
        }
        return params;
    }

    @Command(name = "outline", description = "Print the Author block tree as JSON")
    static class OutlineCmd implements Callable<Integer> {

        @Parameters(index = "0", arity = "0..1", description = "File (active document if omitted)")
        Path file;

        @Override
        public Integer call() throws Exception {
            var client = requireEditor();
            if (client == null) {
                return 1;
            }
            JsonNode result = client.call("authorOutline", params(file));
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result));
            return 0;
        }
    }

    @Command(name = "switch", description = "Switch the document to the Author view")
    static class SwitchCmd implements Callable<Integer> {

        @Parameters(index = "0", arity = "0..1", description = "File (active document if omitted)")
        Path file;

        @Option(names = "--split", description = "Show XML and Author side by side")
        boolean split;

        @Override
        public Integer call() throws Exception {
            var client = requireEditor();
            if (client == null) {
                return 1;
            }
            ObjectNode p = params(file);
            if (split) {
                p.put("split", true);
            }
            JsonNode result = client.call("authorSwitch", p);
            System.out.println(result.has("message") ? result.get("message").asText() : result);
            return 0;
        }
    }

    @Command(name = "insert", description = "Insert a block under a parent block id")
    static class InsertCmd implements Callable<Integer> {

        @Parameters(index = "0", description = "Block type (p, step, ul, note, ...)")
        String type;

        @Option(names = "--parent", required = true, description = "Parent block id (from outline)")
        String parentId;

        @Option(names = "--index", description = "Position among children (append if omitted)")
        Integer index;

        @Option(names = "--file", description = "File (active document if omitted)")
        Path file;

        @Override
        public Integer call() throws Exception {
            var client = requireEditor();
            if (client == null) {
                return 1;
            }
            ObjectNode p = params(file);
            p.put("type", type);
            p.put("parentId", parentId);
            if (index != null) {
                p.put("index", index);
            }
            JsonNode result = client.call("authorInsertBlock", p);
            boolean ok = result.has("success") && result.get("success").asBoolean();
            System.out.println(result.has("message") ? result.get("message").asText() : result);
            return ok ? 0 : 1;
        }
    }

    @Command(name = "set-text", description = "Replace the plain text of a text block")
    static class SetTextCmd implements Callable<Integer> {

        @Parameters(index = "0", description = "Block id (from outline)")
        String blockId;

        @Parameters(index = "1", description = "New text")
        String text;

        @Option(names = "--file", description = "File (active document if omitted)")
        Path file;

        @Override
        public Integer call() throws Exception {
            var client = requireEditor();
            if (client == null) {
                return 1;
            }
            ObjectNode p = params(file);
            p.put("blockId", blockId);
            p.put("text", text);
            JsonNode result = client.call("authorSetText", p);
            boolean ok = result.has("success") && result.get("success").asBoolean();
            System.out.println(result.has("message") ? result.get("message").asText() : result);
            return ok ? 0 : 1;
        }
    }

    @Command(name = "issues", description = "List Author-view validation issues as JSON")
    static class IssuesCmd implements Callable<Integer> {

        @Parameters(index = "0", arity = "0..1", description = "File (active document if omitted)")
        Path file;

        @Override
        public Integer call() throws Exception {
            var client = requireEditor();
            if (client == null) {
                return 1;
            }
            JsonNode result = client.call("authorIssues", params(file));
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result));
            return 0;
        }
    }
}
