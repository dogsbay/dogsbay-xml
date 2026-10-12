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

import javax.swing.JComponent;
import javax.swing.JToolBar;

import com.dogsbay.xml.browser.MarkdownConverter;
import com.dogsbay.xml.viewer.MarkdownOutlinePanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * Document format for Markdown files (.md, .markdown, .mdown, .mkd, .mkdn).
 */
public class MarkdownDocumentFormat implements DocumentFormat {

    @Override
    public String getName() {
        return "Markdown";
    }

    @Override
    public String getContentType() {
        return "text/markdown";
    }

    @Override
    public String[] getExtensions() {
        return new String[] { "md", "markdown", "mdown", "mkd", "mkdn" };
    }

    @Override
    public DogsBayEditorKit createEditorKit(XmlEditorPane pane) {
        return new MarkdownEditorKit(pane);
    }

    @Override
    public JToolBar createToolbar() {
        return new MarkdownToolbar();
    }

    @Override
    public JComponent createOutlinePanel(Object parent) {
        if (parent instanceof DogsBayAIEditor) {
            return new MarkdownOutlinePanel((DogsBayAIEditor) parent);
        }
        return null;
    }

    @Override
    public String convertToHtml(String sourceText) {
        return MarkdownConverter.convert(sourceText);
    }
}
