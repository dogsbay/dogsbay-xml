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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reports the DITA specialization of a topic/map: its DOCTYPE (public/system id), root
 * element, the {@code @class} generalization chain (general → specific), and the
 * {@code @domains} it declares. This is how the editor surfaces that a team's custom
 * specialization is understood — the grammar layer already resolves custom DTDs; this
 * names what a file <em>is</em>.
 */
public final class SpecializationScanner {

    /**
     * @param root      root element local name (the most-specific structural type)
     * @param publicId  DOCTYPE public id, or null
     * @param systemId  DOCTYPE system id (the DTD), or null
     * @param classChain {@code @class} module/type tokens, general → specific
     *                  (e.g. {@code [topic/topic, concept/concept]}); empty if none
     * @param domains   {@code @domains} module ids the doctype integrates; empty if none
     */
    public record SpecInfo(String root, String publicId, String systemId,
            List<String> classChain, List<String> domains) {
        public SpecInfo {
            classChain = classChain == null ? List.of() : List.copyOf(classChain);
            domains = domains == null ? List.of() : List.copyOf(domains);
        }
        /** True when the root is a specialization of {@code topic}/{@code map} (chain &gt; 1). */
        public boolean isSpecialized() {
            return classChain.size() > 1;
        }
    }

    private static final Pattern DOCTYPE = Pattern.compile(
            "<!DOCTYPE\\s+\\S+\\s+PUBLIC\\s+\"([^\"]*)\"\\s+\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTYPE_SYSTEM = Pattern.compile(
            "<!DOCTYPE\\s+\\S+\\s+SYSTEM\\s+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private SpecializationScanner() {}

    /** Scan a file on disk; unreadable files yield a near-empty {@link SpecInfo}. */
    public static SpecInfo scan(File file) {
        String head = readHead(file);
        try (InputStream in = new FileInputStream(file)) {
            return scan(new InputSource(in), head);
        } catch (IOException e) {
            return new SpecInfo(null, null, null, List.of(), List.of());
        }
    }

    /** Scan in-memory content (editor buffers, tests). */
    public static SpecInfo scan(String content) {
        return scan(new InputSource(new StringReader(content)), content);
    }

    private static SpecInfo scan(InputSource input, String headForDoctype) {
        String publicId = null;
        String systemId = null;
        Matcher m = DOCTYPE.matcher(headForDoctype == null ? "" : headForDoctype);
        if (m.find()) {
            publicId = m.group(1);
            systemId = m.group(2);
        } else {
            Matcher ms = DOCTYPE_SYSTEM.matcher(headForDoctype == null ? "" : headForDoctype);
            if (ms.find()) {
                systemId = ms.group(1);
            }
        }
        Handler handler = new Handler();
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (StopParsing ignored) {
            // first element captured — done
        } catch (Exception e) {
            // malformed — return what we have
        }
        return new SpecInfo(handler.root, publicId, systemId,
                parseClass(handler.classAttr), splitWords(handler.domainsAttr));
    }

    /** {@code @class} = "- topic/topic concept/concept " → general→specific tokens. */
    static List<String> parseClass(String classAttr) {
        List<String> chain = new ArrayList<>();
        if (classAttr == null) {
            return chain;
        }
        for (String token : classAttr.trim().split("\\s+")) {
            if (token.contains("/")) {              // skip the leading "-"/"+" marker
                chain.add(token);
            }
        }
        return chain;
    }

    private static List<String> splitWords(String value) {
        List<String> out = new ArrayList<>();
        if (value != null) {
            for (String w : value.trim().split("\\s+")) {
                if (!w.isEmpty()) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    /** Thrown to stop parsing after the root element. */
    private static final class StopParsing extends SAXException {}

    private static final class Handler extends DefaultHandler {
        String root;
        String classAttr;
        String domainsAttr;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a)
                throws SAXException {
            root = localName != null && !localName.isEmpty() ? localName : qName;
            classAttr = a.getValue("class");
            domainsAttr = a.getValue("domains");
            throw new StopParsing();            // root captured; stop
        }
    }

    private static String readHead(File file) {
        try (InputStream in = new FileInputStream(file)) {
            // readNBytes fully reads up to n (a single read() may short-read); the head
            // is generous so a DOCTYPE after a leading comment/copyright block is found.
            byte[] buf = in.readNBytes(8192);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

}
