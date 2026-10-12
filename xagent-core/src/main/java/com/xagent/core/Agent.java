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
package com.xagent.core;

import com.xagent.event.AgentEvent;
import com.xagent.message.AgentMessage;
import com.xagent.message.UserMessage;
import com.xagent.provider.ProviderConfig;
import com.xagent.session.SessionManager;
import com.xagent.tool.ToolRegistry;
import dev.langchain4j.model.chat.StreamingChatModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stateful agent that holds conversation history, tools, and runs the agent loop.
 * Mirrors pi's Agent class in packages/agent/src/agent.ts.
 */
public class Agent {

	private volatile StreamingChatModel model;
	private final Supplier<StreamingChatModel> modelSupplier;
	private volatile ProviderConfig config;
	private final List<AgentMessage> messages = new ArrayList<>();
	private final Set<Consumer<AgentEvent>> subscribers = ConcurrentHashMap.newKeySet();
	private final String systemPrompt;
	private final ToolRegistry toolRegistry;
	private final Compactor compactor;
	private final UsageStats usageStats = new UsageStats();
	private final ConcurrentLinkedQueue<String> steeringQueue = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<String> followUpQueue = new ConcurrentLinkedQueue<>();
	private ToolHooks toolHooks;
	private SessionManager sessionManager;
	private String sessionId;
	private String lastEntryId;
	private volatile boolean streaming = false;
	private volatile AgentLoop currentLoop = null;

	public Agent(StreamingChatModel model, ProviderConfig config) {
		this(model, config, "You are a helpful coding assistant.", new ToolRegistry());
	}

	public Agent(StreamingChatModel model, ProviderConfig config, String systemPrompt) {
		this(model, config, systemPrompt, new ToolRegistry());
	}

	public Agent(StreamingChatModel model, ProviderConfig config, String systemPrompt, ToolRegistry toolRegistry) {
		this(model, config, systemPrompt, toolRegistry, CompactionConfig.DEFAULT);
	}

	public Agent(StreamingChatModel model, ProviderConfig config, String systemPrompt, ToolRegistry toolRegistry,
		CompactionConfig compactionConfig) {
		this.model = model;
		this.modelSupplier = () -> model;
		this.config = config;
		this.systemPrompt = systemPrompt;
		this.toolRegistry = toolRegistry;
		this.compactor = new Compactor(compactionConfig);
		this.usageStats.configureForModel(config.provider(), config.model());
	}

	/**
	 * Lazy variant: the model is created on first use via {@code modelSupplier}.
	 * This lets the UI start before credentials exist (so {@code /login} can run)
	 * and surfaces provider/auth errors at prompt time instead of crashing at
	 * startup. The supplier is re-invoked after {@link #invalidateModel()}.
	 */
	public Agent(Supplier<StreamingChatModel> modelSupplier, ProviderConfig config, String systemPrompt,
		ToolRegistry toolRegistry) {
		this(modelSupplier, config, systemPrompt, toolRegistry, CompactionConfig.DEFAULT);
	}

	public Agent(Supplier<StreamingChatModel> modelSupplier, ProviderConfig config, String systemPrompt,
		ToolRegistry toolRegistry, CompactionConfig compactionConfig) {
		this.model = null;
		this.modelSupplier = modelSupplier;
		this.config = config;
		this.systemPrompt = systemPrompt;
		this.toolRegistry = toolRegistry;
		this.compactor = new Compactor(compactionConfig);
		this.usageStats.configureForModel(config.provider(), config.model());
	}

	public UsageStats usageStats() {
		return usageStats;
	}

	/**
	 * Enable session persistence. Creates a new session or resumes an existing one.
	 */
	public void enableSession(SessionManager manager, String existingSessionId) throws IOException {
		this.sessionManager = manager;
		if (existingSessionId != null) {
			this.sessionId = existingSessionId;
			var loaded = manager.loadMessages(existingSessionId);
			messages.addAll(loaded);
		} else {
			// Reserve, don't create: an editor launch where nobody speaks to the
			// agent should leave no transcript behind.
			String cwd = System.getProperty("user.dir");
			this.sessionId = manager.reserveSession(cwd, config.provider(), config.model());
		}
	}

	public String sessionId() {
		return sessionId;
	}

	public void subscribe(Consumer<AgentEvent> listener) {
		subscribers.add(listener);
	}

	public void unsubscribe(Consumer<AgentEvent> listener) {
		subscribers.remove(listener);
	}

	public List<AgentMessage> messages() {
		return List.copyOf(messages);
	}

	public boolean isStreaming() {
		return streaming;
	}

	public ToolRegistry toolRegistry() {
		return toolRegistry;
	}

