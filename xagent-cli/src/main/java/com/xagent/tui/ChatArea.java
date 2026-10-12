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
package com.xagent.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Scrollable chat area that displays conversation blocks.
 * Flexible height — takes remaining vertical space.
 */
public class ChatArea implements Component {

	/**
	 * A block of content in the chat area.
	 */
	public sealed interface ChatBlock {
		record UserBlock(String text) implements ChatBlock {}
		record AssistantBlock(String text, boolean complete) implements ChatBlock {}
		record ToolBlock(String name, String args, String result, boolean isError, boolean collapsed) implements ChatBlock {}
		record InfoBlock(String text) implements ChatBlock {}
	}

	private final List<ChatBlock> blocks = new ArrayList<>();
	private final Theme theme;
	private final MarkdownRenderer markdownRenderer;
	private final AtomicBoolean dirty = new AtomicBoolean(true);
	private int scrollOffset = 0;
	private boolean autoScroll = true;
	private int lastRenderedHeight = 0;
	private boolean rawMode = false;

	public ChatArea() {
		this(Theme.DEFAULT);
	}

	public ChatArea(Theme theme) {
		this.theme = theme;
		this.markdownRenderer = new MarkdownRenderer(theme);
	}

	public synchronized void toggleRawMode() {
		rawMode = !rawMode;
		dirty.set(true);
	}

	public boolean isRawMode() {
		return rawMode;
	}

	public synchronized void addUserBlock(String text) {
		blocks.add(new ChatBlock.UserBlock(text));
		if (autoScroll) scrollToBottom();
		dirty.set(true);
	}

	public synchronized void startAssistantBlock() {
		blocks.add(new ChatBlock.AssistantBlock("", false));
		dirty.set(true);
	}

	public synchronized void appendToAssistant(String text) {
		for (int i = blocks.size() - 1; i >= 0; i--) {
			if (blocks.get(i) instanceof ChatBlock.AssistantBlock ab) {
				blocks.set(i, new ChatBlock.AssistantBlock(ab.text() + text, false));
				if (autoScroll) scrollToBottom();
				dirty.set(true);
				return;
			}
		}
	}

	public synchronized void completeAssistantBlock() {
		for (int i = blocks.size() - 1; i >= 0; i--) {
			if (blocks.get(i) instanceof ChatBlock.AssistantBlock ab) {
				blocks.set(i, new ChatBlock.AssistantBlock(ab.text(), true));
				dirty.set(true);
				return;
			}
		}
	}

	public synchronized void addToolBlock(String name, String args) {
		blocks.add(new ChatBlock.ToolBlock(name, args, null, false, false));
		if (autoScroll) scrollToBottom();
		dirty.set(true);
	}

	public synchronized void updateToolBlock(String name, String result, boolean isError) {
		for (int i = blocks.size() - 1; i >= 0; i--) {
			if (blocks.get(i) instanceof ChatBlock.ToolBlock tb && tb.name().equals(name) && tb.result() == null) {
				blocks.set(i, new ChatBlock.ToolBlock(tb.name(), tb.args(), result, isError, true));
				if (autoScroll) scrollToBottom();
				dirty.set(true);
				return;
			}
		}
	}

	public synchronized void addErrorBlock(String message) {
		blocks.add(new ChatBlock.AssistantBlock(style("Error: " + message, theme.error()), true));
		if (autoScroll) scrollToBottom();
		dirty.set(true);
	}

	public synchronized void addInfoBlock(String text) {
		blocks.add(new ChatBlock.InfoBlock(text));
		if (autoScroll) scrollToBottom();
		dirty.set(true);
	}

	public synchronized void clear() {
		blocks.clear();
		scrollOffset = 0;
		autoScroll = true;
		dirty.set(true);
	}

	public void scrollUp(int lines) {
		autoScroll = false;
		scrollOffset = Math.max(0, scrollOffset - lines);
		dirty.set(true);
	}

	public void scrollDown(int lines) {
		scrollOffset += lines;
		dirty.set(true);
	}

	private void scrollToBottom() {
		// Will be clamped during render
		scrollOffset = Integer.MAX_VALUE;
		autoScroll = true;
	}

	public List<ChatBlock> blocks() {
		return List.copyOf(blocks);
	}

