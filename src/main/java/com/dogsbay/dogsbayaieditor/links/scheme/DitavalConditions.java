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

package com.dogsbay.dogsbayaieditor.links.scheme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads the {@code <prop att val action>} entries of a DITAVAL file — a value-only
 * view used to validate ditaval condition values against a {@link SubjectScheme}.
 *
 * <p>Deliberately GUI-free and standalone: the existing {@code DitavalFilter} lives
 * in {@code xml.browser} (forbidden to dita-core), and we only need att/val/line,
 * not the DOM-stripping behaviour. Parsed with a {@link Locator} so each condition
 * carries its source line; external DTD/entities are disabled.
 */
public final class DitavalConditions {

    private static final Logger LOG = LoggerFactory.getLogger(DitavalConditions.class);

    /** One {@code <prop>} entry. */
    public record Condition(String att, String val, String action, int line) {}

    private DitavalConditions() {}

    /** Read all {@code <prop>} conditions from a DITAVAL file; empty on error. */
    public static List<Condition> read(File ditaval) {
        List<Condition> out = new ArrayList<>();
        if (ditaval == null || !ditaval.isFile()) {
            return out;
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(ditaval, new PropHandler(out));
        } catch (Exception e) {
            LOG.debug("Could not read DITAVAL {}: {}", ditaval, e.toString());
        }
        return out;
    }

    private static void trySetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception e) {
            LOG.debug("SAX feature {} not supported by the parser", feature);
        }
    }

    /** Collects {@code <prop>} att/val/action with the source line from a Locator. */
    private static final class PropHandler extends DefaultHandler {
        private final List<Condition> out;
        private Locator locator;

        PropHandler(List<Condition> out) {
            this.out = out;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (!"prop".equals(name)) {
                return;
            }
            String att = attrs.getValue("att");
            String val = attrs.getValue("val");
            if (att == null || val == null) {
                return; // catch-all props (no att/val) carry no controlled value
            }
            int line = locator != null ? locator.getLineNumber() : -1;
            out.add(new Condition(att, val, attrs.getValue("action"), line));
        }
    }
}
