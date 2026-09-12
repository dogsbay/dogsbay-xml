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

package com.dogsbay.dogsbayaieditor.validate;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses an XML file into a DOM where each element carries its 1-based start
 * line (the line of its start tag) as user data. DOM doesn't track source
 * positions, so we build it from SAX and stamp the {@link Locator} line on each
 * element. External DTD loading is disabled, so DITA PUBLIC doctypes need no
 * catalog.
 *
 * <p>Used to turn a Schematron SVRL location (an XPath) into a source line for
 * click-to-open: parse here, run Schematron on this DOM, then evaluate the
 * location XPath against it and read {@link #lineOf(Node)} of the matched node.
 */
public final class LineNumberedDom {

    /** User-data key under which each element's 1-based start line is stored. */
    public static final String LINE_KEY = "dogsbay.lineNumber";

    private LineNumberedDom() {}

    /** Parse {@code file} into a DOM with per-element start lines. */
    public static Document parse(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().newDocument();

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setValidating(false);
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        spf.setFeature("http://xml.org/sax/features/validation", false);
        SAXParser parser = spf.newSAXParser();

        parser.parse(file, new Builder(doc));
        return doc;
    }

    /** The 1-based start line stamped on a node, or -1 if absent. */
    public static int lineOf(Node node) {
        if (node == null) {
            return -1;
        }
        Object v = node.getUserData(LINE_KEY);
        return (v instanceof Integer i) ? i : -1;
    }

    /** SAX handler that builds the DOM and stamps each element's start line. */
    private static final class Builder extends DefaultHandler {
        private final Document doc;
        private final Deque<Node> stack = new ArrayDeque<>();
        private Locator locator;

        Builder(Document doc) {
            this.doc = doc;
            this.stack.push(doc);
        }

        @Override
        public void setDocumentLocator(Locator l) {
            this.locator = l;
        }

        @Override
        public void startElement(String uri, String local, String qName, Attributes attrs) {
            Element e = doc.createElementNS(uri == null || uri.isEmpty() ? null : uri, qName);
            for (int i = 0; i < attrs.getLength(); i++) {
                String aUri = attrs.getURI(i);
                if (aUri == null || aUri.isEmpty()) {
                    e.setAttribute(attrs.getQName(i), attrs.getValue(i));
                } else {
                    e.setAttributeNS(aUri, attrs.getQName(i), attrs.getValue(i));
                }
            }
            if (locator != null) {
                e.setUserData(LINE_KEY, locator.getLineNumber(), null);
            }
            stack.peek().appendChild(e);
            stack.push(e);
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            stack.pop();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            stack.peek().appendChild(doc.createTextNode(new String(ch, start, length)));
        }
    }
}
