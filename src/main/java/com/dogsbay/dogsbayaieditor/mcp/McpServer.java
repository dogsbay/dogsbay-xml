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

package com.dogsbay.dogsbayaieditor.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;

/**
 * MCP (Model Context Protocol) server implementation.
 *
 * Implements the MCP protocol over HTTP (Streamable HTTP transport):
 * - POST /mcp — JSON-RPC 2.0 requests
 *
 * MCP methods handled:
 * - initialize — handshake, returns server capabilities
 * - notifications/initialized — client acknowledgment (no response)
 * - tools/list — enumerate available tools
 * - tools/call — execute a tool
 *
 * Tools map directly to Command Engine commands via JsonRpcHandler.
 */
public class McpServer implements HttpHandler {

    /** Streamable-HTTP session header (MCP 2025-03-26). */
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final ObjectMapper mapper = new ObjectMapper();
    private final JsonRpcHandler rpcHandler;
    private final List<McpTool> tools;
    private final AgentSessionRegistry sessions;
    /** Sessions this server opened, closed together on {@link #close()}. */
    private final java.util.Set<String> owned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile AgentSession legacyClient;

    /** External MCP sessions unseen for this long are closed on the next initialize. */
    static final java.time.Duration IDLE = java.time.Duration.ofHours(4);
    /** A session that only ever initialized (its client never echoed the id) is dropped after this. */
    static final java.time.Duration UNUSED = java.time.Duration.ofMinutes(10);

    /** One HTTP-level response: status plus JSON body ({@code null} body = 202, no content). */
    public record Response(int status, String body) {}

    /** A server with a private, throwaway session registry (catalog and tests). */
    public McpServer(CommandExecutor executor) {
        this(executor, new AgentSessionRegistry());
    }

    /**
     * @param sessions where MCP clients are registered, so every tool call is
     *                 attributed to the client that made it
     */
    public McpServer(CommandExecutor executor, AgentSessionRegistry sessions) {
        this.rpcHandler = new JsonRpcHandler(executor);
        this.tools = buildTools();
        this.sessions = sessions;
    }

    /**
     * The tool catalog (name, description, rpcMethod, JSON schema). Exposed so
     * other command→tool bridges (e.g. the in-editor agent's CommandAgentTool)
     * can reuse the same definitions instead of duplicating them.
     */
    public List<McpTool> getTools() {
        return tools;
    }

    /** Execute a tool's underlying command by rpcMethod (same path as tools/call). */
    public Object dispatch(String rpcMethod, JsonNode args)
            throws com.dogsbay.dogsbayaieditor.commands.CommandException {
        return rpcHandler.dispatch(rpcMethod, args);
    }