	public void clearHistory() {
		messages.clear();
	}

	public void abort() {
		if (currentLoop != null) {
			currentLoop.cancel();
		}
	}

	/**
	 * Interrupt the current turn with a new instruction.
	 * The message is injected into history and the agent re-loops.
	 */
	public void steer(String message) {
		steeringQueue.add(message);
	}

	/**
	 * Queue a message to be processed after the current turn completes.
	 */
	public void followUp(String message) {
		followUpQueue.add(message);
	}

	/**
	 * Set tool hooks for intercepting tool calls.
	 */
	public void setToolHooks(ToolHooks hooks) {
		this.toolHooks = hooks;
	}

	/**
	 * Drop the cached model so the next prompt re-creates it via the supplier.
	 * Call after credentials change (e.g. {@code /login} / {@code /logout}).
	 */
	public void invalidateModel() {
		this.model = null;
	}

	/** The provider configuration currently in effect. */
	public ProviderConfig config() {
		return config;
	}

	/**
	 * Switch the active provider/model at runtime (e.g. from a /provider
	 * picker or after /login). Reconfigures cost tracking and drops the cached
	 * model so the next prompt is created with the new config.
	 */
	public void setProviderConfig(ProviderConfig newConfig) {
		this.config = newConfig;
		this.usageStats.configureForModel(newConfig.provider(), newConfig.model());
		invalidateModel();
	}

	private StreamingChatModel resolveModel() {
		StreamingChatModel m = model;
		if (m == null) {
			// may throw (not authenticated / no API key); only cache on success
			m = modelSupplier.get();
			model = m;
		}
		return m;
	}

	/**
	 * Manually trigger context compaction.
	 * Returns the summary text, or null if no compaction was needed.
	 */
	public String compact() {
		return compactor.compact(messages);
	}

	/**
	 * Send a user message and run the agent loop.
	 * The agent will stream responses and execute tool calls until done.
	 * Blocks until the loop completes.
	 */
	public void prompt(String input) {
		// Resolve the model first: auth/provider errors surface here (propagated
		// to the REPL/TUI to display) before any history is mutated.
		StreamingChatModel activeModel = resolveModel();

		var userMsg = new UserMessage(input);
		messages.add(userMsg);
		persistMessage(userMsg);

		// Auto-compact if needed
		if (compactor.needsCompaction(messages)) {
			compactor.compact(messages);
		}

		streaming = true;
		try {
			int messagesBefore = messages.size();
			var loop = new AgentLoop(
				activeModel, toolRegistry, systemPrompt,
				config.model(), config.provider(),
				this::emit, true, RetryConfig.DEFAULT, usageStats, toolHooks
			);
			currentLoop = loop;
			loop.run(messages);

			// Persist any new messages added by the loop
			for (int i = messagesBefore; i < messages.size(); i++) {
				persistMessage(messages.get(i));
			}

			// Process follow-up queue
			String followUp;
			while ((followUp = followUpQueue.poll()) != null) {
				var followUpMsg = new UserMessage(followUp);
				messages.add(followUpMsg);
				persistMessage(followUpMsg);
				int beforeFollowUp = messages.size();
				var followUpLoop = new AgentLoop(
					activeModel, toolRegistry, systemPrompt,
					config.model(), config.provider(),
					this::emit, true, RetryConfig.DEFAULT, usageStats, toolHooks
				);
				currentLoop = followUpLoop;
				followUpLoop.run(messages);
				for (int i = beforeFollowUp; i < messages.size(); i++) {
					persistMessage(messages.get(i));
				}
			}

			// Name the session after the first thing asked of it — what the user
			// typed, not the editor context prepended to it, which is identical
			// in every session and so identifies none of them.
			// …unless it already has one. A session can be named before anything
			// is said in it, and that name is the user's, not a default to
			// overwrite with whatever they happened to type first.
			if (sessionManager != null && sessionId != null && messagesBefore == 1
					&& !sessionManager.hasName(sessionId)) {
				try {
					String asked = SessionManager.stripEditorContext(input);
					sessionManager.appendSummary(sessionId,
						truncate(asked.isBlank() ? input : asked, 80));
				} catch (IOException ignored) {}
			}
		} finally {
			streaming = false;
			currentLoop = null;
		}
	}

	private void persistMessage(AgentMessage message) {
		if (sessionManager != null && sessionId != null) {
			try {
				sessionManager.appendMessage(sessionId, message, lastEntryId);
			} catch (IOException ignored) {}
		}
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", " ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}

	private void emit(AgentEvent event) {
		for (var subscriber : subscribers) {
			try {
				subscriber.accept(event);
			} catch (Exception e) {
				// Don't let a subscriber error crash the agent
			}
		}
	}
}
