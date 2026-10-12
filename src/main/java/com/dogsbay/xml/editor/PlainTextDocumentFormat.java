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
 * Document format for plain text files. This is the default/fallback
 * format used when no other registered format matches the file extension.
 * Provides no syntax highlighting, toolbar, outline, or preview.
 */
public class PlainTextDocumentFormat implements DocumentFormat {

    @Override
    public String getName() {
        return "Plain Text";
    }

    @Override
    public String getContentType() {
        return "text/txt";
    }

    @Override
    public String[] getExtensions() {
        return new String[] { "txt", "text", "log", "cfg", "conf", "ini", "properties" };
    }

    @Override
    public DogsBayEditorKit createEditorKit(XmlEditorPane pane) {
        return new TextEditorKit(pane);
    }
}