    /** As {@link #dispatch(String, JsonNode)}, attributed to {@code session}. */
    public Object dispatch(String rpcMethod, JsonNode args, AgentSession session)
            throws com.dogsbay.dogsbayaieditor.commands.CommandException {
        return rpcHandler.dispatch(rpcMethod, args, session);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        Optional<AgentSession> auth = EditorHttpServer.sessionOf(exchange);
        if ("DELETE".equals(method)) {
            Response r = handleDelete(exchange.getRequestHeaders().getFirst(SESSION_HEADER), auth);
            exchange.sendResponseHeaders(r.status(), -1);
            exchange.getResponseBody().close();
            return;
        }
        if (!"POST".equals(method)) {
            sendJson(exchange, 405, """
                {"error":"Method not allowed. Use POST."}""");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Response r = handleRequest(body, exchange.getRequestHeaders(), exchange.getResponseHeaders(), auth);

        if (r.body() != null) {
            sendJson(exchange, r.status(), r.body());
        } else {
            exchange.sendResponseHeaders(202, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * Streamable HTTP: the client ends its session explicitly. Only the
     * session's own token, or the master token (no auth session), may do so;
     * a per-session caller naming another session gets 403.
     */
    public Response handleDelete(String id, Optional<AgentSession> auth) {
        if (id == null) {
            return new Response(400, null);
        }
        if (auth.isPresent() && !auth.get().id().equals(id)) {
            return new Response(403, null);
        }
        boolean closed = sessions.get(id)
                .filter(x -> x.kind() == SessionKind.EXTERNAL_MCP)
                .flatMap(x -> sessions.close(x.id())).isPresent();
        owned.remove(id);
        return new Response(closed ? 200 : 404, null);
    }

    /**
     * The transport-independent core of {@link #handle}: one JSON-RPC request
     * in, one response out ({@code null} body for a notification).
     *
     * <p>Session resolution, in order: the session a per-session bearer token
     * authenticated as ({@code authSession}); the {@value #SESSION_HEADER}
     * header a client echoes after {@code initialize}; on {@code initialize}
     * with neither, a new {@link SessionKind#EXTERNAL_MCP} session named from
     * {@code clientInfo} whose id is returned in {@value #SESSION_HEADER}; and
     * for any other method with neither, one shared session for clients that
     * never send the header. A header naming a session that is unknown or
     * closed is answered with 404, as the MCP Streamable HTTP transport
     * requires, so the client re-initializes instead of being misattributed.
     */
    public Response handleRequest(String body, Headers requestHeaders, Headers responseHeaders,
            Optional<AgentSession> authSession) {
        try {
            JsonNode request = mapper.readTree(body);
            String method = request.has("method") ? request.get("method").asText() : "";
            JsonNode id = request.get("id");
            JsonNode params = request.get("params");

            Optional<AgentSession> resolved = resolveSession(method, params, requestHeaders, authSession);
            if (resolved.isEmpty()) {
                return new Response(404, formatError(id, -32001,
                        "Unknown or expired " + SESSION_HEADER + "; send initialize again"));
            }
            AgentSession session = resolved.get();
            if (!"initialize".equals(method)) {
                sessions.touch(session.id());
            }
            if (session.kind() == SessionKind.EXTERNAL_MCP && session != legacyClient) {
                responseHeaders.set(SESSION_HEADER, session.id());
            }

            String result = switch (method) {
                case "initialize" -> handleInitialize(id);
                case "notifications/initialized" -> null; // notification, no response
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, params, session);
                case "ping" -> handlePing(id);
                default -> formatError(id, -32601, "Method not found: " + method);
            };
            return new Response(result == null ? 202 : 200, result);
        } catch (Exception e) {
            return new Response(200, formatError(null, -32603, e.getMessage()));
        }
    }

    /** Close every session this server opened. Called when the endpoint stops. */
    public void close() {
        for (String id : List.copyOf(owned)) {
            sessions.close(id);
        }
        owned.clear();
        legacyClient = null;
    }

    private Optional<AgentSession> resolveSession(String method, JsonNode params, Headers headers,
            Optional<AgentSession> authSession) {
        if (authSession.isPresent()) {
            return authSession;
        }
        String headerId = headers == null ? null : headers.getFirst(SESSION_HEADER);
        if (headerId != null) {
            return sessions.get(headerId).filter(x -> x.kind() == SessionKind.EXTERNAL_MCP);
        }
        if ("initialize".equals(method)) {
            for (AgentSession idle : sessions.closeIdle(SessionKind.EXTERNAL_MCP, IDLE)) {
                owned.remove(idle.id());
            }
            for (AgentSession unused : sessions.closeUntouched(SessionKind.EXTERNAL_MCP, UNUSED)) {
                owned.remove(unused.id());
            }
            String client = clientName(params);
            return Optional.of(open(client, "mcp:" + slug(client)));
        }
        return Optional.of(legacyClient());
    }

    private AgentSession open(String displayName, String identity) {
        AgentSession s = sessions.open(SessionKind.EXTERNAL_MCP, displayName, identity,
                CapabilityTier.T1_COMMANDS);
        owned.add(s.id());
        return s;
    }

    /** One shared session for clients that never echo the session header. */
    private AgentSession legacyClient() {
        AgentSession s = legacyClient;
        if (s == null || sessions.get(s.id()).isEmpty()) {
            synchronized (this) {
                s = legacyClient;
                if (s == null || sessions.get(s.id()).isEmpty()) {
                    s = open("MCP client", "mcp:unknown");
                    legacyClient = s;
                }
            }
        }
        return s;
    }

    private static String clientName(JsonNode params) {
        JsonNode info = params == null ? null : params.get("clientInfo");
        String name = info != null && info.hasNonNull("name") ? info.get("name").asText().trim() : "";
        return name.isEmpty() ? "MCP client" : name;
    }

    static String slug(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "unknown" : s;
    }

    // ── MCP Methods ─────────────────────────────────────────────────────

    private String handleInitialize(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode toolsCap = capabilities.putObject("tools");
        toolsCap.put("listChanged", false);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "dogsbay-editor");
        serverInfo.put("version",
            com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion());
        // With a hundred tools listed flat, name the entry points (MCP 2025 "instructions").
        result.put("instructions", com.dogsbay.agent.AgentGuidance.mcpInstructions());

        return formatResult(id, result);
    }

    private String handlePing(JsonNode id) {
        return formatResult(id, mapper.createObjectNode());
    }

    private String handleToolsList(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsArray = result.putArray("tools");

        for (McpTool tool : tools) {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", tool.inputSchema());
            // MCP tool annotations: clients use readOnlyHint to skip approval prompts.
            toolNode.putObject("annotations")
                    .put("readOnlyHint", JsonRpcHandler.isReadOnly(tool.rpcMethod()));
        }

        return formatResult(id, result);
    }

    private String handleToolsCall(JsonNode id, JsonNode params, AgentSession session) {
        if (params == null || !params.has("name")) {
            return formatError(id, -32602, "Missing tool name");
        }

        String toolName = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        // Find the tool
        McpTool tool = tools.stream()
            .filter(t -> t.name().equals(toolName))
            .findFirst()
            .orElse(null);

        if (tool == null) {
            return formatError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            // Dispatch to the Command Engine via JsonRpcHandler
            Object cmdResult = rpcHandler.dispatch(tool.rpcMethod(), arguments, session);
            String content = mapper.writeValueAsString(cmdResult);

            ObjectNode result = mapper.createObjectNode();
            ArrayNode contentArray = result.putArray("content");
            ObjectNode textContent = contentArray.addObject();
            textContent.put("type", "text");
            textContent.put("text", content);
            result.put("isError", false);

            return formatResult(id, result);
        } catch (Exception e) {
            ObjectNode result = mapper.createObjectNode();
            ArrayNode contentArray = result.putArray("content");
            ObjectNode textContent = contentArray.addObject();
            textContent.put("type", "text");
            textContent.put("text", "Error: " + e.getMessage());
            result.put("isError", true);

            return formatResult(id, result);
        }
    }

    // ── JSON-RPC Formatting ─────────────────────────────────────────────

    private String formatResult(JsonNode id, JsonNode result) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) response.set("id", id);
            response.set("result", result);
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return formatError(id, -32603, "Serialization error");
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

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── Tool Definitions ────────────────────────────────────────────────

    private List<McpTool> buildTools() {
        List<McpTool> list = new ArrayList<>();

        list.add(new McpTool("validate_document",
            "Validate an XML document against its schema: XSD, DTD or RelaxNG. A DITA topic or map "
            + "with no schema or catalog given is validated against the bundled DITA DTDs, so the "
            + "result covers structure, not only well-formedness. This is grammar validation of one "
            + "file only: for references, keys, element ids, metadata and (with schematron) house "
            + "rules across the project or a map, run project_health. "
            + "Pass catalog files only for non-DITA DOCTYPEs.",
            "validate", schema(
                prop("file", "string", "Absolute path to XML file", true),
                prop("schema", "string", "Schema file path (XSD/DTD/RNG). Omit for DITA: the bundled DTDs are used", false),
                prop("catalogs", "array", "List of XML catalog file paths for entity resolution", false)
            )));

        list.add(new McpTool("parse_document",
            "Parse an XML document and return root element, namespace, and encoding.",
            "parse", schema(
                prop("file", "string", "Absolute path to XML file", true)
            )));

        list.add(new McpTool("document_info",
            "Show document metadata: encoding, grammar type, root element, size, line count.",
            "info", schema(
                prop("file", "string", "Absolute path to XML file", true)
            )));

        list.add(new McpTool("format_document",
            "Pretty-print an XML document with configurable indentation.",
            "format", schema(
                prop("file", "string", "Absolute path to XML file", true),
                prop("indent", "integer", "Indentation spaces (default: 2)", false),
                prop("output", "string", "Output file path (returns content if omitted)", false)
            )));

        list.add(new McpTool("xpath_query",
            "Run an XPath query across one or more XML files. Supports glob patterns.",
            "query", schema(
                prop("file", "string", "File path or glob pattern (e.g., 'docs/**/*.xml')", true),
                prop("xpath", "string", "XPath 1.0 expression", true)
            )));

        list.add(new McpTool("transform_xslt",
            "Apply an XSLT stylesheet to an XML document.",
            "transform", schema(
                prop("input", "string", "Input XML file path", true),
                prop("xslt", "string", "XSLT stylesheet path", true),
                prop("output", "string", "Output file path (returns content if omitted)", false)
            )));

        list.add(new McpTool("where_used",
            "Find every reference to a file across a project: map topicrefs, "
            + "conrefs, links, images — including indirect references via keys "
            + "when a root map is given. Essential before renaming or moving a topic. "
            + "For the whole project's structure in one call, use project_graph.",
            "where-used", schema(
                prop("file", "string", "The target file path", true),
                prop("root", "string", "Project root directory to scan", true),
                prop("map", "string", "Root map for via-key references", false)
            )));

        list.add(new McpTool("list_keys",
            "List the effective key space of a DITA root map (including submaps): "
            + "key name, defining map and line, href, resolved target, text.",
            "list-keys", schema(
                prop("map", "string", "Root map file path", true),
                prop("ditaval", "string", "DITAVAL conditioning the key space", false)
            )));

        list.add(new McpTool("resolve_key",
            "Resolve one key against a DITA root map: where it's defined, what "
            + "href/text it carries, the file it points at, and its key scope. "
            + "Pass scope to resolve from inside a @keyscope (innermost-first, then "
            + "outward); a key can also be referenced scope-qualified as scope.key.",
            "resolve-key", schema(
                prop("key", "string", "The key name (bare or scope-qualified)", true),
                prop("map", "string", "Root map file path", true),
                prop("ditaval", "string", "DITAVAL conditioning the key space", false),
                prop("scope", "string", "Key-scope context to resolve from (dotted path)",
                    false)
            )));

        list.add(new McpTool("check_links",
            "Report broken references across a project: path references whose "
            + "target file doesn't exist, and undefined keys when a root map "
            + "is given. Empty result means the references are clean. project_health includes this "
            + "check; use check_links to re-check references alone after a fix.",
            "check-links", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("map", "string", "Root map for key validation", false)
            )));

        list.add(new McpTool("project_health",
            "START HERE for any validation or audit. A superset of check_links (broken "
            + "references, undefined/unused keys, orphan topics), validate_project (DTD/grammar "
            + "validation of the scope: the map's publication set when a map is given), "
            + "conref_audit (reuse refs whose target file exists but #fragment id is missing) "
            + "and metadata_audit, in one call. "
            + "'clean' is true only when everything that ran passes — use it as a done-gate; "
            + "'checked' lists what ran. Pass 'schematron' to include the project's house rules "
            + "(shortdesc required, no hardcoded product name, …), which grammar validation "
            + "cannot express and which are otherwise not checked here. Metadata and Schematron "
            + "findings come grouped by rule with counts (metadataRules, schematronRules).",
            "project-health", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("map", "string", "Root map — enables key analysis and scopes "
                    + "validation to the publication set", false),
                prop("schematron", "string", "Schematron schema (.sch) of house rules "
                    + "to apply across the same scope", false),
                prop("include", "string", "Comma-separated legs to run, default all: "
                    + "reuse, validation, elementIds, metadata, schematron, proposals", false),
                prop("severity", "string", "'error' keeps only findings that block clean "
                    + "(drops unused keys, orphans, warnings and recommended metadata)", false),
                prop("group", "boolean", "Group metadata and Schematron findings by rule "
                    + "(default true); false lists every finding", false)
            )));

