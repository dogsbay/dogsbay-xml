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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of supported document formats. Formats are registered at startup
 * and looked up by filename when documents are opened.
 *
 * Registration order determines priority: the first format whose
 * {@link DocumentFormat#matches(String)} returns true wins.
 */
public class DocumentFormatRegistry {

    private static final List<DocumentFormat> formats = new ArrayList<>();
    private static DocumentFormat defaultFormat = null;

    /**
     * Registers a document format. Formats registered earlier take priority
     * when multiple formats match the same extension.
     */
    public static void register(DocumentFormat format) {
        formats.add(format);
    }

    /**
     * Sets the fallback format used when no registered format matches.
     */
    public static void setDefault(DocumentFormat format) {
        defaultFormat = format;
    }

    /**
     * Returns the fallback format (typically plain text).
     */
    public static DocumentFormat getDefault() {
        return defaultFormat;
    }

    /**
     * Detects the document format by filename. Returns the first matching
     * registered format, or the default format if none match.
     */
    public static DocumentFormat detect(String filename) {
        if (filename != null) {
            for (DocumentFormat format : formats) {
                if (format.matches(filename)) {
                    return format;
                }
            }
        }
        return defaultFormat;
    }

    /**
     * Returns all registered formats (unmodifiable).
     */
    public static List<DocumentFormat> getAll() {
        return Collections.unmodifiableList(formats);
    }

    /**
     * Finds a registered format by content type string.
     */
    public static DocumentFormat findByContentType(String contentType) {
        for (DocumentFormat format : formats) {
            if (format.getContentType().equals(contentType)) {
                return format;
            }
        }
        return defaultFormat;
    }

    /**
     * Finds a registered format by display name.
     */
    public static DocumentFormat findByName(String name) {
        for (DocumentFormat format : formats) {
            if (format.getName().equals(name)) {
                return format;
            }
        }
        return null;
    }

    /**
     * Clears all registered formats. Primarily for testing.
     */
    public static void clear() {
        formats.clear();
        defaultFormat = null;
    }
}
