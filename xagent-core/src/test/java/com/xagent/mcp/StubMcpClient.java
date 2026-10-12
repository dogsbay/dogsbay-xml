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
package com.xagent.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.*;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * Stub MCP client for testing. Returns preconfigured tool lists and execution results.
 */
class StubMcpClient implements McpClient {

	private ToolExecutionResult executeResult;
	private RuntimeException throwOnExecute;
	private List<ToolSpecification> toolSpecs = List.of();
	private String lastRequestName;

	void setExecuteResult(ToolExecutionResult result) {
		this.executeResult = result;
	}

	void setThrowOnExecute(RuntimeException e) {
		this.throwOnExecute = e;
	}

	void setToolSpecs(List<ToolSpecification> specs) {
		this.toolSpecs = specs;
	}

	String getLastRequestName() {
		return lastRequestName;
	}

	@Override
	public String key() {
		return "stub";
	}

	@Override
	public List<ToolSpecification> listTools() {
		return toolSpecs;
	}

	@Override
	public List<ToolSpecification> listTools(InvocationContext context) {
		return toolSpecs;
	}

	@Override
	public ToolExecutionResult executeTool(ToolExecutionRequest request) {
		this.lastRequestName = request.name();
		if (throwOnExecute != null) {
			throw throwOnExecute;
		}
		return executeResult;
	}

	@Override
	public ToolExecutionResult executeTool(ToolExecutionRequest request, InvocationContext context) {
		return executeTool(request);
	}

	@Override
	public List<McpResource> listResources() {
		return List.of();
	}

	@Override
	public List<McpResource> listResources(InvocationContext context) {
		return List.of();
	}

	@Override
	public List<McpResourceTemplate> listResourceTemplates() {
		return List.of();
	}

	@Override
	public List<McpResourceTemplate> listResourceTemplates(InvocationContext context) {
		return List.of();
	}

	@Override
	public McpReadResourceResult readResource(String uri) {
		return null;
	}

	@Override
	public McpReadResourceResult readResource(String uri, InvocationContext context) {
		return null;
	}

	@Override
	public List<McpPrompt> listPrompts() {
		return List.of();
	}

	@Override
	public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
		return null;
	}

	@Override
	public void checkHealth() {
	}

	@Override
	public void setRoots(List<McpRoot> roots) {
	}

	@Override
	public void close() {
	}
}