        list.add(new McpTool("schematron_project",
            "Run a Schematron schema (.sch) across a scope on disk — business/style "
            + "rules that DTD/XSD/RelaxNG can't express (e.g. 'every <step> has a "
            + "<cmd>', 'no hardcoded product name'). Returns counts plus the failed "
            + "assertions (file, location, message). Empty result means all rules pass.",
            "schematron-project", schema(
                prop("root", "string", "Project root directory", true),
                prop("schema", "string", "Path to the .sch Schematron schema", true),
                prop("map", "string", "Root map — apply to its publication set", false),
                prop("scope", "string",
                    "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides 'map'; default 'root')", false)
            )));

        list.add(new McpTool("schematron",
            "Apply a Schematron schema (.sch) to a single document — the business/style "
            + "rules DTD/XSD/RelaxNG can't express. Same engine and results as "
            + "schematron_project; use this when you only care about one file.",
            "schematron", schema(
                prop("file", "string", "The document to check", true),
                prop("schema", "string", "Path to the .sch Schematron schema", true)
            )));

        list.add(new McpTool("review_list",
            "List the review proposals in a DITA document: the insertions, deletions and comments agents "
            + "have made, with their author and position. Your own edits appear here after "
            + "set_document_content; the writer accepts or rejects them.",
            "review-list", schema(
                prop("file", "string", "The document", true),
                prop("author", "string", "Only this author's proposals (an id such as ai:claude-acp)", false)
            )));

        list.add(new McpTool("review_comment",
            "Leave a review comment in a DITA document without changing its text: a recommendation "
            + "for the writer, attributed to you. Say where it goes: after the first occurrence of some "
            + "text, or as the first child of the element with an id.",
            "review-comment", schema(
                prop("file", "string", "The document", true),
                prop("text", "string", "The comment", true),
                prop("afterText", "string", "Place the comment right after this text", false),
                prop("elementId", "string", "Or place it as the first child of this element", false)
            )));

        list.add(new McpTool("list_agent_sessions",
            "List the agent sessions connected to this editor: who else is working here, and as what.",
            "sessions", schema()));

        list.add(new McpTool("agent_audit_log",
            "What agents did in the project, newest first: command, identity, files, dry run, outcome. "
            + "Read-only.",
            "audit-log", schema(
                prop("root", "string", "Project root (default: the open project)", false),
                prop("identity", "string", "Only this identity, e.g. ai:claude-acp", false),
                prop("command", "string", "Only this command, e.g. set-content", false),
                prop("limit", "integer", "At most this many entries (default 50; 0 for all)", false)
            )));

        list.add(new McpTool("validate_deliverables",
            "Validate every deliverable of the project (from a project.{xml,json,yaml} "
            + "file, else a synthesized default) — each deliverable's map publication "
            + "set, on disk, each file validated once. Returns per-deliverable counts (name, map, "
            + "total, passed, failed) and each finding once with the deliverables it breaks "
            + "(capped at 200; truncated says how many more), so you can see which "
            + "audiences/outputs are affected. Grammar only: "
            + "for references, keys and reuse per deliverable use project_graph, or project_health "
            + "with that deliverable's map.",
            "validate-deliverables", schema(
                prop("root", "string", "Project root directory", true)
            )));

