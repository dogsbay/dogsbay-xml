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

package com.dogsbay.dogsbayaieditor.plugin.json;

import java.awt.Color;
import java.awt.Font;

import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

import com.dogsbay.xml.editor.BufferContent;
import com.dogsbay.xml.editor.Constants;
import com.dogsbay.xml.editor.DogsBayEditorKit;
import com.dogsbay.xml.editor.XmlContext;
import com.dogsbay.xml.editor.XmlDocument;
import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * Editor kit for JSON content with syntax highlighting.
 */
public class JsonEditorKit extends DogsBayEditorKit {

    private static Font font = null;

    private XmlContext context = null;
    private JsonViewFactory factory = null;
    private XmlEditorPane editor = null;

    public JsonEditorKit(XmlEditorPane editor) {
        super();
        this.editor = editor;
        factory = new JsonViewFactory();
        context = new XmlContext();
        initJsonStyles();
    }

    private void initJsonStyles() {
        Color defaultFg = javax.swing.UIManager.getColor("TextPane.foreground");
        if (defaultFg == null) defaultFg = Color.BLACK;

        context.setAttributes(Constants.JSON_KEY, new Color(0x569CD6), Font.BOLD);       // Blue bold
        context.setAttributes(Constants.JSON_STRING, new Color(0x6A9955), Font.PLAIN);   // Green
        context.setAttributes(Constants.JSON_NUMBER, new Color(0xB5CEA8), Font.PLAIN);   // Light green
        context.setAttributes(Constants.JSON_KEYWORD, new Color(0xC586C0), Font.BOLD);   // Purple bold
        context.setAttributes(Constants.JSON_PUNCTUATION, defaultFg, Font.PLAIN);        // Default
        context.setAttributes(Constants.JSON_COMMENT, new Color(0x6A9955), Font.ITALIC); // Green italic
        context.setAttributes(Constants.JSON_ERROR, new Color(0xF44747), Font.PLAIN);    // Red
        context.setAttributes(Constants.JSON_TEXT, defaultFg, Font.PLAIN);               // Default
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    public void setFont(Font font) {
        JsonEditorKit.font = font;
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
        // JSON does not use XML error overlays
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

    class JsonViewFactory implements ViewFactory {
        private View current = null;
        private boolean highlight = false;

        public View create(Element elem) {
            current = new JsonView(context, elem);
            ((JsonView) current).setHighlight(highlight);
            return current;
        }

        public void setHighlight(boolean enabled) {
            highlight = enabled;
            if (current instanceof JsonView) {
                ((JsonView) current).setHighlight(enabled);
            }
        }

        public boolean isHighlight() {
            return highlight;
        }
    }
}
