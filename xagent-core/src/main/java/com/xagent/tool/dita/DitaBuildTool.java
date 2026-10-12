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
package com.xagent.tool.dita;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.operations.BashOperations;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run a DITA-OT build and report errors in a fix-loop-friendly shape.
 * Requires the {@code dita} command (DITA Open Toolkit) on the PATH,
 * or an explicit {@code dita_cmd}.
 */
public class DitaBuildTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int DEFAULT_TIMEOUT_SECONDS = 600;

	/** [DOTJ012F]-style message codes. */
	private static final Pattern CODE = Pattern.compile("\\[(DOT[A-Z0-9]+[EWF])\\]");
	/** file references with optional :line:col, both file: URIs and bare paths */
	private static final Pattern LOCATION = Pattern.compile(
		"(?:file:/+)?([^\\s\\[\\]'\"]+\\.(?:ditamap|dita|xml))(?::(\\d+))?(?::(\\d+))?");

	record Issue(String severity, String code, String file, int line, int column, String message) {}

	private final Path cwd;
	private final BashOperations bashOps;

	public DitaBuildTool(Path cwd, BashOperations bashOps) {
		this.cwd = cwd;
		this.bashOps = bashOps;
	}

	@Override
	public String name() {
		return "dita_build";
	}

	@Override
	public String description() {
		return "Run a DITA-OT build for a DITA map. Requires the DITA Open Toolkit "
			+ "('dita' command on PATH, or pass dita_cmd). Returns build errors as "
			+ "structured file:line: severity: message lines so they can be fixed and the "
			+ "build re-run.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		ObjectNode props = schema.putObject("properties");

		ObjectNode map = props.putObject("map");
		map.put("type", "string");
		map.put("description", "Path to the .ditamap (or topic) to build");

		ObjectNode transtype = props.putObject("transtype");
		transtype.put("type", "string");
		transtype.put("description", "Output format / transtype (default: html5)");

		ObjectNode output = props.putObject("output");
		output.put("type", "string");
		output.put("description", "Output directory (default: out)");

		ObjectNode ditaCmd = props.putObject("dita_cmd");
		ditaCmd.put("type", "string");
		ditaCmd.put("description", "Path to the dita executable (default: dita on PATH)");

		ObjectNode timeout = props.putObject("timeout_seconds");
		timeout.put("type", "integer");
		timeout.put("description", "Build timeout in seconds (default: 600)");

		schema.putArray("required").add("map");
		return schema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
		Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {

		String map = params.path("map").asText("");
		if (map.isEmpty()) {
			return AgentToolResult.error("Missing required parameter: map");
		}
		String transtype = params.path("transtype").asText("html5");
		String output = params.path("output").asText("out");
		String ditaCmd = params.path("dita_cmd").asText("dita");
		int timeoutSeconds = params.path("timeout_seconds").asInt(DEFAULT_TIMEOUT_SECONDS);

		String command = ditaCmd
			+ " --input=" + quote(map)
			+ " --format=" + quote(transtype)
			+ " --output=" + quote(output);

		BashOperations.BashResult result;
		try {
			result = bashOps.exec(command, cwd, Duration.ofSeconds(timeoutSeconds), null, isCancelled);
		} catch (Exception e) {
			return AgentToolResult.error("Failed to run DITA-OT: " + e.getMessage());
		}

		String log = result.stdout() + "\n" + result.stderr();
		List<Issue> issues = parseIssues(log);
		long errors = issues.stream().filter(i -> "error".equals(i.severity())).count();
		long warnings = issues.size() - errors;

		ObjectNode details = MAPPER.createObjectNode();
		details.put("exitCode", result.exitCode());
		details.put("command", command);
		ArrayNode issueArray = details.putArray("issues");
		for (var issue : issues) {
			ObjectNode node = issueArray.addObject();
			node.put("severity", issue.severity());
			if (issue.code() != null) node.put("code", issue.code());
			if (issue.file() != null) node.put("file", issue.file());
			if (issue.line() > 0) node.put("line", issue.line());
			if (issue.column() > 0) node.put("column", issue.column());
			node.put("message", issue.message());
		}

		if (result.exitCode() == 0) {
			var sb = new StringBuilder("DITA-OT build succeeded: " + transtype + " output in " + output);
			if (warnings > 0) {
				sb.append("\n").append(warnings).append(" warning").append(warnings == 1 ? "" : "s").append(":\n");
				sb.append(formatIssues(issues));
			}
			return AgentToolResult.success(sb.toString(), details);
		}

		var sb = new StringBuilder("DITA-OT build failed (exit " + result.exitCode() + ")");
		if (!issues.isEmpty()) {
			sb.append(", ").append(errors).append(" error").append(errors == 1 ? "" : "s").append(":\n");
			sb.append(formatIssues(issues));
		} else {
			sb.append(". Last output:\n").append(tail(log, 20));
		}
		return AgentToolResult.error(sb.toString(), details);
	}

	static List<Issue> parseIssues(String log) {
		var issues = new ArrayList<Issue>();
		for (String line : log.split("\n")) {
			String severity;
			if (line.contains("[ERROR]") || line.contains("[FATAL]")) {
				severity = "error";
			} else if (line.contains("[WARN]") || line.contains("[WARNING]")) {
				severity = "warning";
			} else {
				continue;
			}

			String code = null;
			Matcher codeMatcher = CODE.matcher(line);
			if (codeMatcher.find()) {
				code = codeMatcher.group(1);
			}

			String file = null;
			int lineNo = -1;
			int column = -1;
			Matcher locMatcher = LOCATION.matcher(line);
			if (locMatcher.find()) {
				file = locMatcher.group(1);
				if (locMatcher.group(2) != null) lineNo = Integer.parseInt(locMatcher.group(2));
				if (locMatcher.group(3) != null) column = Integer.parseInt(locMatcher.group(3));
			}

			String message = line
				.replace("[ERROR]", "").replace("[FATAL]", "")
				.replace("[WARN]", "").replace("[WARNING]", "")
				.strip();
			issues.add(new Issue(severity, code, file, lineNo, column, message));
		}
		return issues;
	}

	private static String formatIssues(List<Issue> issues) {
		var sb = new StringBuilder();
		for (var issue : issues) {
			if (issue.file() != null) {
				sb.append(issue.file());
				if (issue.line() > 0) {
					sb.append(':').append(issue.line());
					if (issue.column() > 0) sb.append(':').append(issue.column());
				}
				sb.append(": ");
			}
			sb.append(issue.severity()).append(": ").append(issue.message()).append('\n');
		}
		return sb.toString().strip();
	}

	private static String tail(String text, int lines) {
		var all = text.strip().split("\n");
		int from = Math.max(0, all.length - lines);
		return String.join("\n", List.of(all).subList(from, all.length));
	}

	private static String quote(String value) {
		if (value.matches("[A-Za-z0-9._/=-]+")) {
			return value;
		}
		return "'" + value.replace("'", "'\\''") + "'";
	}
}
