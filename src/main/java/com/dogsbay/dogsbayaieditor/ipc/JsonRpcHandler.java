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

package com.dogsbay.dogsbayaieditor.ipc;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.dogsbay.dogsbayaieditor.commands.*;
import com.dogsbay.dogsbayaieditor.commands.results.*;

/**
 * Handles JSON-RPC 2.0 requests by dispatching to the CommandExecutor.
 * Used by both the IPC socket server and the REST API.
 */
public class JsonRpcHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final CommandExecutor executor;

    public JsonRpcHandler(CommandExecutor executor) {
        this.executor = executor;
    }

    /**
     * Handle a JSON-RPC request string, return a JSON-RPC response string.
     */
    public String handle(String requestJson) {
        return handle(requestJson, SessionContext.current());
    }

    /** Handle a request on behalf of {@code session}. */
    public String handle(String requestJson, AgentSession session) {
        try {
            JsonNode request = mapper.readTree(requestJson);
            String method = request.get("method").asText();
            JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();
            JsonNode id = request.get("id");

            Object result = dispatch(method, params, session);
            return formatResult(id, result);
        } catch (CommandException e) {
            return formatError(null, mapErrorCode(e.getCode()), e.getMessage());
        } catch (Exception e) {
            return formatError(null, -32603, e.getMessage());
        }
    }

    /**
     * Dispatch a method name + params to the appropriate Command.
     */
    public Object dispatch(String method, JsonNode params) throws CommandException {
        return dispatch(method, params, SessionContext.current());
    }

    /**
     * Dispatch on behalf of {@code session}: bound for the duration so the
     * executor attributes, gates and audits the command to that session.
     */
    public Object dispatch(String method, JsonNode params, AgentSession session) throws CommandException {
        Bound executor = new Bound(this.executor, session);
        return dispatchWith(executor, method, params);
    }

    /** The executor with one session attached: every command below runs as it. */
    private record Bound(CommandExecutor delegate, AgentSession session) {
        <R> R execute(Command<R> command) throws CommandException {
            return delegate.execute(command, session);
        }
    }

    private Object dispatchWith(Bound executor, String method, JsonNode params) throws CommandException {
        return switch (method) {
            case "validate" -> {
                Path file = pathParam(params, "file");
                Path schema = optionalPathParam(params, "schema");
                List<Path> catalogs = pathListParam(params, "catalogs");
                yield executor.execute(new ValidateCommand(file, schema, catalogs));
            }
            case "parse" -> {
                Path file = pathParam(params, "file");
                yield executor.execute(new ParseCommand(file));
            }
            case "info" -> {
                Path file = pathParam(params, "file");
                yield executor.execute(new InfoCommand(file));
            }
            case "format" -> {
                Path file = pathParam(params, "file");
                // omitted indent → use the project/house style (null), not a forced 2
                Integer indent = params.has("indent") ? params.get("indent").asInt() : null;
                Path output = optionalPathParam(params, "output");
                yield executor.execute(new FormatCommand(file, indent, output));
            }
            case "reflow" -> {
                Path file = pathParam(params, "file");
                Path output = optionalPathParam(params, "output");
                yield executor.execute(new ReflowCommand(file, output));
            }
            case "query" -> {
                String filePattern = params.get("file").asText();
                String xpath = params.get("xpath").asText();
                yield executor.execute(new QueryCommand(filePattern, xpath));
            }
            case "transform" -> {
                Path input = pathParam(params, "input");
                Path xslt = pathParam(params, "xslt");
                Path output = optionalPathParam(params, "output");
                yield executor.execute(new TransformCommand(input, xslt, output, Map.of()));
            }
            case "where-used" -> {
                String file = params.get("file").asText();
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "map");
                yield executor.execute(new WhereUsedCommand(file, root, rootMap));
            }
            case "list-keys" -> {
                String rootMap = params.get("map").asText();
                String ditaval = optionalString(params, "ditaval");
                yield executor.execute(new ListKeysCommand(rootMap, ditaval));
            }
            case "resolve-key" -> {
                String key = params.get("key").asText();
                String rootMap = params.get("map").asText();
                String ditaval = optionalString(params, "ditaval");
                String scope = optionalString(params, "scope");
                yield executor.execute(new ResolveKeyCommand(key, rootMap, ditaval, scope));
            }
            case "check-links" -> {
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "map");
                yield executor.execute(new CheckLinksCommand(root, rootMap));
            }
            case "render-preview" -> {
                String file = params.get("file").asText();
                String output = optionalString(params, "output");
                String map = optionalString(params, "map");
                String ditaval = optionalString(params, "ditaval");
                boolean showChanges = params.path("showChanges").asBoolean(false);
                yield executor.execute(new RenderPreviewCommand(file, output, map, ditaval, showChanges));
            }
            case "project-graph" -> {
                String root = requiredString(params, "root");
                yield executor.execute(new ProjectGraphCommand(root, optionalString(params, "map"),
                        optionalString(params, "deliverable"), params.path("checks").asBoolean(true)));
            }
            case "render-report" -> {
                String root = optionalString(params, "root");
                String output = requiredString(params, "output");
                if (root != null && !Path.of(output).isAbsolute()) {
                    output = Path.of(root).resolve(output).toString();
                }
                String template = optionalString(params, "template");
                String data = optionalString(params, "data");
                String sourceLabel = null;
                if (data == null) {
                    // Run the source under the same session, so the page shows exactly what the tool returns.
                    String source = optionalString(params, "source");
                    if (source == null) {
                        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                                "render_report needs a source (a read-only tool such as project_graph) or data (JSON)");
                    }
                    String sourceMethod = source.trim().replace('_', '-');
                    if (!isReadOnly(sourceMethod)) {
                        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                                "A report's source must be a read-only tool; '" + source + "' is not one");
                    }
                    ObjectNode args = sourceArgs(params.get("args"));
                    if (root != null && !args.has("root")) {
                        args.put("root", root);
                    }
                    Object result = dispatchWith(executor, sourceMethod, args);
                    try {
                        data = mapper.writeValueAsString(result);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                                "Could not serialise the " + sourceMethod + " result: " + e.getMessage(), e);
                    }
                    sourceLabel = sourceMethod + " " + args;
                    if (template == null) {
                        template = defaultTemplateFor(sourceMethod);
                    }
                }
                yield executor.execute(new RenderReportCommand(root, template, output, data, sourceLabel));
            }
            case "health" -> {
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "map");
                yield executor.execute(new HealthCommand(root, rootMap));
            }
            case "validate-project" -> {
                String root = params.get("root").asText();
                String scope = optionalString(params, "scope");
                if (scope == null) {
                    String map = optionalString(params, "map");
                    if (map != null) {
                        scope = "map:" + map;
                    }
                }
                yield executor.execute(
                    new ValidateProjectCommand(root, scope, java.util.List.of()));
            }
            case "project-health" -> {
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "map");
                String schematron = optionalString(params, "schematron");
                // Agents get rules grouped unless they ask otherwise: the per-file form
                // repeated one message dozens of times.
                yield executor.execute(new ProjectHealthCommand(root, rootMap, schematron,
                        stringList(params, "include"), optionalString(params, "severity"),
                        params.path("group").asBoolean(true)));
            }
            case "conref-audit" -> {
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "map");
                yield executor.execute(new ConrefAuditCommand(root, rootMap));
            }
            case "schematron-project" -> {
                String root = params.get("root").asText();
                String schema = params.get("schema").asText();
                String scope = optionalString(params, "scope");
                if (scope == null) {
                    String map = optionalString(params, "map");
                    if (map != null) {
                        scope = "map:" + map;
                    }
                }
                yield executor.execute(new SchematronProjectCommand(root, scope, schema));
            }
            case "schematron" -> {
                String file = requiredString(params, "file");
                String schema = requiredString(params, "schema");
                yield executor.execute(new SchematronCommand(file, schema));
            }
            case "review-list" -> {
                String file = requiredString(params, "file");
                String author = params.has("author") && !params.get("author").isNull() ? params.get("author").asText() : null;
                yield executor.execute(new ReviewListCommand(file, author));
            }
            case "review-accept" -> {
                String file = requiredString(params, "file");
                yield executor.execute(new ReviewAcceptCommand(file, optionalString(params, "id"),
                        optionalString(params, "author"), params.path("all").asBoolean(false)));
            }
            case "review-reject" -> {
                String file = requiredString(params, "file");
                yield executor.execute(new ReviewRejectCommand(file, optionalString(params, "id"),
                        optionalString(params, "author"), params.path("all").asBoolean(false)));
            }
            case "review-comment" -> {
                String file = requiredString(params, "file");
                String text = requiredString(params, "text");
                yield executor.execute(new ReviewCommentCommand(file, optionalString(params, "afterText"),
                        optionalString(params, "elementId"), text));
            }
            case "sessions" -> executor.execute(new ListSessionsCommand());
            case "agents" -> executor.execute(new ListAgentsCommand());
            case "audit-log" -> executor.execute(new AuditLogCommand(optionalString(params, "root"),
                    optionalString(params, "identity"), optionalString(params, "command"),
                    params.path("limit").asInt(50)));
            case "validate-deliverables" -> {
                String root = params.get("root").asText();
                yield executor.execute(new ValidateDeliverablesCommand(root));
            }
            case "validate-deep" -> {
                String root = params.get("root").asText();
                String deliverable = params.has("deliverable")
                        ? params.get("deliverable").asText(null) : null;
                String map = params.has("map") ? params.get("map").asText(null) : null;
                yield executor.execute(new ValidateDeepCommand(root, deliverable, map, null));
            }
            case "build-deliverables" -> {
                String root = params.get("root").asText();
                String output = params.has("output") ? params.get("output").asText(null) : null;
                String deliverable = params.has("deliverable")
                        ? params.get("deliverable").asText(null) : null;
                yield executor.execute(
                        new BuildDeliverablesCommand(root, output, deliverable, null));
            }
            case "validate-conditions" -> {
                String root = params.get("root").asText();
                String subjectScheme = optionalString(params, "subjectScheme");
                String scope = optionalString(params, "scope");
                if (scope == null) {
                    String map = optionalString(params, "map");
                    if (map != null) {
                        scope = "map:" + map;
                    }
                }
                yield executor.execute(
                        new ValidateConditionsCommand(root, scope, subjectScheme));
            }
            case "list-subjects" -> {
                String subjectScheme = params.get("subjectScheme").asText();
                yield executor.execute(new ListSubjectsCommand(subjectScheme));
            }
            case "metadata-audit" -> {
                String root = params.get("root").asText();
                String scope = optionalString(params, "scope");
                if (scope == null) {
                    String map = optionalString(params, "map");
                    if (map != null) {
                        scope = "map:" + map;
                    }
                }
                String policy = optionalString(params, "policy");
                yield executor.execute(new MetadataAuditCommand(root, scope, policy));
            }
            case "metadata-export-schematron" -> {
                String root = params.get("root").asText();
                String policy = optionalString(params, "policy");
                String output = optionalString(params, "output");
                yield executor.execute(
                        new ExportMetadataSchematronCommand(root, policy, output));
            }
            case "metadata-set" -> {
                String root = params.get("root").asText();
                String scope = optionalString(params, "scope");
                if (scope == null) {
                    String map = optionalString(params, "map");
                    if (map != null) {
                        scope = "map:" + map;
                    }
                }
                boolean dryRun = params.has("dryRun") && params.get("dryRun").asBoolean(false);
                java.util.List<MetadataSetSpec> specs = new java.util.ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode ch = params.get("changes");
                if (ch != null && ch.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : ch) {
                        specs.add(new MetadataSetSpec(n.get("field").asText(),
                                n.has("value") ? n.get("value").asText(null) : null,
                                n.has("mode") ? n.get("mode").asText(null) : null));
                    }
                } else if (params.has("field")) {
                    specs.add(new MetadataSetSpec(params.get("field").asText(),
                            optionalString(params, "value"), optionalString(params, "mode")));
                }
                yield executor.execute(new MetadataSetCommand(root, scope, specs, dryRun));
            }
            case "edit-map" -> {
                Integer index = params.has("index") && !params.get("index").isNull()
                        ? params.get("index").asInt() : null;
                boolean dryRun = params.has("dryRun") && params.get("dryRun").asBoolean(false);
                yield executor.execute(new com.dogsbay.dogsbayaieditor.commands.EditMapCommand(
                        params.get("map").asText(),
                        params.get("op").asText(),
                        optionalString(params, "ref"),
                        optionalString(params, "parent"),
                        index,
                        optionalString(params, "type"),
                        optionalString(params, "name"),
                        optionalString(params, "value"),
                        optionalString(params, "href"),
                        optionalString(params, "navtitle"),
                        optionalString(params, "toMap"),
                        dryRun));
            }
            case "reltable-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.ReltableAuditCommand(
                        params.get("map").asText()));
            case "keyword-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.KeywordAuditCommand(
                        params.get("root").asText(), optionalString(params, "scope")));
            case "index-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.IndexAuditCommand(
                        params.get("root").asText(), optionalString(params, "scope")));
            case "glossary-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.GlossaryAuditCommand(
                        params.get("root").asText(), optionalString(params, "scope"),
                        optionalString(params, "rootMap")));
            case "conref-push-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.ConrefPushAuditCommand(
                        params.get("root").asText(), optionalString(params, "scope"),
                        optionalString(params, "rootMap")));
            case "chunk-audit" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.ChunkAuditCommand(
                        params.get("root").asText(), optionalString(params, "scope")));
            case "specialization-info" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands
                        .SpecializationInfoCommand(params.get("file").asText()));
            case "list-branches" ->
                executor.execute(new com.dogsbay.dogsbayaieditor.commands.ListBranchesCommand(
                        params.get("map").asText()));
            case "edit-reltable" -> {
                java.util.function.Function<String, Integer> intOf = k ->
                        params.has(k) && !params.get(k).isNull() ? params.get(k).asInt() : null;
                boolean dryRun = params.has("dryRun") && params.get("dryRun").asBoolean(false);
                yield executor.execute(new com.dogsbay.dogsbayaieditor.commands.EditReltableCommand(
                        params.get("map").asText(),
                        params.has("table") ? params.get("table").asInt() : 0,
                        params.get("op").asText(),
                        intOf.apply("row"), intOf.apply("col"), intOf.apply("index"),
                        optionalString(params, "href"), optionalString(params, "keyref"),
                        optionalString(params, "navtitle"), optionalString(params, "name"),
                        optionalString(params, "value"), optionalString(params, "columns"),
                        dryRun));
            }
            case "rename-file" -> {
                String file = params.get("file").asText();
                String newPath = params.get("newPath").asText();
                String root = params.get("root").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new RenameFileCommand(file, newPath, root, apply));
            }
            case "rename-key" -> {
                String oldKey = params.get("oldKey").asText();
                String newKey = params.get("newKey").asText();
                String root = params.get("root").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new RenameKeyCommand(oldKey, newKey, root, apply));
            }
            case "delete-file" -> {
                String file = params.get("file").asText();
                String root = params.get("root").asText();
                boolean removeRefs = params.has("removeRefs")
                        && params.get("removeRefs").asBoolean(false);
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new DeleteFileCommand(file, root, removeRefs, apply));
            }
            case "retarget" -> {
                String from = params.get("from").asText();
                String to = params.get("to").asText();
                String root = params.get("root").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new RetargetCommand(from, to, root, apply));
            }
            case "keyify" -> {
                String file = params.get("file").asText();
                String key = params.get("key").asText();
                String map = params.get("map").asText();
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "rootMap");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new KeyifyCommand(file, key, map, root, rootMap, apply));
            }
            case "inline-key" -> {
                String key = params.get("key").asText();
                String map = params.get("map").asText();
                String root = params.get("root").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new InlineKeyCommand(key, map, root, apply));
            }
            case "extract-conref" -> {
                String file = params.get("file").asText();
                String elementId = params.get("elementId").asText();
                String warehouse = params.get("to").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new ExtractConrefCommand(
                        file, elementId, warehouse, apply));
            }
            case "inline-conref" -> {
                String target = params.get("target").asText();
                String elementId = params.get("elementId").asText();
                String root = params.get("root").asText();
                String file = optionalString(params, "file");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new InlineConrefCommand(
                        target, elementId, root, file, apply));
            }
            case "rename-element-id" -> {
                String file = params.get("file").asText();
                String oldId = params.get("oldId").asText();
                String newId = params.get("newId").asText();
                String root = params.get("root").asText();
                String rootMap = optionalString(params, "rootMap");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new RenameElementIdCommand(
                        file, oldId, newId, root, rootMap, apply));
            }
            case "merge-keydefs" -> {
                String rootMap = params.get("rootMap").asText();
                String key = optionalString(params, "key");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new MergeKeydefsCommand(rootMap, key, apply));
            }
            case "split-topic" -> {
                String file = params.get("file").asText();
                String root = params.get("root").asText();
                String map = optionalString(params, "map");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new SplitTopicCommand(file, root, map, apply));
            }
            case "rename-profile-value" -> {
                String attribute = params.get("attribute").asText();
                String oldValue = params.get("oldValue").asText();
                String newValue = params.get("newValue").asText();
                String root = params.get("root").asText();
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new RenameProfileValueCommand(
                        attribute, oldValue, newValue, root, apply));
            }
            case "create-keydef" -> {
                String key = params.get("key").asText();
                String text = params.get("text").asText();
                String map = params.get("map").asText();
                String rootMap = optionalString(params, "rootMap");
                String replaceRoot = optionalString(params, "replaceRoot");
                boolean apply = params.has("apply") && params.get("apply").asBoolean(false);
                yield executor.execute(new CreateKeydefCommand(
                        key, text, map, rootMap, replaceRoot, apply));
            }
            case "getSelection" -> executor.execute(new GetSelectionCommand());
            case "replaceSelection" -> {
                String replaceText = params.get("text").asText();
                yield executor.execute(new ReplaceSelectionCommand(replaceText));
            }
            case "gotoLine" -> executor.execute(new GotoLineCommand(params.get("line").asInt()));
            case "getCursor" -> executor.execute(new GetCursorCommand());
            case "setCursor" -> {
                boolean relative = params.has("relative") && params.get("relative").asBoolean();
                yield executor.execute(
                    new SetCursorCommand(params.get("position").asInt(), relative));
            }
            case "selectElement" -> {
                boolean contentOnly =
                    params.has("contentOnly") && params.get("contentOnly").asBoolean();
                yield executor.execute(new SelectElementCommand(contentOnly));
            }
            case "open" -> {
                Path file = pathParam(params, "file");
                String grammar = params.has("grammar") ? params.get("grammar").asText() : null;
                yield executor.execute(new OpenCommand(file, grammar));
            }
            case "close" -> {
                Path file = optionalPathParam(params, "file");
                boolean all = params.has("all") && params.get("all").asBoolean();
                yield executor.execute(new CloseCommand(file, all));
            }
            case "save" -> {
                Path file = optionalPathParam(params, "file");
                boolean all = params.has("all") && params.get("all").asBoolean();
                yield executor.execute(new SaveCommand(file, all));
            }
            case "listDocuments" -> executor.execute(new ListDocumentsCommand());
            case "getContent" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new GetContentCommand(file));
            }
            case "setContent" -> {
                Path file = optionalPathParam(params, "file");
                String content = params.get("content").asText();
                yield executor.execute(new SetContentCommand(file, content));
            }
            case "getOutline" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new GetOutlineCommand(file));
            }
            case "authorSwitch" -> {
                Path file = optionalPathParam(params, "file");
                boolean split = params.has("split") && params.get("split").asBoolean();
                yield executor.execute(new AuthorSwitchCommand(file, split));
            }
            case "authorOutline" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new AuthorOutlineCommand(file));
            }
            case "authorInsertBlock" -> {
                Path file = optionalPathParam(params, "file");
                Integer index = params.has("index") ? params.get("index").asInt() : null;
                yield executor.execute(new AuthorInsertBlockCommand(file,
                        requiredString(params, "type"), requiredString(params, "parentId"), index));
            }
            case "authorSetText" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new AuthorSetTextCommand(file,
                        requiredString(params, "blockId"), requiredString(params, "text")));
            }
            case "authorIssues" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new AuthorIssuesCommand(file));
            }
            case "screenshot" -> executor.execute(
                    new ScreenshotCommand(params.get("output").asText()));
            case "getErrors" -> {
                Path file = optionalPathParam(params, "file");
                yield executor.execute(new GetErrorsCommand(file));
            }
            case "search" -> {
                String query = params.get("query").asText();
                String glob = params.has("glob") ? params.get("glob").asText() : null;
                boolean regex = params.has("regex") && params.get("regex").asBoolean();
                boolean caseSensitive = !params.has("caseSensitive") || params.get("caseSensitive").asBoolean();
                int max = params.has("maxResults") ? params.get("maxResults").asInt() : 0;
                yield executor.execute(new SearchProjectCommand(query, glob, regex, caseSensitive, max));
            }
            case "listFiles" -> {
                String pattern = params.get("pattern").asText();
                yield executor.execute(new ListProjectFilesCommand(pattern));
            }
            case "createProject" -> {
                String name = params.get("name").asText();
                Path folder = pathParam(params, "folder");
                String type = params.has("type") ? params.get("type").asText() : null;
                Path rootMap = optionalPathParam(params, "rootMap");
                yield executor.execute(new CreateProjectCommand(name, folder, type, rootMap));
            }
            case "openProject" -> {
                String name = params.get("name").asText();
                yield executor.execute(new OpenProjectCommand(name));
            }
            case "listProjects" -> executor.execute(new ListProjectsCommand());
            case "getProject" -> executor.execute(new GetProjectCommand());
            case "newDocument" -> {
                Path file = pathParam(params, "file");
                String content = params.has("content") ? params.get("content").asText() : null;
                yield executor.execute(new NewDocumentCommand(file, content));
            }
            case "listSidebars" -> executor.execute(new ListSidebarsCommand());
            case "switchSidebar" -> {
                String id = params.get("id").asText();
                String side = params.has("side") ? params.get("side").asText() : "left";
                yield executor.execute(new SwitchSidebarCommand(id, side));
            }
            case "openDitaMap" -> {
                Path file = pathParam(params, "file");
                yield executor.execute(new OpenDitaMapCommand(file));
            }
            default -> throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "Unknown method: " + method
            );
        };
    }

    /**
     * The command class an RPC method builds. Mirrors {@link #dispatch} and
     * is checked against it by {@code JsonRpcMethodMapTest}.
     */
    public static Class<? extends Command<?>> commandClassOf(String method) {
        return switch (method) {
            case "validate" -> ValidateCommand.class;
            case "parse" -> ParseCommand.class;
            case "info" -> InfoCommand.class;
            case "format" -> FormatCommand.class;
            case "reflow" -> ReflowCommand.class;
            case "query" -> QueryCommand.class;
            case "transform" -> TransformCommand.class;
            case "where-used" -> WhereUsedCommand.class;
            case "list-keys" -> ListKeysCommand.class;
            case "resolve-key" -> ResolveKeyCommand.class;
            case "check-links" -> CheckLinksCommand.class;
            case "render-preview" -> RenderPreviewCommand.class;
            case "render-report" -> RenderReportCommand.class;
            case "project-graph" -> ProjectGraphCommand.class;
            case "health" -> HealthCommand.class;
            case "validate-project" -> ValidateProjectCommand.class;
            case "project-health" -> ProjectHealthCommand.class;
            case "conref-audit" -> ConrefAuditCommand.class;
            case "schematron-project" -> SchematronProjectCommand.class;
            case "schematron" -> SchematronCommand.class;
            case "validate-deliverables" -> ValidateDeliverablesCommand.class;
            case "validate-deep" -> ValidateDeepCommand.class;
            case "build-deliverables" -> BuildDeliverablesCommand.class;
            case "validate-conditions" -> ValidateConditionsCommand.class;
            case "list-subjects" -> ListSubjectsCommand.class;
            case "metadata-audit" -> MetadataAuditCommand.class;
            case "metadata-export-schematron" -> ExportMetadataSchematronCommand.class;
            case "metadata-set" -> MetadataSetCommand.class;
            case "edit-map" -> EditMapCommand.class;
            case "reltable-audit" -> ReltableAuditCommand.class;
            case "keyword-audit" -> KeywordAuditCommand.class;
            case "index-audit" -> IndexAuditCommand.class;
            case "glossary-audit" -> GlossaryAuditCommand.class;
            case "conref-push-audit" -> ConrefPushAuditCommand.class;
            case "chunk-audit" -> ChunkAuditCommand.class;
            case "specialization-info" -> SpecializationInfoCommand.class;
            case "list-branches" -> ListBranchesCommand.class;
            case "review-list" -> ReviewListCommand.class;
            case "review-accept" -> ReviewAcceptCommand.class;
            case "review-reject" -> ReviewRejectCommand.class;
            case "review-comment" -> ReviewCommentCommand.class;
            case "sessions" -> ListSessionsCommand.class;
            case "agents" -> ListAgentsCommand.class;
            case "audit-log" -> AuditLogCommand.class;
            case "edit-reltable" -> EditReltableCommand.class;
            case "rename-file" -> RenameFileCommand.class;
            case "rename-key" -> RenameKeyCommand.class;
            case "delete-file" -> DeleteFileCommand.class;
            case "retarget" -> RetargetCommand.class;
            case "keyify" -> KeyifyCommand.class;
            case "inline-key" -> InlineKeyCommand.class;
            case "extract-conref" -> ExtractConrefCommand.class;
            case "inline-conref" -> InlineConrefCommand.class;
            case "rename-element-id" -> RenameElementIdCommand.class;
            case "merge-keydefs" -> MergeKeydefsCommand.class;
            case "split-topic" -> SplitTopicCommand.class;
            case "rename-profile-value" -> RenameProfileValueCommand.class;
            case "create-keydef" -> CreateKeydefCommand.class;
            case "getSelection" -> GetSelectionCommand.class;
            case "replaceSelection" -> ReplaceSelectionCommand.class;
            case "gotoLine" -> GotoLineCommand.class;
            case "getCursor" -> GetCursorCommand.class;
            case "setCursor" -> SetCursorCommand.class;
            case "selectElement" -> SelectElementCommand.class;
            case "open" -> OpenCommand.class;
            case "close" -> CloseCommand.class;
            case "save" -> SaveCommand.class;
            case "listDocuments" -> ListDocumentsCommand.class;
            case "getContent" -> GetContentCommand.class;
            case "setContent" -> SetContentCommand.class;
            case "getOutline" -> GetOutlineCommand.class;
            case "authorSwitch" -> AuthorSwitchCommand.class;
            case "authorOutline" -> AuthorOutlineCommand.class;
            case "authorInsertBlock" -> AuthorInsertBlockCommand.class;
            case "authorSetText" -> AuthorSetTextCommand.class;
            case "authorIssues" -> AuthorIssuesCommand.class;
            case "screenshot" -> ScreenshotCommand.class;
            case "getErrors" -> GetErrorsCommand.class;
            case "search" -> SearchProjectCommand.class;
            case "listFiles" -> ListProjectFilesCommand.class;
            case "createProject" -> CreateProjectCommand.class;
            case "openProject" -> OpenProjectCommand.class;
            case "listProjects" -> ListProjectsCommand.class;
            case "getProject" -> GetProjectCommand.class;
            case "newDocument" -> NewDocumentCommand.class;
            case "listSidebars" -> ListSidebarsCommand.class;
            case "switchSidebar" -> SwitchSidebarCommand.class;
            case "openDitaMap" -> OpenDitaMapCommand.class;
            default -> null;
        };
    }

    /** True when the method's command is a {@link ReadOnlyCommand}. */
    public static boolean isReadOnly(String method) {
        Class<?> c = commandClassOf(method);
        return c != null && ReadOnlyCommand.class.isAssignableFrom(c);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * A required string parameter, reported as an argument error when absent.
     *
     * <p>{@code params.get(name).asText()} NPEs on a missing key, which surfaces
     * to the caller as an internal error even though the tool schema declares the
     * parameter required.
     *
     * @param params the request parameters
     * @param name the parameter name
     * @return its value
     * @throws CommandException if the parameter is missing or null
     */
    private String requiredString(JsonNode params, String name) throws CommandException {
        if (!params.has(name) || params.get(name).isNull()) {
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "Missing required parameter: " + name
            );
        }
        return params.get(name).asText();
    }

    private Path pathParam(JsonNode params, String name) throws CommandException {
        if (!params.has(name) || params.get(name).isNull()) {
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "Missing required parameter: " + name
            );
        }
        return Path.of(params.get(name).asText());
    }

    private Path optionalPathParam(JsonNode params, String name) {
        if (!params.has(name) || params.get(name).isNull()) return null;
        return Path.of(params.get(name).asText());
    }

    /** A report source's arguments: a JSON object, or the same as a JSON string (the CLI's form). */
    private static ObjectNode sourceArgs(JsonNode args) throws CommandException {
        if (args == null || args.isNull()) {
            return mapper.createObjectNode();
        }
        if (args.isObject()) {
            return ((ObjectNode) args).deepCopy();
        }
        try {
            JsonNode parsed = mapper.readTree(args.asText());
            if (parsed != null && parsed.isObject()) {
                return (ObjectNode) parsed;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // reported below
        }
        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "args must be a JSON object of the source tool's arguments");
    }

    /** The built-in template made for a source's output, or null when there is none. */
    static String defaultTemplateFor(String method) {
        return switch (method) {
            case "project-graph" -> "relationship-map";
            case "project-health" -> "health";
            default -> null;
        };
    }

    /** A list parameter given as a JSON array or a comma-separated string; null when absent. */
    static java.util.List<String> stringList(JsonNode params, String name) {
        JsonNode node = params.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> out.add(n.asText()));
        } else {
            for (String part : node.asText().split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private String optionalString(JsonNode params, String name) {
        if (!params.has(name) || params.get(name).isNull()) return null;
        return params.get(name).asText();
    }

    private List<Path> pathListParam(JsonNode params, String name) {
        List<Path> result = new ArrayList<>();
        if (params.has(name) && params.get(name).isArray()) {
            for (JsonNode node : params.get(name)) {
                result.add(Path.of(node.asText()));
            }
        }
        return result;
    }

    private String formatResult(JsonNode id, Object result) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) response.set("id", id);
            response.set("result", mapper.valueToTree(result));
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return formatError(id, -32603, "Serialization error: " + e.getMessage());
        }
    }

    private String formatError(JsonNode id, int code, String message) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) response.set("id", id);
            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    private int mapErrorCode(CommandException.ErrorCode code) {
        return switch (code) {
            case FILE_NOT_FOUND -> -32001;
            case PARSE_ERROR -> -32002;
            case VALIDATION_ERROR -> -32003;
            case SCHEMA_NOT_FOUND -> -32004;
            case XPATH_ERROR -> -32005;
            case TRANSFORM_ERROR -> -32006;
            case EDITOR_NOT_RUNNING -> -32007;
            case DOCUMENT_NOT_OPEN -> -32008;
            case INVALID_ARGUMENT -> -32602;
            case PERMISSION_DENIED -> -32009;
            case CONFLICT -> -32010;
            case LOCKED -> -32011;
            case INTERNAL_ERROR -> -32603;
        };
    }
}