	@Override
	public synchronized List<String> render(int width) {
		// Render all blocks to lines
		List<String> allLines = new ArrayList<>();
		for (var block : blocks) {
			allLines.addAll(renderBlock(block, width));
			allLines.add(""); // blank line separator
		}
		// Remove trailing blank line
		if (!allLines.isEmpty() && allLines.getLast().isEmpty()) {
			allLines.removeLast();
		}

		lastRenderedHeight = allLines.size();

		// Note: desiredHeight is -1 (flexible), actual height is determined by Layout.
		// We return all lines; Layout will handle height allocation.
		// Scroll offset is applied here to return the visible window.
		return allLines;
	}

	/**
	 * Returns visible lines within the allocated height, applying scroll offset.
	 */
	public synchronized List<String> renderVisible(int width, int height) {
		List<String> allLines = render(width);

		int maxScroll = Math.max(0, allLines.size() - height);
		if (scrollOffset > maxScroll) {
			scrollOffset = maxScroll;
			autoScroll = true;
		}

		List<String> visible = new ArrayList<>(height);
		for (int i = scrollOffset; i < scrollOffset + height && i < allLines.size(); i++) {
			visible.add(allLines.get(i));
		}
		// Pad remaining
		while (visible.size() < height) {
			visible.add("");
		}
		return visible;
	}

	private List<String> renderBlock(ChatBlock block, int width) {
		return switch (block) {
			case ChatBlock.UserBlock ub -> renderUserBlock(ub, width);
			case ChatBlock.AssistantBlock ab -> renderAssistantBlock(ab, width);
			case ChatBlock.ToolBlock tb -> renderToolBlock(tb, width);
			case ChatBlock.InfoBlock ib -> renderInfoBlock(ib, width);
		};
	}

	private List<String> renderUserBlock(ChatBlock.UserBlock block, int width) {
		List<String> lines = new ArrayList<>();
		String prefix = style("> ", theme.userPrompt());
		int prefixVisLen = 2;
		int contentWidth = width - prefixVisLen;
		if (contentWidth <= 0) contentWidth = 1;

		boolean first = true;
		for (String wrapped : TextWrapper.wrap(block.text(), contentWidth)) {
			if (first) {
				lines.add(prefix + style(wrapped, theme.userPrompt()));
				first = false;
			} else {
				lines.add("  " + style(wrapped, theme.userPrompt()));
			}
		}
		return lines;
	}

	private List<String> renderAssistantBlock(ChatBlock.AssistantBlock block, int width) {
		if (block.text().isEmpty()) {
			return new ArrayList<>();
		}
		if (rawMode) {
			// Raw mode: plain text with word wrapping
			return TextWrapper.wrap(block.text(), width);
		} else {
			// Rendered mode: markdown to ANSI
			return markdownRenderer.render(block.text(), width);
		}
	}

	private List<String> renderToolBlock(ChatBlock.ToolBlock block, int width) {
		List<String> lines = new ArrayList<>();
		String statusIcon;
		if (block.result() == null) {
			statusIcon = style("⟳ ", theme.spinner());
		} else if (block.isError()) {
			statusIcon = style("✗ ", theme.toolResultError());
		} else {
			statusIcon = style("✓ ", theme.toolResultSuccess());
		}

		String header = statusIcon + style(block.name(), theme.toolName());
		if (block.args() != null && !block.args().isEmpty()) {
			String truncArgs = block.args().length() > 60
				? block.args().substring(0, 57) + "..."
				: block.args();
			header += " " + style(truncArgs, theme.toolArgs());
		}
		lines.add(header);

		if (block.result() != null && !block.collapsed()) {
			String resultColor = block.isError() ? theme.toolResultError() : theme.dim();
			String truncResult = block.result().length() > 500
				? block.result().substring(0, 497) + "..."
				: block.result();
			for (String rl : truncResult.split("\n", -1)) {
				for (String wrapped : TextWrapper.wrap(rl, width - 2)) {
					lines.add("  " + style(wrapped, resultColor));
				}
			}
		}

		return lines;
	}

	private List<String> renderInfoBlock(ChatBlock.InfoBlock block, int width) {
		return TextWrapper.wrap(block.text(), width);
	}

	@Override
	public int desiredHeight() {
		return -1; // flexible
	}

	@Override
	public boolean isDirty() {
		return dirty.get();
	}

	@Override
	public void markClean() {
		dirty.set(false);
	}
}
