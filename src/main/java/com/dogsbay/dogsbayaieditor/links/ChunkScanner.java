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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts DITA 1.3 chunking (`@chunk`) usages from a map in one hardened SAX pass
 * (same hardening as {@link LinkExtractor}). A {@code chunk_audit} validates the token
 * values against the DITA 1.3 vocabulary and flags conflicting combinations.
 */
public final class ChunkScanner {

    /** One {@code @chunk} attribute occurrence. */
    public record ChunkUse(String element, String value, int line) {}

    private ChunkScanner() {}

    /** Scan a file on disk; unreadable/malformed files yield an empty list. */
    public static List<ChunkUse> scan(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return scan(new InputSource(in));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Scan in-memory content (editor buffers, tests). */
    public static List<ChunkUse> scan(String content) {
        return scan(new InputSource(new StringReader(content)));
    }

    private static List<ChunkUse> scan(InputSource input) {
        Handler handler = new Handler();
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (Exception e) {
            // Malformed mid-edit files are normal — keep what we have.
        }
        return handler.uses;
    }

    private static final class Handler extends DefaultHandler {
        final List<ChunkUse> uses = new ArrayList<>();
        private Locator locator;

        @Override
        public void setDocumentLocator(Locator l) {
            this.locator = l;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            String chunk = a.getValue("chunk");
            if (chunk != null && !chunk.trim().isEmpty()) {
                String name = localName != null && !localName.isEmpty() ? localName : qName;
                uses.add(new ChunkUse(name, chunk.trim(),
                        locator != null ? locator.getLineNumber() : -1));
            }
        }
    }

}
