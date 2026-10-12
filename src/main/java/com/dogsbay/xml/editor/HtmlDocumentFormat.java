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

/**
 * Document format for HTML files (.html, .htm).
 * Uses the XML editor kit for syntax highlighting.
 * Preview shows the HTML content directly.
 */
public class HtmlDocumentFormat implements DocumentFormat {

    @Override
    public String getName() {
        return "HTML";
    }

    @Override
    public String getContentType() {
        return "text/xml";
    }

    @Override
    public String[] getExtensions() {
        return new String[] { "html", "htm" };
    }

    @Override
    public DogsBayEditorKit createEditorKit(XmlEditorPane pane) {
        return new XmlEditorKit(pane, null);
    }

    @Override
    public String convertToHtml(String sourceText) {
        // HTML is already HTML — pass through directly
        return sourceText;
    }

    @Override
    public boolean isXmlBased() {
        return true;
    }
}