        list.add(new McpTool("validate_deep",
            "Deep-validate by running DITA-OT preprocessing (the engine tier): runs "
            + "the real preprocessing pipeline on a deliverable's map, applying its "
            + "DITAVAL, so it catches keyref/conref resolution failures, circular "
            + "maprefs, and filtered-content errors that validate_project cannot. "
            + "Returns one result per deliverable (name, map, ditaval, success, "
            + "diagnostics). Requires DITA-OT installed; slower than validate_project. "
            + "Complements project_health, which checks the sources on disk without DITA-OT.",
            "validate-deep", schema(
                prop("root", "string", "Project root directory", true),
                prop("deliverable", "string",
                    "Deliverable name to validate (all deliverables if omitted)", false),
                prop("map", "string",
                    "A specific map to validate (overrides 'deliverable'); uses the "
                    + "DITAVAL of the deliverable that ships it, if any", false)
            )));

        list.add(new McpTool("build_deliverables",
            "Build a DITA project's deliverables with DITA-OT — each with its transtype, "
            + "DITAVAL filter(s), publication params, and output directory. Builds one "
            + "named deliverable or all. Returns one result per deliverable (name, "
            + "transtype, outputDir, success, diagnostics, and tempDir when temporary files "
            + "were kept). Requires DITA-OT; this runs full publishing, so it is slower than "
            + "validation. To troubleshoot what DITA-OT resolved (conrefs, keys, filtering), "
            + "build with keepTemp and read the preprocessed XML in tempDir.",
            "build-deliverables", schema(
                prop("root", "string", "Project root directory", true),
                prop("output", "string",
                    "Base output directory (each deliverable under a subdir); "
                    + "overrides the project's output", false),
                prop("deliverable", "string",
                    "Deliverable name to build (all deliverables if omitted)", false),
                prop("keepTemp", "boolean",
                    "Keep DITA-OT's temporary files in <root>/.dogsbay/temp/<deliverable> "
                    + "(true), or discard them (false); omitted follows each deliverable's "
                    + "clean.temp param", false)
            )));
        list.add(new McpTool("validate_conditions",
            "Scan a DITA project or deliverable for profiling-attribute values "
            + "(platform, audience, product, otherprops, …) that violate a subjectScheme's "
            + "controlled values — e.g. platform=\"macos\" when the scheme only allows \"mac\". "
            + "Checks both element attributes and DITAVAL <prop val> entries; returns file, "
            + "line, attribute, value and a near-miss suggestion. Catches typos and "
            + "drift in conditional filters. No scheme ⇒ no findings.",
            "validate-conditions", schema(
                prop("root", "string", "Project root directory", true),
                prop("subjectScheme", "string",
                    "Subject scheme .ditamap (omit to discover from the map's closure)", false),
                prop("map", "string",
                    "Root map — scan its publication set and discover its scheme", false),
                prop("scope", "string",
                    "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides 'map'; default 'root')", false)
            )));
        list.add(new McpTool("list_subjects",
            "List the controlled vocabulary from a subjectScheme (.ditamap): each governed "
            + "attribute (e.g. platform, audience) and its allowed values. Use before "
            + "editing conditions to see the legal set.",
            "list-subjects", schema(
                prop("subjectScheme", "string", "Subject scheme .ditamap file path", true)
            )));
        list.add(new McpTool("metadata_audit",
            "Audit topics/maps against the project's required-metadata policy (from "
            + ".dogsbay/config.xml, or an explicit policy file). Reports per file the "
            + "missing-required, forbidden, and out-of-vocabulary metadata (prolog/"
            + "topicmeta: author, audience, category, keywords, prodname, critdates, …). "
            + "No policy ⇒ no findings. Included in project_health; use this to re-check "
            + "metadata alone after a fix.",
            "metadata-audit", schema(
                prop("root", "string", "Project root directory", true),
                prop("map", "string", "Root map — audit its publication set", false),
                prop("scope", "string",
                    "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides 'map'; default 'root')", false),
                prop("policy", "string",
                    "Explicit policy .xml file (overrides the project config)", false)
            )));
        list.add(new McpTool("metadata_export_schematron",
            "Compile the project's required-metadata policy to ISO Schematron and return "
            + "it (writing to 'output' if given) — portable validation for CI / DITA-OT / "
            + "oXygen. The in-tool audit uses metadata_audit; this is for external pipelines.",
            "metadata-export-schematron", schema(
                prop("root", "string", "Project root directory", true),
                prop("policy", "string",
                    "Explicit policy .xml file (overrides the project config)", false),
                prop("output", "string", "Path to write the .sch to (optional)", false)
            )));
        list.add(new McpTool("metadata_set",
            "Bulk-set one metadata field across a scope, field-preserving (other prolog "
            + "content + formatting untouched). mode: set (overwrite) | fill (only if "
            + "absent) | append (add to a list like keyword) | remove. field is the "
            + "element name (audience, category, keyword, created, author, …); name/content "
            + "fields take 'name=value'. Topics (.dita) only. Use dryRun to preview.",
            "metadata-set", schema(
                prop("root", "string", "Project root directory", true),
                prop("field", "string", "Metadata field element name (e.g. audience)", true),
                prop("value", "string", "The value (or 'name=value' for othermeta/data)", false),
                prop("mode", "string", "set | fill | append | remove (default fill)", false),
                prop("map", "string", "Root map — apply to its publication set", false),
                prop("scope", "string",
                    "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root'", false),
                prop("dryRun", "boolean", "Report changes without writing", false)
            )));

