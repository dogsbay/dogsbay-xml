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

package com.dogsbay.xml.viewer;

import com.dogsbay.xml.DogsBayDocument;

/**
 * Interface for format-specific outline panels. Implementations provide
 * a tree/list view of document structure (headings, keys, sections, etc.)
 * and are created by {@link com.dogsbay.xml.editor.DocumentFormat#createOutlinePanel}.
 *
 * The implementing class must also be a {@link javax.swing.JComponent}
 * (typically extends JPanel or ViewTreePanel).
 */
public interface FormatOutlinePanel {

    /** Set the document to display in the outline. Null clears the outline. */
    void setDocument(DogsBayDocument document);

    /** Expand all nodes in the outline tree. */
    void expandAll();

    /** Collapse all nodes in the outline tree. */
    void collapseAll();

    /** Request focus on this panel. */
    void setFocus();

    /** Update visual preferences (fonts, colors). */
    void updatePreferences();

    /** Release resources. */
    void cleanup();
}
