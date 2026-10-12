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

import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * One place for the project's hardened, non-validating SAX setup: no external DTD
 * fetching, no external general/parameter entities, and an entity resolver that returns
 * empty for every external id — so the scanners parse offline and aren't exposed to XXE.
 * Shared by every {@code links} scanner ({@link LinkExtractor}, {@link IndextermScanner},
 * {@link GlossaryScanner}, {@link ConrefPushScanner}, {@link ChunkScanner},
 * {@link SpecializationScanner}); a hardening change is made here once.
 */
public final class HardenedSax {

    private HardenedSax() {}

    /** A namespace-aware, non-validating, externally-inert {@link XMLReader}. */
    public static XMLReader newReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        // Without a handler the JDK parser prints "[Fatal Error] ..." to stderr for
        // every malformed file a scan meets, then throws anyway. Throw quietly.
        reader.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        return reader;
    }

    private static void trySetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // the EntityResolver still guards even if a feature isn't supported
        }
    }
}
