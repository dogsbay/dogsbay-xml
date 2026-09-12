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

package com.dogsbay.xml.author.spi;

import java.net.URI;

import org.dom4j.Element;

/**
 * Resolves DITA references for display: key text, href targets, conref
 * content. The Author panel renders best-effort with whatever the host
 * provides — every method may return null ("unknown"), and {@link #NONE}
 * resolves nothing.
 */
public interface ReferenceResolver {

    ReferenceResolver NONE = new ReferenceResolver() {
    };

    /** Display text for a key ({@code <keyword keyref="k"/>}), or null. */
    default String keyText(String key) {
        return null;
    }

    /** The href a key points at (for {@code <image keyref=...>}), or null. */
    default String keyHref(String key) {
        return null;
    }

    /** Absolute URI for an href, resolved against the document, or null. */
    default URI resolveHref(String href) {
        return null;
    }

    /** The element a conref points at ({@code file.dita#topic/id}), or null. */
    default Element resolveConref(String conref) {
        return null;
    }
}
