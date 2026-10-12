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

package com.dogsbay.xml.author.model;

/**
 * Notification of a change to an {@link AuthorDocument}. {@code block} is the
 * most specific block affected: the parent for structure changes, the edited
 * block for text/attribute changes, the new root for root changes.
 */
public record AuthorDocumentEvent(AuthorDocument document, Kind kind, AuthorBlock block) {

    public enum Kind {
        /** Children inserted, removed or reordered under {@code block}. */
        STRUCTURE,
        /** Inline text of {@code block} replaced. */
        TEXT,
        /** Attributes of {@code block} changed. */
        ATTRIBUTES,
        /** Document root replaced; {@code block} is the new root. */
        ROOT
    }
}
