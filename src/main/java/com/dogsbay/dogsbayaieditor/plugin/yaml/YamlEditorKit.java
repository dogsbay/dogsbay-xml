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

package com.dogsbay.dogsbayaieditor.plugin.yaml;

import java.awt.Color;
import java.awt.Font;

import javax.swing.UIManager;
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
 * Editor kit for YAML content with syntax highlighting.
 */
public class YamlEditorKit extends DogsBayEditorKit {

    private static Font font = null;
    private XmlContext context = null;
    private YamlViewFactory factory = null;
    private XmlEditorPane editor = null;

    public YamlEditorKit(XmlEditorPane editor) {
        super();
        this.editor = editor;
        factory = new YamlViewFactory();
        context = new XmlContext();
        initStyles();
    }

    private void initStyles() {
        Color defaultFg = UIManager.getColor("TextPane.foreground");
        if (defaultFg == null) defaultFg = Color.BLACK;

        context.setAttributes(Constants.YAML_KEY, new Color(0x569CD6), Font.BOLD);        // Blue bold
        context.setAttributes(Constants.YAML_STRING, new Color(0x6A9955), Font.PLAIN);    // Green
        context.setAttributes(Constants.YAML_NUMBER, new Color(0xB5CEA8), Font.PLAIN);    // Light green
        context.setAttributes(Constants.YAML_KEYWORD, new Color(0xC586C0), Font.BOLD);    // Purple bold
        context.setAttributes(Constants.YAML_PUNCTUATION, defaultFg, Font.PLAIN);         // Default
        context.setAttributes(Constants.YAML_COMMENT, new Color(0x608B4E), Font.ITALIC);  // Green italic
        context.setAttributes(Constants.YAML_ANCHOR, new Color(0xD7BA7D), Font.PLAIN);    // Orange
        context.setAttributes(Constants.YAML_TAG, new Color(0xB267E6), Font.PLAIN);       // Purple
        context.setAttributes(Constants.YAML_TEXT, defaultFg, Font.PLAIN);                // Default
    }

    @Override public String getContentType() { return "text/yaml"; }
    public void setFont(Font f) { font = f; context.setFont(f); }
    public Font getFont() { return font; }
    public void setHighlight(boolean enabled) { factory.setHighlight(enabled); }
    public boolean isHighlight() { return factory.isHighlight(); }
    public void setErrorHighlighting(boolean enabled) {}
    public boolean isErrorHighlighting() { return false; }
    public void setAttributes(int id, Color color, int style) { context.setAttributes(id, color, style); }

    @Override public Document createDefaultDocument() {
        return new XmlDocument(editor, new BufferContent(1024));
    }

    @Override public final ViewFactory getViewFactory() { return factory; }

    public void cleanup() { finalize(); }
    protected void finalize() { context.cleanup(); context = null; factory = null; editor = null; }

    class YamlViewFactory implements ViewFactory {
        private View current = null;
        private boolean highlight = false;

        public View create(Element elem) {
            current = new YamlView(context, elem);
            ((YamlView) current).setHighlight(highlight);
            return current;
        }
        public void setHighlight(boolean enabled) {
            highlight = enabled;
            if (current instanceof YamlView) ((YamlView) current).setHighlight(enabled);
        }
        public boolean isHighlight() { return highlight; }
    }
}
