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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts DITA 1.3 conref-push (`@conaction`) usages from a topic in one hardened SAX
 * pass (same hardening as {@link LinkExtractor}), with the spec's sibling-pairing rules
 * checked locally: {@code pushbefore}/{@code pushafter} need a {@code mark} sibling
 * (which carries the target {@code @conref}); {@code pushreplace} carries its own
 * {@code @conref}; a {@code mark} needs a push sibling. A {@code conref_push_audit}
 * adds cross-file target resolution on top.
 */
public final class ConrefPushScanner {

    /**
     * One {@code @conaction} element.
     *
     * @param element   element local name
     * @param action    the {@code @conaction} value (mark/pushbefore/pushafter/pushreplace)
     * @param conref    its {@code @conref}, or null
     * @param conkeyref its {@code @conkeyref}, or null
     * @param line      1-based line
     * @param pairing   a local sibling-pairing problem, or null if well-formed
     */
    public record Push(String element, String action, String conref, String conkeyref,
            int line, String pairing) {

        /** True for the mark/push action that carries the target reference to resolve. */
        public boolean carriesTarget() {
            return "mark".equals(action) || "pushreplace".equals(action);
        }
    }

    private ConrefPushScanner() {}

    /** Scan a file on disk; unreadable/malformed files yield an empty list. */
    public static List<Push> scan(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return scan(new InputSource(in));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Scan in-memory content (editor buffers, tests). */
    public static List<Push> scan(String content) {
        return scan(new InputSource(new StringReader(content)));
    }

    private static List<Push> scan(InputSource input) {
        Handler handler = new Handler();
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (Exception e) {
            // Malformed mid-edit files are normal — keep what we have.
        }
        return handler.pushes;
    }

    private static final class Handler extends DefaultHandler {
        final List<Push> pushes = new ArrayList<>();

        /** A conaction element awaiting its parent's end so its siblings are known. */
        private record Pending(String element, String action, String conref,
                String conkeyref, int line) {}

        /** One open element, accumulating its direct conaction children (lazily — most
         *  elements have none, so the list is created only when one is appended). */
        private static final class Frame {
            List<Pending> conactionChildren;

            List<Pending> children() {
                if (conactionChildren == null) {
                    conactionChildren = new ArrayList<>();
                }
                return conactionChildren;
            }
        }

        private final Deque<Frame> stack = new ArrayDeque<>();
        private Locator locator;

        @Override
        public void setDocumentLocator(Locator l) {
            this.locator = l;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            String action = a.getValue("conaction");
            if (action != null && !action.isEmpty() && !stack.isEmpty()) {
                stack.peek().children().add(new Pending(name, action.trim(),
                        a.getValue("conref"), a.getValue("conkeyref"),
                        locator != null ? locator.getLineNumber() : -1));
            }
            stack.push(new Frame());
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            Frame frame = stack.pop();
            List<Pending> group = frame.conactionChildren;
            if (group == null || group.isEmpty()) {
                return;
            }
            boolean hasMark = group.stream().anyMatch(p -> "mark".equals(p.action()));
            boolean hasBeforeAfter = group.stream().anyMatch(p ->
                    "pushbefore".equals(p.action()) || "pushafter".equals(p.action()));
            for (Pending p : group) {
                String pairing = switch (p.action()) {
                    case "pushbefore", "pushafter" -> hasMark ? null
                            : p.action() + " has no sibling conaction=\"mark\" "
                                    + "(nothing identifies the push target)";
                    case "mark" -> hasBeforeAfter ? null
                            : "mark has no pushbefore/pushafter sibling (nothing to push)";
                    case "pushreplace" -> (p.conref() != null || p.conkeyref() != null) ? null
                            : "pushreplace has no @conref/@conkeyref target";
                    default -> "unknown conaction \"" + p.action() + "\"";
                };
                pushes.add(new Push(p.element(), p.action(), p.conref(), p.conkeyref(),
                        p.line(), pairing));
            }
        }
    }

}
