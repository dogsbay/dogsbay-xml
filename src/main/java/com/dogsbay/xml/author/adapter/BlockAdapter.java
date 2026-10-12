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

package com.dogsbay.xml.author.adapter;

import org.dom4j.Element;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;

/**
 * Converts between a parsed XML tree and the block model. Implementations
 * must be lossless: markup they cannot represent structurally lands in
 * raw-XML blocks and is re-emitted verbatim on export.
 */
public interface BlockAdapter {

    /** Builds a block document from a parsed root element. Never fails on valid XML. */
    AuthorDocument importDocument(Element root);

    /** Serializes the block tree back to a detached element tree. */
    Element exportDocument(AuthorDocument doc);

    /**
     * Imports one element as a block destined for {@code parent} (a paste),
     * without attaching it; markup that does not fit becomes a raw block.
     */
    AuthorBlock importFragment(AuthorDocument doc, Element element, AuthorBlock parent);

    /** Serializes one block and its subtree (a copy). */
    Element exportFragment(AuthorBlock block);

    /**
     * Whether inline formatting (bold, semantic phrases, ...) survives export.
     * When false the UI disables formatting actions so styling is never
     * silently dropped.
     */
    default boolean supportsInlineStyles() {
        return true;
    }
}
