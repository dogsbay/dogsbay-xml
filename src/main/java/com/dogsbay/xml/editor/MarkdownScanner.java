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

package com.dogsbay.xml.editor;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterBlock;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Scanner for Markdown content based on flexmark-java AST.
 */
public class MarkdownScanner {
	private static final Parser PARSER;

	static {
		MutableDataSet options = new MutableDataSet();
		options.set(Parser.EXTENSIONS, Arrays.asList(
				TablesExtension.create(),
				StrikethroughExtension.create(),
				TaskListExtension.create(),
				AutolinkExtension.create(),
				YamlFrontMatterExtension.create()));
		PARSER = Parser.builder(options).build();
	}

	private final Document document;
	private List<Token> tokens = Collections.emptyList();
	private Token currentToken = null;
	private int nextIndex = 0;

	public int token = -1;
	public boolean error = false;

	public MarkdownScanner(Document document) throws IOException {
		this.document = document;
		setRange(0, document.getLength());
	}

	public void setRange(int start, int end) throws IOException {
		buildTokens();
		positionTo(start);
	}

	public int getStartOffset() {
		if (currentToken == null) {
			return 0;
		}
		return currentToken.start;
	}

	public int getEndOffset() {
		if (currentToken == null) {
			return Integer.MAX_VALUE;
		}
		return currentToken.end;
	}

	public long scan() throws IOException {
		if (nextIndex >= tokens.size()) {
			currentToken = null;
			token = -1;
			return 0;
		}

		currentToken = tokens.get(nextIndex++);
		token = currentToken.type;
		return 0;
	}

	public void cleanup() {
		tokens = Collections.emptyList();
		currentToken = null;
	}

	private void positionTo(int start) {
		nextIndex = 0;
		currentToken = null;
		token = -1;

		for (int i = 0; i < tokens.size(); i++) {
			Token t = tokens.get(i);
			if (t.end > start) {
				currentToken = t;
				token = t.type;
				nextIndex = i + 1;
				return;
			}
		}
	}

	private void buildTokens() throws IOException {
		String text;
		try {
			text = document.getText(0, document.getLength());
		} catch (BadLocationException e) {
			throw new IOException(e);
		}

		Node root = PARSER.parse(text);
		List<Span> spans = new ArrayList<>();
		collectSpans(root, spans);
		tokens = buildTokenList(spans, text.length());
	}

	private void collectSpans(Node node, List<Span> spans) {
		if (node == null) {
			return;
		}

		int start = node.getStartOffset();
		int end = node.getEndOffset();

		int type = -1;
		int priority = 0;

		if (node instanceof Heading) {
			type = Constants.MD_HEADER;
			priority = 60;
		} else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
			type = Constants.MD_CODE_BLOCK;
			priority = 120;
		} else if (node instanceof Code) {
			type = Constants.MD_CODE;
			priority = 110;
		} else if (node instanceof Link || node instanceof LinkRef) {
			type = Constants.MD_LINK;
			priority = 90;
		} else if (node instanceof AutoLink) {
			type = Constants.MD_URL;
			priority = 90;
		} else if (node instanceof Image || node instanceof ImageRef) {
			type = Constants.MD_IMAGE;
			priority = 90;
		} else if (node instanceof BlockQuote) {
			type = Constants.MD_BLOCKQUOTE;
			priority = 50;
		} else if (node instanceof BulletList || node instanceof OrderedList || node instanceof ListItem) {
			type = Constants.MD_LIST;
			priority = 55;
		} else if (node instanceof ThematicBreak) {
			type = Constants.MD_RULE;
			priority = 85;
		} else if (node instanceof TableBlock || node instanceof TableHead || node instanceof TableBody
				|| node instanceof TableRow || node instanceof TableCell) {
			type = Constants.MD_TABLE;
			priority = 60;
		} else if (node instanceof Strikethrough) {
			type = Constants.MD_STRIKETHROUGH;
			priority = 75;
		} else if (node instanceof TaskListItem) {
			type = Constants.MD_TASK;
			priority = 65;
		} else if (node instanceof HtmlInline || node instanceof HtmlBlock) {
			type = Constants.MD_HTML;
			priority = 100;
		} else if (node instanceof YamlFrontMatterBlock) {
			type = Constants.MD_YAML;
			priority = 95;
		} else if (node instanceof com.vladsch.flexmark.ast.Emphasis) {
			type = Constants.MD_EMPHASIS;
			priority = 80;
		} else if (node instanceof com.vladsch.flexmark.ast.StrongEmphasis) {
			type = Constants.MD_STRONG;
			priority = 85;
		}

		if (type != -1 && start >= 0 && end > start) {
			spans.add(new Span(start, end, type, priority));
		}

		for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
			collectSpans(child, spans);
		}
	}

	private List<Token> buildTokenList(List<Span> spans, int length) {
		if (length <= 0) {
			return Collections.emptyList();
		}

		List<Event> events = new ArrayList<>();
		for (Span span : spans) {
			if (span.start < span.end) {
				events.add(new Event(span.start, true, span));
				events.add(new Event(span.end, false, span));
			}
		}

		Collections.sort(events, new Comparator<Event>() {
			@Override
			public int compare(Event a, Event b) {
				if (a.pos != b.pos) {
					return Integer.compare(a.pos, b.pos);
				}
				if (a.isStart != b.isStart) {
					return a.isStart ? 1 : -1; // end before start at same pos
				}
				return 0;
			}
		});

		TreeSet<Span> active = new TreeSet<>(new Comparator<Span>() {
			@Override
			public int compare(Span a, Span b) {
				if (a == b) {
					return 0;
				}
				if (a.priority != b.priority) {
					return Integer.compare(b.priority, a.priority);
				}
				if (a.start != b.start) {
					return Integer.compare(a.start, b.start);
				}
				if (a.end != b.end) {
					return Integer.compare(b.end, a.end);
				}
				return Integer.compare(a.id, b.id);
			}
		});

		List<Token> result = new ArrayList<>();
		int pos = 0;
		int i = 0;

		while (i < events.size()) {
			int nextPos = events.get(i).pos;
			if (nextPos > pos) {
				int type = active.isEmpty() ? Constants.MD_TEXT : active.first().type;
				result.add(new Token(pos, nextPos, type));
				pos = nextPos;
			}

			while (i < events.size() && events.get(i).pos == nextPos) {
				Event event = events.get(i);
				if (event.isStart) {
					active.add(event.span);
				} else {
					active.remove(event.span);
				}
				i++;
			}
		}

		if (pos < length) {
			int type = active.isEmpty() ? Constants.MD_TEXT : active.first().type;
			result.add(new Token(pos, length, type));
		}

		return result;
	}

	private static class Token {
		private final int start;
		private final int end;
		private final int type;

		private Token(int start, int end, int type) {
			this.start = start;
			this.end = end;
			this.type = type;
		}
	}

	private static class Span {
		private static int counter = 0;

		private final int id;
		private final int start;
		private final int end;
		private final int type;
		private final int priority;

		private Span(int start, int end, int type, int priority) {
			this.id = counter++;
			this.start = start;
			this.end = end;
			this.type = type;
			this.priority = priority;
		}
	}

	private static class Event {
		private final int pos;
		private final boolean isStart;
		private final Span span;

		private Event(int pos, boolean isStart, Span span) {
			this.pos = pos;
			this.isStart = isStart;
			this.span = span;
		}
	}
}
