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

import javax.swing.JComponent;

import com.dogsbay.xml.editor.DocumentFormat;
import com.dogsbay.xml.editor.DogsBayEditorKit;
import com.dogsbay.xml.editor.XmlEditorPane;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * Document format for YAML files (.yml, .yaml).
 */
public class YamlDocumentFormat implements DocumentFormat {

    @Override public String getName() { return "YAML"; }
    @Override public String getContentType() { return "text/yaml"; }
    @Override public String[] getExtensions() { return new String[] { "yml", "yaml" }; }

    @Override
    public DogsBayEditorKit createEditorKit(XmlEditorPane pane) {
        return new YamlEditorKit(pane);
    }

    @Override
    public JComponent createOutlinePanel(Object parent) {
        if (parent instanceof DogsBayAIEditor) {
            return new YamlOutlinePanel((DogsBayAIEditor) parent);
        }
        return null;
    }

    @Override
    public String convertToHtml(String sourceText) {
        if (sourceText == null || sourceText.isEmpty()) return "";
        return "<pre style=\"font-family: monospace; white-space: pre-wrap;\">"
                + sourceText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</pre>";
    }
}
