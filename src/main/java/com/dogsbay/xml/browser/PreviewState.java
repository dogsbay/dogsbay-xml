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

package com.dogsbay.xml.browser;

import com.dogsbay.xml.DogsBayDocument;

import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.JViewport;

/**
 * Holds the state for an active preview: the panel, the document being previewed,
 * the debounce timer for live refresh, the Swing document reference, and the listeners.
 *
 * @author DogsBay Ltd
 */
class PreviewState {
    final HtmlPreviewPanel panel;
    final DogsBayDocument document;
    final javax.swing.text.Document swingDocument;
    final Timer debounceTimer;
    final DocumentListener swingDocListener;

    // Scroll sync state
    JViewport scrollViewport;
    ChangeListener scrollListener;

    PreviewState(HtmlPreviewPanel panel, DogsBayDocument document,
                 javax.swing.text.Document swingDocument,
                 Timer debounceTimer, DocumentListener swingDocListener) {
        this.panel = panel;
        this.document = document;
        this.swingDocument = swingDocument;
        this.debounceTimer = debounceTimer;
        this.swingDocListener = swingDocListener;
    }

    void dispose() {
        debounceTimer.stop();
        if (swingDocument != null && swingDocListener != null) {
            swingDocument.removeDocumentListener(swingDocListener);
        }
        if (scrollViewport != null && scrollListener != null) {
            scrollViewport.removeChangeListener(scrollListener);
        }
    }
}
