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

package com.dogsbay.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

/**
 * One place for hardening XML parser and transformer factories against
 * external-entity (XXE) and external-resource (SSRF) attacks, while leaving
 * catalog-based grammar resolution to the caller's {@code EntityResolver}.
 * The SAX counterpart for the links scanners is
 * {@link com.dogsbay.dogsbayaieditor.links.HardenedSax}; a hardening change
 * is made in these two classes once.
 */
public final class SecureXml {

    private SecureXml() {}

    /**
     * Hardens a DOM factory against XXE. Blocks external <em>general</em> entities
     * (the classic {@code <!ENTITY x SYSTEM "file:///etc/passwd">} file-read vector)
     * and enables secure processing. Legitimate content entities in grammar-driven
     * documents (DITA {@code &trade;}, XHTML {@code &nbsp;}) are <em>internal</em>
     * entities declared inside a DTD module, so blocking external general entities
     * does not affect them.
     *
     * <p>When {@code allowDoctype} is false the DOCTYPE declaration is rejected
     * outright and all external DTD / parameter-entity loading is disabled — use it
     * for inputs that never legitimately carry a DOCTYPE. When {@code allowDoctype}
     * is true this method deliberately leaves external-DTD loading and parameter
     * entities at the caller's settings so catalog-resolved grammars (DITA) can load
     * their entity modules; the caller is responsible for installing a catalog
     * {@code EntityResolver} that constrains what external ids resolve to.
     */
    public static void hardenDom(DocumentBuilderFactory factory, boolean allowDoctype) {
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);

        if (!allowDoctype) {
            trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        }

        factory.setXIncludeAware(false);
    }

    /**
     * Blocks external DTD and stylesheet access on a transformer factory so a
     * stylesheet (or a document's associated-stylesheet PI) cannot read local
     * files or reach remote hosts via {@code document()}, {@code xsl:import},
     * or {@code xsl:include}.
     */
    public static void hardenTransformerFactory(TransformerFactory factory) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception ignored) {
            // best effort — the attributes below still apply where supported
        }

        trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    }

    /**
     * Enables secure processing on a transformer factory WITHOUT blocking external
     * stylesheet/DTD access. Use for the interactive editor transform, where a
     * document's associated-stylesheet PI or a stylesheet's {@code xsl:import} may
     * legitimately reference a local file the user chose to transform — full
     * blocking ({@link #hardenTransformerFactory}) is for the untrusted tool surface.
     */
    public static void enableSecureProcessing(TransformerFactory factory) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // not supported by this parser — the remaining features still guard
        }
    }

    private static void trySetAttribute(TransformerFactory factory, String attribute, String value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (Exception ignored) {
            // not supported by this transformer implementation
        }
    }
}