        list.add(new McpTool("edit_map",
            "Structurally edit a DITA map, reference-safe and formatting-preserving. "
            + "op: set-attr (set/remove attribute 'name' on 'ref'; empty value removes) | "
            + "insert (add 'type' element, default topicref, under 'parent' at 'index' with "
            + "href/navtitle) | remove ('ref' + subtree; warns on inbound refs) | move "
            + "(reparent/reorder 'ref' under 'parent' at 'index'; 'toMap' moves cross-map, "
            + "rebasing hrefs). Topicrefs are addressed by @id or a child-path like /1/3 "
            + "(/ = root). Use dryRun to preview the plan + warnings.",
            "edit-map", schema(
                prop("map", "string", "Path to the .ditamap", true),
                prop("op", "string", "set-attr | insert | remove | move", true),
                prop("ref", "string", "Target selector (@id or /1/3) for set-attr/remove/move",
                    false),
                prop("parent", "string", "Parent selector for insert/move (/ = root)", false),
                prop("index", "integer", "Child index for insert/move (default: append)", false),
                prop("type", "string", "Element type for insert (default topicref)", false),
                prop("name", "string", "Attribute name for set-attr", false),
                prop("value", "string", "Attribute value for set-attr (empty = remove)", false),
                prop("href", "string", "@href for insert", false),
                prop("navtitle", "string", "@navtitle for insert", false),
                prop("toMap", "string", "Destination map for a cross-map move", false),
                prop("dryRun", "boolean", "Report the plan without writing", false)
            )));

        list.add(new McpTool("reltable_audit",
            "Validate a map's relationship tables and preview the related-links they "
            + "generate. Checks each cell topicref: it resolves (href exists / keyref "
            + "resolves), targets a topic not a map, and (soft) its type matches the "
            + "column; flags degenerate rows that link nothing. Returns issues + the "
            + "projected per-topic links.",
            "reltable-audit", schema(
                prop("map", "string", "Path to the .ditamap", true)
            )));

        list.add(new McpTool("edit_reltable",
            "Edit a map's relationship table, formatting-preserving. Tables/rows/cells "
            + "are 0-based. op: create-table (columns = comma-separated types, e.g. "
            + "concept,task,reference) | add-row | remove-row | add-target (href or "
            + "keyref into cell row,col) | remove-target (index in cell row,col) | "
            + "set-attr (name/value on the table, a row, or a cell). dryRun to preview.",
            "edit-reltable", schema(
                prop("map", "string", "Path to the .ditamap", true),
                prop("op", "string", "create-table | add-row | remove-row | add-target | "
                    + "remove-target | set-attr", true),
                prop("table", "integer", "0-based reltable index (default 0)", false),
                prop("row", "integer", "Row index", false),
                prop("col", "integer", "Column index", false),
                prop("index", "integer", "Target index (remove-target)", false),
                prop("href", "string", "@href for add-target", false),
                prop("keyref", "string", "@keyref for add-target", false),
                prop("navtitle", "string", "@navtitle for add-target", false),
                prop("name", "string", "Attribute name for set-attr", false),
                prop("value", "string", "Attribute value for set-attr (empty = remove)", false),
                prop("columns", "string", "Comma-separated column types for create-table", false),
                prop("dryRun", "boolean", "Report the plan without writing", false)
            )));

