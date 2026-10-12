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
package com.xagent.extension;

import com.xagent.tool.AgentTool;
import com.xagent.tool.ToolRegistry;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Loads and manages extensions. Dispatches lifecycle events with error isolation.
 *
 * Extensions are discovered from:
 * 1. ServiceLoader (classpath)
 * 2. JAR files in ~/.xagent/extensions/
 * 3. JAR files in .xagent/extensions/ (project-local)
 */
public class ExtensionRunner {

	private final List<Extension> extensions = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();

	/**
	 * Load extensions from all sources and register their tools.
	 */
	public void loadExtensions(ExtensionContext context, ToolRegistry toolRegistry) {
		// Load from ServiceLoader (classpath)
		for (Extension ext : ServiceLoader.load(Extension.class)) {
			loadExtension(ext, context, toolRegistry);
		}

		// Load from JAR directories
		loadFromDirectory(
			Path.of(System.getProperty("user.home"), ".xagent", "extensions"),
			context, toolRegistry
		);
		loadFromDirectory(
			context.cwd().resolve(".xagent").resolve("extensions"),
			context, toolRegistry
		);
	}

	/**
	 * Load extensions from a specific list (useful for testing).
	 */
	public void loadExtensions(List<Extension> exts, ExtensionContext context, ToolRegistry toolRegistry) {
		for (Extension ext : exts) {
			loadExtension(ext, context, toolRegistry);
		}
	}

	public List<Extension> extensions() {
		return Collections.unmodifiableList(extensions);
	}

	public List<String> errors() {
		return Collections.unmodifiableList(errors);
	}

	public void onTurnStart(ExtensionContext context) {
		for (Extension ext : extensions) {
			safeRun(ext, () -> ext.onTurnStart(context));
		}
	}

	public void onTurnEnd(ExtensionContext context) {
		for (Extension ext : extensions) {
			safeRun(ext, () -> ext.onTurnEnd(context));
		}
	}

	/**
	 * Called before tool execution. Returns false if any extension vetoes the call.
	 */
	public boolean onBeforeToolCall(String toolName, String arguments, ExtensionContext context) {
		for (Extension ext : extensions) {
			try {
				if (!ext.onBeforeToolCall(toolName, arguments, context)) {
					return false;
				}
			} catch (Exception e) {
				recordError(ext, e);
			}
		}
		return true;
	}

	public void onAfterToolCall(String toolName, String result, boolean isError, ExtensionContext context) {
		for (Extension ext : extensions) {
			safeRun(ext, () -> ext.onAfterToolCall(toolName, result, isError, context));
		}
	}

	public void onAgentEnd(ExtensionContext context) {
		for (Extension ext : extensions) {
			safeRun(ext, () -> ext.onAgentEnd(context));
		}
	}

	public void unloadAll() {
		for (Extension ext : extensions) {
			safeRun(ext, ext::onUnload);
		}
		extensions.clear();
	}

	private void loadExtension(Extension ext, ExtensionContext context, ToolRegistry toolRegistry) {
		try {
			ext.onLoad(context);

			// Register custom tools
			List<AgentTool> tools = ext.registerTools();
			if (tools != null) {
				for (AgentTool tool : tools) {
					toolRegistry.register(tool);
				}
			}

			extensions.add(ext);
		} catch (Exception e) {
			recordError(ext, e);
		}
	}

	private void loadFromDirectory(Path dir, ExtensionContext context, ToolRegistry toolRegistry) {
		if (!Files.isDirectory(dir)) return;

		try (Stream<Path> jars = Files.list(dir)) {
			List<URL> jarUrls = jars
				.filter(p -> p.toString().endsWith(".jar"))
				.map(p -> {
					try {
						return p.toUri().toURL();
					} catch (Exception e) {
						return null;
					}
				})
				.filter(u -> u != null)
				.toList();

			if (jarUrls.isEmpty()) return;

			var classLoader = new URLClassLoader(
				jarUrls.toArray(new URL[0]),
				getClass().getClassLoader()
			);

			for (Extension ext : ServiceLoader.load(Extension.class, classLoader)) {
				loadExtension(ext, context, toolRegistry);
			}
		} catch (IOException e) {
			errors.add("Failed to scan extensions directory " + dir + ": " + e.getMessage());
		}
	}

	private void safeRun(Extension ext, Runnable action) {
		try {
			action.run();
		} catch (Exception e) {
			recordError(ext, e);
		}
	}

	private void recordError(Extension ext, Exception e) {
		errors.add("[" + ext.name() + "] " + e.getMessage());
	}
}
