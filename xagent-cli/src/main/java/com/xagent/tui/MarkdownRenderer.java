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

import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.xagent.tui.AnsiCodes.RESET;

/**
 * Renders CommonMark markdown to ANSI-styled terminal lines.
 */
public class MarkdownRenderer {

	private final Theme theme;
	private final Parser parser;

	public MarkdownRenderer(Theme theme) {
		this.theme = theme;
		this.parser = Parser.builder().build();
	}

	/**
	 * Parses markdown and renders it to ANSI-styled lines wrapped to the given width.
	 */
	public List<String> render(String markdown, int width) {
		if (markdown == null || markdown.isEmpty()) {
			return List.of("");
		}

		Node document = parser.parse(markdown);
		RenderVisitor visitor = new RenderVisitor(theme, width);
		document.accept(visitor);
		return visitor.getLines();
	}

	private static class ListContext {
		final boolean ordered;
		int counter;

		ListContext(boolean ordered, int startNumber) {
			this.ordered = ordered;
			this.counter = startNumber;
		}
	}

	private static class RenderVisitor extends AbstractVisitor {

		private final Theme theme;
		private final int width;
		private final List<String> lines = new ArrayList<>();
		private final Deque<String> styleStack = new ArrayDeque<>();
		private final Deque<ListContext> listStack = new ArrayDeque<>();
		private StringBuilder currentParagraph;
		private boolean inBlockquote = false;
		private int blockquoteDepth = 0;

		RenderVisitor(Theme theme, int width) {
			this.theme = theme;
			this.width = width;
		}

		List<String> getLines() {
			return lines;
		}

		// --- Block nodes ---

		@Override
		public void visit(Document document) {
			visitChildren(document);
			// Remove trailing blank lines
			while (!lines.isEmpty() && lines.getLast().isEmpty()) {
				lines.removeLast();
			}
		}

		@Override
		public void visit(Heading heading) {
			startParagraph();
			String style;
			int level = heading.getLevel();
			switch (level) {
				case 1 -> style = theme.mdHeading1();
				case 2 -> style = theme.mdHeading2();
				default -> style = theme.mdHeading3();
			}
			styleStack.push(style);
			visitChildren(heading);
			String text = flushParagraph();
			styleStack.pop();

			lines.add(style + text + RESET);
			lines.add("");
		}

		@Override
		public void visit(Paragraph paragraph) {
			startParagraph();
			visitChildren(paragraph);
			String text = flushParagraph();

			if (text.isEmpty()) return;

			int effectiveWidth = width;
			String linePrefix = "";
			if (inBlockquote) {
				linePrefix = theme.mdBlockquote() + "│ " + RESET;
				effectiveWidth = width - 2;
			}

			// Inside a list item, paragraph content is handled by ListItem visitor
			if (!listStack.isEmpty()) {
				// Rewrite to currentParagraph so ListItem can pick it up
				appendToParagraph(text);
				return;
			}

			for (String wrapped : TextWrapper.wrap(applyCurrentStyle(text), effectiveWidth)) {
				lines.add(linePrefix + wrapped);
			}
			lines.add("");
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			String lang = fencedCodeBlock.getInfo();
			if (lang != null && !lang.isEmpty()) {
				lines.add("  " + theme.mdCodeLang() + lang + RESET);
			}

			String literal = fencedCodeBlock.getLiteral();
			if (literal != null) {
				if (literal.endsWith("\n")) {
					literal = literal.substring(0, literal.length() - 1);
				}
				for (String codeLine : literal.split("\n", -1)) {
					lines.add("  " + theme.mdCodeBlock() + codeLine + RESET);
				}
			}
			lines.add("");
		}

		@Override
		public void visit(IndentedCodeBlock indentedCodeBlock) {
			String literal = indentedCodeBlock.getLiteral();
			if (literal != null) {
				if (literal.endsWith("\n")) {
					literal = literal.substring(0, literal.length() - 1);
				}
				for (String codeLine : literal.split("\n", -1)) {
					lines.add("  " + theme.mdCodeBlock() + codeLine + RESET);
				}
			}
			lines.add("");
		}

		@Override
		public void visit(BlockQuote blockQuote) {
			blockquoteDepth++;
			boolean wasInBlockquote = inBlockquote;
			inBlockquote = true;
			visitChildren(blockQuote);
			blockquoteDepth--;
			if (blockquoteDepth == 0) {
				inBlockquote = wasInBlockquote;
			}
		}