        list.add(new McpTool("keyword_audit",
            "Survey the <keyword> vocabulary across a project: distinct keywords with "
            + "frequencies, topics with none, near-duplicate spellings (drift), and the "
            + "keyword co-occurrence signal — topic pairs that share keywords, the "
            + "deterministic relatedness hint for relationship tables / related-links. "
            + "Topics only. Pair with the dita-keywords/dita-reltables skills.",
            "keyword-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("scope", "string",
                    "Scope: 'map:<path>', 'glob:<pattern>', or 'root'", false)
            )));

        list.add(new McpTool("index_audit",
            "Survey the DITA 1.3 index (<indexterm>): the entry inventory with topic "
            + "frequencies (nested 'primary > secondary' paths), topics carrying no index "
            + "terms (coverage gaps), and dangling <index-see>/<index-see-also> redirects "
            + "whose target is no real index entry. Topics only.",
            "index-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("scope", "string",
                    "Scope: 'map:<path>', 'glob:<pattern>', or 'root'", false)
            )));

        list.add(new McpTool("glossary_audit",
            "Survey the DITA 1.3 glossary (<glossentry>): the term inventory (id + "
            + "glossterm + surface forms), <abbreviated-form>/<term> references whose key "
            + "doesn't resolve to a glossary entry (undefined), and glossentries nothing "
            + "references (unused). Pass rootMap to resolve keys; without it, inventory only.",
            "glossary-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("scope", "string",
                    "Scope: 'map:<path>', 'glob:<pattern>', or 'root'", false),
                prop("rootMap", "string",
                    "Root map whose key space resolves keyrefs (for undefined/unused)", false)
            )));

        list.add(new McpTool("specialization_info",
            "Report a DITA file's specialization: its DOCTYPE (public/system id), root "
            + "element, the @class generalization chain (general→specific, e.g. "
            + "topic/topic → concept/concept), and the @domains it integrates. Confirms a "
            + "team's custom (or standard) specialization is understood.",
            "specialization-info", schema(
                prop("file", "string", "The DITA file to inspect", true)
            )));

        list.add(new McpTool("chunk_audit",
            "Audit DITA 1.3 chunking (@chunk) across a project's maps: the inventory of "
            + "@chunk usages and validation of token values against the DITA 1.3 vocabulary "
            + "(select-branch/-document/-topic, by-topic/-document, to-content/-navigation) "
            + "— flags unknown tokens and conflicting combinations. Maps only.",
            "chunk-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("scope", "string",
                    "Scope: 'map:<path>', 'glob:<pattern>', or 'root'", false)
            )));

        list.add(new McpTool("conref_push_audit",
            "Audit DITA 1.3 conref push (@conaction) across a project: the inventory of "
            + "mark / pushbefore / pushafter / pushreplace operations, broken sibling "
            + "pairing (a push with no mark, a mark with nothing to push, a pushreplace "
            + "with no target), and push targets (@conref/@conkeyref) that don't resolve. "
            + "Pass rootMap to resolve conkeyref keys.",
            "conref-push-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("scope", "string",
                    "Scope: 'map:<path>', 'glob:<pattern>', or 'root'", false),
                prop("rootMap", "string",
                    "Root map whose key space resolves conkeyref keys", false)
            )));

        list.add(new McpTool("list_branches",
            "Enumerate a map's DITA 1.3 branch-filter variants (each <ditavalref>): the "
            + "topicref it filters, its DITAVAL, and the keyscope DITA-OT generates. The "
            + "variant inventory — the engine performs the actual duplication/build.",
            "list-branches", schema(
                prop("map", "string", "The .ditamap to enumerate", true)
            )));

        list.add(new McpTool("conref_audit",
            "Audit content reuse for broken element ids: conref/conkeyref/keyref/href "
            + "whose target FILE exists but whose #fragment names an id the target "
            + "doesn't contain (e.g. a typo'd conref id). Complements check_links "
            + "(which only checks the file exists). Empty result means clean. Included in "
            + "project_health; use this to re-check element ids alone after a fix.",
            "conref-audit", schema(
                prop("root", "string", "Project root directory to scan", true),
                prop("map", "string", "Root map — resolves keyref/conkeyref targets", false)
            )));

        list.add(new McpTool("validate_project",
            "Validate every file in a scope on disk, without opening documents — "
            + "the project/map-wide validate. DITA topics/maps use the bundled DITA "
            + "catalog automatically. Returns counts (total/passed/failed) plus the "
            + "failing files with their errors (capped); failed=0 means all valid. "
            + "Prefer 'map' (the publication set) for a DITA project. Included in project_health, "
            + "which also checks references, keys, element ids and metadata; use this to re-check "
            + "validation alone after a fix.",
            "validate-project", schema(
                prop("root", "string", "Project root directory", true),
                prop("map", "string", "Root map — validates its publication set", false),
                prop("scope", "string",
                    "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides 'map'; default 'root')", false)
            )));

        list.add(new McpTool("rename_file",
            "Rename/move a file and rewrite every reference to it (maps, "
            + "conrefs, links, images). DRY-RUN by default: returns the full "
            + "edit plan. Call again with apply=true to execute. Always "
            + "review the plan before applying.",
            "rename-file", schema(
                prop("file", "string", "The file to rename/move", true),
                prop("newPath", "string", "The destination path", true),
                prop("root", "string", "Project root whose references are rewritten", true),
                prop("apply", "boolean", "Execute the refactor (default false = plan only)", false)
            )));

        list.add(new McpTool("rename_key",
            "Rename a DITA key: all keydef definitions (every map under the "
            + "root) and every keyref/conkeyref, element-id segments "
            + "preserved. DRY-RUN by default: returns the full edit plan. "
            + "Call again with apply=true to execute. Always review the plan "
            + "before applying.",
            "rename-key", schema(
                prop("oldKey", "string", "The key to rename", true),
                prop("newKey", "string", "The new key name", true),
                prop("root", "string", "Project root to scan and rewrite", true),
                prop("apply", "boolean", "Execute the refactor (default false = plan only)", false)
            )));

        list.add(new McpTool("delete_file",
            "Safe delete: reports every inbound reference to the file. With "
            + "removeRefs=true also removes referencing map elements "
            + "(topicref/keydef); content reuse (conref), links, and images "
            + "are only warned about, never auto-edited. DRY-RUN by default: "
            + "returns the plan. Call again with apply=true to execute. "
            + "Always review the plan before applying.",
            "delete-file", schema(
                prop("file", "string", "The file to delete", true),
                prop("root", "string", "Project root scanned for inbound references", true),
                prop("removeRefs", "boolean", "Also remove referencing map elements (default false)", false),
                prop("apply", "boolean", "Execute the delete (default false = plan only)", false)
            )));

        list.add(new McpTool("retarget",
            "Rewrite every reference that points at one file to point at "
            + "another (no move, no delete) — swap in a replacement topic or "
            + "image. DRY-RUN by default: returns the plan. Call again with "
            + "apply=true to execute. Always review the plan before applying.",
            "retarget", schema(
                prop("from", "string", "The currently referenced file", true),
                prop("to", "string", "The new target file", true),
                prop("root", "string", "Project root whose references are rewritten", true),
                prop("apply", "boolean", "Execute the refactor (default false = plan only)", false)
            )));

        list.add(new McpTool("keyify",
            "Convert direct references to a file into key references (href on "
            + "xref/link/image → keyref, conref → conkeyref) and add a keydef "
            + "to the chosen map. Map topicrefs stay direct. DRY-RUN by "
            + "default: returns the plan. Call again with apply=true to "
            + "execute. Always review the plan before applying.",
            "keyify", schema(
                prop("file", "string", "The referenced file to key", true),
                prop("key", "string", "The key name to introduce", true),
                prop("map", "string", "The map that receives the keydef (often a keys submap)", true),
                prop("root", "string", "Project root whose references are converted", true),
                prop("rootMap", "string", "Root map for context: warns when map isn't included from it", false),
                prop("apply", "boolean", "Execute the refactor (default false = plan only)", false)
            )));

        list.add(new McpTool("inline_key",
            "Replace keyref/conkeyref usages of a key with the direct path it "
            + "resolves to (keyref → href, conkeyref → conref). Text-pulling "
            + "keyrefs are warned about, not touched. DRY-RUN by default: "
            + "returns the plan. Call again with apply=true to execute. "
            + "Always review the plan before applying.",
            "inline-key", schema(
                prop("key", "string", "The key to inline", true),
                prop("map", "string", "Context map that resolves the key", true),
                prop("root", "string", "Project root whose usages are rewritten", true),
                prop("apply", "boolean", "Execute the refactor (default false = plan only)", false)
            )));

        list.add(new McpTool("extract_conref",
            "Move an element (located by its id) from a topic into a reuse "
            + "topic (created if missing) and replace it with a conref stub "
            + "— the standard way to make content reusable. DRY-RUN by "
            + "default: returns the plan. Call again with apply=true to "
            + "execute. Always review the plan before applying.",
            "extract-conref", schema(
                prop("file", "string", "The topic containing the element", true),
                prop("elementId", "string", "The id of the element to extract", true),
                prop("to", "string", "Reuse topic that receives the element (created if missing)", true),
                prop("apply", "boolean", "Execute the extraction (default false = plan only)", false)
            )));

        list.add(new McpTool("create_keydef",
            "Define a text key ('extract variable' for DITA): adds a keydef "
            + "whose keyword is the given text to the chosen map. Replace "
            + "occurrences with <ph keyref=\"key\"/> separately. DRY-RUN by "
            + "default: returns the plan. Call again with apply=true to "
            + "execute. Always review the plan before applying.",
            "create-keydef", schema(
                prop("key", "string", "The key name to define", true),
                prop("text", "string", "The keyword text the key resolves to (plain text)", true),
                prop("map", "string", "The map that receives the keydef (often a keys submap)", true),
                prop("rootMap", "string", "Root map for context: warns when map isn't included from it", false),
                prop("replaceRoot", "string", "Also replace whole-word occurrences in topics under this root with <ph keyref=.../> (text content only)", false),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("inline_conref",
            "Inline a conref permanently (inverse of extract_conref): every "
            + "conref resolving to target#.../elementId is replaced by a copy "
            + "of the element's source text (relative paths inside the copy "
            + "are rebased per destination). Optional file restricts to one "
            + "file. DRY-RUN by default; apply=true to execute. Always review "
            + "the plan before applying.",
            "inline-conref", schema(
                prop("target", "string", "The file holding the reused element", true),
                prop("elementId", "string", "The reused element's id", true),
                prop("root", "string", "Project root scanned for conref instances", true),
                prop("file", "string", "Only inline instances in this file (default all)", false),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("rename_element_id",
            "Rename an element id and every #fragment referencing it: "
            + "#topic/oldId (and #oldId forms when it's the topic id) on "
            + "href/conref project-wide, plus key/oldId on keyref/conkeyref "
            + "when rootMap is given. DRY-RUN by default; apply=true to "
            + "execute. Always review the plan before applying.",
            "rename-element-id", schema(
                prop("file", "string", "The topic containing the element", true),
                prop("oldId", "string", "The current id", true),
                prop("newId", "string", "The new id", true),
                prop("root", "string", "Project root whose references are rewritten", true),
                prop("rootMap", "string", "Root map enabling key-mediated rewriting", false),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("merge_keydefs",
            "Remove shadowed duplicate key definitions from a root map's "
            + "closure. First definition wins, so resolution is unchanged by "
            + "construction; shadowed keydefs are removed (or just their "
            + "shadowed tokens for multi-key keydefs). DRY-RUN by default; "
            + "apply=true to execute. Always review the plan before applying.",
            "merge-keydefs", schema(
                prop("rootMap", "string", "The root map whose closure is scanned", true),
                prop("key", "string", "Merge just this key (default: all duplicates)", false),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("rename_profile_value",
            "Rename a profiling attribute value project-wide: the token in "
            + "attribute=\"...\" lists (product/audience/platform/otherprops) "
            + "across every topic and map — only the matching token changes — "
            + "plus matching <prop att val> rules in .ditaval files so "
            + "filters keep working. DRY-RUN by default; apply=true to "
            + "execute. Always review the plan before applying.",
            "rename-profile-value", schema(
                prop("attribute", "string", "The profiling attribute (e.g. product)", true),
                prop("oldValue", "string", "The token to rename", true),
                prop("newValue", "string", "The new token", true),
                prop("root", "string", "Project root scanned and rewritten", true),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("split_topic",
            "Split a topic: every top-level <section> becomes a standalone "
            + "topic in the same directory (title/id derived from the "
            + "section), the map gains nested topicrefs under the source "
            + "topic's topicref, and href/conref references into the "
            + "split-out content are rewritten project-wide. DRY-RUN by "
            + "default; apply=true to execute. Always review the plan "
            + "before applying.",
            "split-topic", schema(
                prop("file", "string", "The topic to split", true),
                prop("root", "string", "Project root whose references are rewritten", true),
                prop("map", "string", "Map that receives the new topicrefs", false),
                prop("apply", "boolean", "Execute (default false = plan only)", false)
            )));

        list.add(new McpTool("render_preview",
            "Render the styled DITA preview of a topic or map to HTML, with "
            + "keyref/conkeyref resolution against a context map and optional "
            + "DITAVAL filtering.",
            "render-preview", schema(
                prop("file", "string", "DITA topic or map path", true),
                prop("output", "string", "Output HTML path (returns HTML if omitted)", false),
                prop("map", "string", "Context map for key resolution", false),
                prop("ditaval", "string", "DITAVAL filter", false),
                prop("showChanges", "boolean", "Render open review proposals as insertions, "
                    + "deletions and comments instead of the accepted view", false)
            )));

        list.add(new McpTool("project_graph",
            "How the project connects, in one call: nodes (maps, topics, keys, DITAVALs, with type, "
            + "title and which deliverables ship them) and typed edges (mapref, topicref, reltable, "
            + "keydef, keytarget with via = the map that bound the key, keyref, conref, conkeyref, "
            + "link, ditavalref, profile), plus issues: broken references, undefined keys and keys "
            + "that resolve in some deliverables but not others, shadowed and unused keys, orphans, "
            + "and (checks, default true) invalid shipped files, broken element ids and conref push "
            + "problems. Ids are project-relative. Use instead of where_used per file; pass the "
            + "result to render_report (template relationship-map) for a page.",
            "project-graph", schema(
                prop("root", "string", "Project root directory", true),
                prop("map", "string", "Limit to one root map", false),
                prop("deliverable", "string", "Limit to one deliverable from the project file", false),
                prop("checks", "boolean", "Add DTD validation, element-id and conref push findings "
                    + "to issues (default true)", false)
            )));

        list.add(new McpTool("render_report",
            "Write a standalone HTML page for people: one file that works offline from disk, in light "
            + "and dark. The page shows a read-only tool's output: give source (project_graph for a "
            + "relationship map, project_health for a health report) and that tool's args, or pass "
            + "data (JSON). Templates: relationship-map, health, or a project template path such as "
            + ".dogsbay/reports/mine.html; defaults from the source. For a different view, design a "
            + "template (an HTML page with one empty <script id=\"report-data\" type=\"application/json\"> "
            + "element it reads with JSON.parse) and render it here, so the page can be regenerated. "
            + "Never extract the data yourself.",
            "render-report", schema(
                prop("output", "string", "Where to write the page; relative paths resolve against root", true),
                prop("root", "string", "Project root: passed to the source tool, and resolves template "
                    + "and output paths", false),
                prop("source", "string", "Read-only tool whose output fills the page, e.g. project_graph", false),
                prop("args", "object", "The source tool's arguments; root is added when omitted", false),
                prop("data", "string", "JSON to render instead of running a source", false),
                prop("template", "string", "Built-in template name or project template path", false)
            )));

        list.add(new McpTool("open_document",
            "Open an existing file in the editor.",
            "open", schema(
                prop("file", "string", "Absolute path to file", true)
            )));

        list.add(new McpTool("new_document",
            "Create a new file on disk and open it in the editor. " +
            "Creates parent directories if needed. Fails if the file already exists.",
            "newDocument", schema(
                prop("file", "string", "Absolute path for the new file", true),
                prop("content", "string", "Initial file content", false)
            )));

        list.add(new McpTool("close_document",
            "Close a document in the editor.",
            "close", schema(
                prop("file", "string", "File path (closes active document if omitted)", false),
                prop("all", "boolean", "Close all documents", false)
            )));

        list.add(new McpTool("save_document",
            "Save a document in the editor.",
            "save", schema(
                prop("file", "string", "File path (saves active document if omitted)", false),
                prop("all", "boolean", "Save all documents", false)
            )));

        list.add(new McpTool("list_open_documents",
            "List all documents currently open in the editor with metadata.",
            "listDocuments", schema()));

        list.add(new McpTool("get_document_content",
            "Read the content of an open document from the editor buffer.",
            "getContent", schema(
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("set_document_content",
            "Replace the content of an open document in the editor.",
            "setContent", schema(
                prop("file", "string", "File path (active document if omitted)", false),
                prop("content", "string", "New content", true)
            )));

        list.add(new McpTool("get_selection",
            "Get the currently selected text in the editor.",
            "getSelection", schema()));

        list.add(new McpTool("replace_selection",
            "Replace the currently selected text in the editor. Inserts at cursor if nothing selected.",
            "replaceSelection", schema(
                prop("text", "string", "Text to insert", true)
            )));

        list.add(new McpTool("goto_line",
            "Move the editor caret to the start of a 1-based line.",
            "gotoLine", schema(
                prop("line", "integer", "1-based line number", true)
            )));

        list.add(new McpTool("get_cursor",
            "Get the editor caret position as offset plus 1-based line and column.",
            "getCursor", schema()));

        list.add(new McpTool("set_cursor",
            "Move the editor caret to a character offset, or by a signed delta when "
                + "relative is true. Positions outside the document are clamped.",
            "setCursor", schema(
                prop("position", "integer", "Character offset, or delta if relative", true),
                prop("relative", "boolean", "Treat position as a delta from the caret", false)
            )));

        list.add(new McpTool("select_element",
            "Select the XML element surrounding the caret, or just its content.",
            "selectElement", schema(
                prop("contentOnly", "boolean", "Select content only, excluding tags", false)
            )));

        list.add(new McpTool("get_document_outline",
            "Get the structural outline of a document as a tree of elements.",
            "getOutline", schema(
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("author_switch",
            "Switch a document's tab to the WYSIWYG Author view, or to the side-by-side "
                + "XML + Author split when split=true.",
            "authorSwitch", schema(
                prop("file", "string", "File path (active document if omitted)", false),
                prop("split", "boolean", "Show XML and Author side by side", false)
            )));

        list.add(new McpTool("author_outline",
            "Get the Author view's block tree (block ids, types, text, attributes). "
                + "Block ids feed author_insert_block / author_set_text.",
            "authorOutline", schema(
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("author_insert_block",
            "Insert a block (required children auto-created, e.g. step gets its cmd) "
                + "under a parent block id from author_outline. Returns the focused block id.",
            "authorInsertBlock", schema(
                prop("type", "string", "Block type name (p, step, ul, note, ...)", true),
                prop("parentId", "string", "Parent block id from author_outline", true),
                prop("index", "integer", "Position among children (append if omitted)", false),
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("author_set_text",
            "Replace the plain text of a text block identified by its author_outline id.",
            "authorSetText", schema(
                prop("blockId", "string", "Block id from author_outline", true),
                prop("text", "string", "New plain text", true),
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("author_issues",
            "Get the Author view's semantic validation issues (missing titles, empty commands, ...).",
            "authorIssues", schema(
                prop("file", "string", "File path (active document if omitted)", false)
            )));

        list.add(new McpTool("search_project",
            "Search across project files with text or regex.",
            "search", schema(
                prop("query", "string", "Search text or regex", true),
                prop("glob", "string", "File pattern filter (e.g., '*.dita')", false),
                prop("regex", "boolean", "Treat query as regex", false),
                prop("caseSensitive", "boolean", "Case-sensitive search (default: true)", false),
                prop("maxResults", "integer", "Maximum results (default: unlimited)", false)
            )));

        list.add(new McpTool("list_project_files",
            "List project files matching a glob pattern.",
            "listFiles", schema(
                prop("pattern", "string", "Glob pattern (e.g., '**/*.dita')", true)
            )));

        list.add(new McpTool("create_project",
            "Create a new project with a name and folder path. Optionally set type (dita/docbook) and default root map.",
            "createProject", schema(
                prop("name", "string", "Project name", true),
                prop("folder", "string", "Absolute path to project folder", true),
                prop("type", "string", "Project type: 'dita', 'docbook', or omit for generic", false),
                prop("rootMap", "string", "Default root map file path (for DITA projects)", false)
            )));

        list.add(new McpTool("open_project",
            "Switch to an existing project by name. Opens the project folder in the file explorer.",
            "openProject", schema(
                prop("name", "string", "Project name to switch to", true)
            )));

        list.add(new McpTool("list_projects",
            "List all configured projects with metadata.",
            "listProjects", schema()));

        list.add(new McpTool("get_project",
            "Get the active project, including its resolved DITA project: the "
            + "catalogs, the project file (if any), and the deliverables "
            + "(name, root map, ditaval, transtype). Use this to discover the "
            + "root map(s) and conditions instead of searching the filesystem.",
            "getProject", schema()));

        list.add(new McpTool("list_sidebars",
            "List all available sidebar panels with their IDs, names, side (left/right), and active state.",
            "listSidebars", schema()));

        list.add(new McpTool("switch_sidebar",
            "Switch to a sidebar panel by ID. Use list_sidebars to see available IDs.",
            "switchSidebar", schema(
                prop("id", "string", "Sidebar panel ID (e.g., 'fileExplorer', 'git', 'dita', 'search')", true),
                prop("side", "string", "Which side: 'left' (default) or 'right'", false)
            )));

        list.add(new McpTool("open_ditamap",
            "Load a DITA map into the DITA map explorer and switch to it.",
            "openDitaMap", schema(
                prop("file", "string", "Absolute path to the .ditamap file", true)
            )));

        return list;
    }

    // ── Schema Helpers ──────────────────────────────────────────────────

    public record McpTool(String name, String description, String rpcMethod, ObjectNode inputSchema) {}

    private record PropDef(String name, String type, String description, boolean required) {}

    private PropDef prop(String name, String type, String description, boolean required) {
        return new PropDef(name, type, description, required);
    }

    private ObjectNode schema(PropDef... props) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = mapper.createArrayNode();

        for (PropDef p : props) {
            ObjectNode propNode = properties.putObject(p.name());
            propNode.put("type", p.type());
            propNode.put("description", p.description());
            if (p.required()) {
                required.add(p.name());
            }
        }

        if (required.size() > 0) {
            schema.set("required", required);
        }

        return schema;
    }
}
