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

import java.awt.Color;
import java.awt.Font;

import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * Editor kit for Markdown content with syntax highlighting.
 */
public class MarkdownEditorKit extends DogsBayEditorKit {

	private static Font font = null;

	private XmlContext context = null;
	private MarkdownViewFactory factory = null;
	private XmlEditorPane editor = null;

	public MarkdownEditorKit(XmlEditorPane editor) {
		super();

		this.editor = editor;

		factory = new MarkdownViewFactory();
		context = new XmlContext();
	}

	@Override
	public String getContentType() {
		return "text/markdown";
	}

	public void setFont(Font font) {
		MarkdownEditorKit.font = font;
		context.setFont(font);
	}

	public Font getFont() {
		return font;
	}

	public void setHighlight(boolean enabled) {
		factory.setHighlight(enabled);
	}

	public boolean isHighlight() {
		return factory.isHighlight();
	}

	public void setErrorHighlighting(boolean enabled) {
		// Markdown highlighting does not use XML error overlays.
	}

	public boolean isErrorHighlighting() {
		return false;
	}

	public void setAttributes(int id, Color color, int style) {
		context.setAttributes(id, color, style);
	}

	@Override
	public Document createDefaultDocument() {
		return new XmlDocument(editor, new BufferContent(1024));
	}

	@Override
	public final ViewFactory getViewFactory() {
		return factory;
	}

	public void cleanup() {
		finalize();
	}

	protected void finalize() {
		context.cleanup();
		context = null;
		factory = null;
		editor = null;
	}

	class MarkdownViewFactory implements ViewFactory {
		private View current = null;
		private boolean highlight = false;

		public View create(Element elem) {
			if (editor.isWrapped()) {
				current = new MarkdownWrappedView(context, elem);
				((MarkdownWrappedView) current).setHighlight(highlight);
			} else {
				current = new MarkdownView(context, elem);
				((MarkdownView) current).setHighlight(highlight);
			}

			return current;
		}

		public void setHighlight(boolean enabled) {
			highlight = enabled;

			if (current instanceof MarkdownView) {
				((MarkdownView) current).setHighlight(enabled);
			}

			if (current instanceof MarkdownWrappedView) {
				((MarkdownWrappedView) current).setHighlight(enabled);
			}
		}

		public boolean isHighlight() {
			return highlight;
		}
	}
}
