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
package com.dogsbay.dogsbayaieditor.explorer.actions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.MarkdownDocumentFormat;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;

/** When creating a file from the project tree asks what should go in it. */
class NewFileActionTest {

    @BeforeEach
    void registerFormats() {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        DocumentFormatRegistry.register(new MarkdownDocumentFormat());
        PlainTextDocumentFormat plain = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plain);
        DocumentFormatRegistry.setDefault(plain);
    }

    @AfterEach
    void forgetFormats() {
        DocumentFormatRegistry.clear();
    }

    @Test
    void theExplorerOffersAChoiceOnlyWhereTheChoicesMeanSomething() {
        // a document type, a template or a DTD are XML questions; asking them about a note would be consistency for its own sake
        assertThat(NewFileAction.isXmlKind("what-is-audacity.dita")).isTrue();
        assertThat(NewFileAction.isXmlKind("audacity.ditamap")).isTrue();
        assertThat(NewFileAction.isXmlKind("filters.ditaval")).isTrue();
        assertThat(NewFileAction.isXmlKind("project.xml")).isTrue();
        assertThat(NewFileAction.isXmlKind("notes.md")).isFalse();
        assertThat(NewFileAction.isXmlKind("README")).isFalse();
    }
}
