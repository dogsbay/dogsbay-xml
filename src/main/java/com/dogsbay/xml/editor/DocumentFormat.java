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

import java.io.File;

import javax.swing.JComponent;
import javax.swing.JToolBar;

/**
 * Abstraction for document format support (XML, Markdown, AsciiDoc, JSON, YAML, etc.).
 * Each format provides its own editor kit, toolbar, outline panel, and preview conversion.
 *
 * Implementations are registered with {@link DocumentFormatRegistry} and looked up
 * by file extension when a document is opened.
 */
public interface DocumentFormat {

    /**
     * Display name shown in UI (e.g. "XML", "Markdown", "AsciiDoc").
     */
    String getName();

    /**
     * Swing content type string used to select the EditorKit
     * (e.g. "text/xml", "text/markdown").
     */
    String getContentType();

    /**
     * File extensions this format handles, without the leading dot
     * (e.g. "md", "markdown").
     */
    String[] getExtensions();

    /**
     * Returns true if this format matches the given filename.
     * The default implementation checks extensions case-insensitively.
     */
    default boolean matches(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : getExtensions()) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the EditorKit that provides syntax highlighting for this format.
     *
     * @param pane the editor pane this kit will be installed on
     * @return a new EditorKit instance
     */
    DogsBayEditorKit createEditorKit(XmlEditorPane pane);

    /**
     * Creates the format-specific editing toolbar (bold, italic, heading, etc.),
     * or null if this format has no special toolbar.
     */
    default JToolBar createToolbar() {
        return null;
    }

    /**
     * Creates the outline panel for the sidebar (section headings, DOM tree, etc.),
     * or null if this format has no outline support.
     */
    default JComponent createOutlinePanel(Object parent) {
        return null;
    }

    /**
     * Converts the source text to HTML for the preview panel.
     * Return null if this format does not support preview conversion.
     */
    default String convertToHtml(String sourceText) {
        return null;
    }

    /**
     * Converts the source text to HTML for the preview panel, with a base
     * directory for resolving relative paths and includes.
     * Return null if this format does not support preview conversion.
     *
     * @param sourceText the raw source text
     * @param baseDir the directory containing the source file (for resolving includes), or null
     */
    default String convertToHtml(String sourceText, File baseDir) {
        return convertToHtml(sourceText);
    }

    /**
     * Converts the source text to HTML with additional rendering context
     * (e.g. a DITA context map for key resolution, a DITAVAL filter).
     * Formats that don't use the context fall back to the 2-arg form.
     *
     * @param sourceText the raw source text
     * @param baseDir the directory containing the source file, or null
     * @param options rendering context; may be null
     */
    default String convertToHtml(String sourceText, File baseDir,
                                 com.dogsbay.xml.browser.PreviewOptions options) {
        return convertToHtml(sourceText, baseDir);
    }

    /**
     * Whether this format should use the XML parser and DOM model.
     * True for XML and DTD; false for plain-text formats like Markdown.
     */
    default boolean isXmlBased() {
        return false;
    }

    /**
     * Whether this format supports the Designer (schema-aware graphical) view.
     */
    default boolean supportsDesigner() {
        return false;
    }

    /**
     * Whether this format supports the tree-based Viewer.
     */
    default boolean supportsViewer() {
        return false;
    }

    /**
     * Whether this format supports schema validation.
     */
    default boolean supportsValidation() {
        return false;
    }
}