		@Override
		public void visit(BulletList bulletList) {
			listStack.push(new ListContext(false, 0));
			visitChildren(bulletList);
			listStack.pop();
			if (listStack.isEmpty()) {
				lines.add("");
			}
		}

		@Override
		public void visit(OrderedList orderedList) {
			listStack.push(new ListContext(true, orderedList.getStartNumber()));
			visitChildren(orderedList);
			listStack.pop();
			if (listStack.isEmpty()) {
				lines.add("");
			}
		}

		@Override
		public void visit(ListItem listItem) {
			ListContext ctx = listStack.peek();
			if (ctx == null) {
				visitChildren(listItem);
				return;
			}

			int indent = (listStack.size() - 1) * 2;
			String indentStr = indent > 0 ? " ".repeat(indent) : "";

			String marker;
			int markerVisLen;
			if (ctx.ordered) {
				String num = String.valueOf(ctx.counter);
				marker = theme.mdListMarker() + num + ". " + RESET;
				markerVisLen = num.length() + 2;
				ctx.counter++;
			} else {
				marker = theme.mdListMarker() + "- " + RESET;
				markerVisLen = 2;
			}

			// Collect the text content from children
			startParagraph();
			visitChildren(listItem);
			String text = flushParagraph();

			if (!text.isEmpty()) {
				int contentWidth = width - indent - markerVisLen;
				if (contentWidth < 1) contentWidth = 1;

				List<String> wrapped = TextWrapper.wrap(applyCurrentStyle(text), contentWidth);
				boolean first = true;
				for (String wl : wrapped) {
					if (first) {
						lines.add(indentStr + marker + wl);
						first = false;
					} else {
						lines.add(indentStr + " ".repeat(markerVisLen) + wl);
					}
				}
			}
		}

		@Override
		public void visit(ThematicBreak thematicBreak) {
			int ruleWidth = Math.max(1, width);
			lines.add(theme.mdHorizontalRule() + "─".repeat(ruleWidth) + RESET);
			lines.add("");
		}

		// --- Inline nodes ---

		@Override
		public void visit(Text text) {
			appendToParagraph(text.getLiteral());
		}

		@Override
		public void visit(SoftLineBreak softLineBreak) {
			appendToParagraph(" ");
		}

		@Override
		public void visit(HardLineBreak hardLineBreak) {
			appendToParagraph("\n");
		}

		@Override
		public void visit(Code code) {
			appendToParagraph(theme.mdCodeInline() + code.getLiteral() + RESET + currentStyleCodes());
		}

		@Override
		public void visit(Emphasis emphasis) {
			styleStack.push(theme.mdItalic());
			appendToParagraph(theme.mdItalic());
			visitChildren(emphasis);
			styleStack.pop();
			appendToParagraph(RESET + currentStyleCodes());
		}

		@Override
		public void visit(StrongEmphasis strongEmphasis) {
			styleStack.push(theme.mdBold());
			appendToParagraph(theme.mdBold());
			visitChildren(strongEmphasis);
			styleStack.pop();
			appendToParagraph(RESET + currentStyleCodes());
		}

		@Override
		public void visit(Link link) {
			// Save current paragraph state
			StringBuilder saved = currentParagraph;
			currentParagraph = new StringBuilder();
			visitChildren(link);
			String linkText = currentParagraph.toString();
			currentParagraph = saved;

			String url = link.getDestination();
			appendToParagraph(linkText + " (" + theme.mdLink() + url + RESET + currentStyleCodes() + ")");
		}

		@Override
		public void visit(Image image) {
			String alt = image.getTitle() != null ? image.getTitle() : "image";
			appendToParagraph("[" + alt + "]");
		}

		// --- Helpers ---

		private void startParagraph() {
			if (currentParagraph == null) {
				currentParagraph = new StringBuilder();
			}
		}

		private void appendToParagraph(String text) {
			if (currentParagraph == null) {
				currentParagraph = new StringBuilder();
			}
			currentParagraph.append(text);
		}

		private String flushParagraph() {
			if (currentParagraph == null) return "";
			String text = currentParagraph.toString();
			currentParagraph = null;
			return text;
		}

		private String currentStyleCodes() {
			StringBuilder sb = new StringBuilder();
			for (String style : styleStack) {
				sb.append(style);
			}
			return sb.toString();
		}

		private String applyCurrentStyle(String text) {
			String styles = currentStyleCodes();
			if (styles.isEmpty()) return text;
			return styles + text + RESET;
		}
	}
}
