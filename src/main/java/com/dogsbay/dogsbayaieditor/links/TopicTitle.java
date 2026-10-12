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
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A topic file's title, for the places that have to show what a link to it would say.
 *
 * <p>Read from the head of the file with a pattern rather than parsed: the File Explorer labels hundreds of
 * topics at once, and the preview asks again for every key in the map. Stateless — callers that ask repeatedly
 * hold their own cache, because they know when theirs goes stale.
 */
public final class TopicTitle {

    /** How much of the file to read. A title after this much prolog is not a title anyone is waiting for. */
    private static final int HEAD_BYTES = 64 * 1024;

    private static final Pattern TITLE =
            Pattern.compile("<title\\b[^>]*>(.*?)</title>", Pattern.DOTALL);
    /** The root element's start tag, once the prolog is out of the way. */
    private static final Pattern ROOT_TAG =
            Pattern.compile("\\A\\s*<([A-Za-z_][-\\w.:]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*)>", Pattern.DOTALL);
    /** An {@code id} attribute in a start tag's attribute text, in either quote style. */
    private static final Pattern ID_ATTRIBUTE =
            Pattern.compile("\\bid\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    /** A character or entity reference the flattening did not resolve. */
    private static final Pattern UNRESOLVED_REFERENCE = Pattern.compile("&[A-Za-z_#][^;\\s]*;");
    /**
     * An empty inline element standing in for a key: {@code <keyword keyref="product-name"/>}, or the same
     * written with a separate end tag. One that has content of its own needs nothing done — the content is the
     * text, and flattening the markup leaves it there, which is what DITA says should happen.
     */
    private static final Pattern EMPTY_KEYREF = Pattern.compile(
            "<(keyword|ph|term|cite|abbreviated-form)\\b((?:\"[^\"]*\"|'[^']*'|[^>\"'])*?)"
            + "(?:/>|></\\1\\s*>)", Pattern.DOTALL);
    /** A {@code keyref} attribute in a start tag's attribute text, in either quote style. */
    private static final Pattern KEYREF_ATTRIBUTE =
            Pattern.compile("\\bkeyref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern NUMERIC_REFERENCE = Pattern.compile("&#(x([0-9a-fA-F]+)|([0-9]+));");

    /**
     * Where the text of a key comes from, for a title that is written with one.
     *
     * <p>{@code <title>Installing <keyword keyref="product-name"/></title>} is an ordinary way to write a
     * title, and every caller that wants to show it has to be able to say what the key stands for.
     */
    public interface KeyText {

        /** The text {@code key} stands for, or null when it does not resolve. */
        String of(String key);

        /** Resolves nothing — for a caller with no key space to hand. */
        KeyText NONE = key -> null;
    }

    private TopicTitle() {
    }

    /**
     * The file's first {@code <title>} as plain text, or null when it has none — or when it is written with a
     * key that {@code keys} cannot resolve, since a title reading "What is ?" names nothing.
     *
     * <p>Lenient about one thing only: a title carrying a DTD entity comes back with the reference in it,
     * because a File Explorer row reading {@code Installing &prodname;} is still the row the reader is looking
     * for, and an empty one is not.
     */
    public static String read(File topic, KeyText keys) {
        String head = withoutComments(head(topic));
        return head == null ? null : firstTitle(head, keys);
    }

    /**
     * The title a link to {@code href} would show, or null.
     *
     * <p>The fragment decides whether the file's own title is the right answer. No fragment, or one naming the
     * root element, and it is. A fragment of the form {@code #topic/element} addresses something inside the
     * topic, whose title is not the topic's — so nothing is offered rather than the wrong thing. The same for a
     * fragment naming a nested topic: finding that one's title means parsing, and a wrong title in a link is
     * worse than no title at all.
     *
     * <p>Stricter than {@link #read} in one more way. A title still carrying an unresolved reference
     * ({@code Installing &prodname;}) is refused, because this text goes into a link where the build would have
     * shown the resolved character, and the key's own name reads better than an entity does.
     *
     * @param file the resolved target file (fragment already stripped)
     * @param href the raw {@code @href}, whose fragment is read here; may be null
     */
    public static String forHref(File file, String href, KeyText keys) {
        String head = withoutComments(head(file));
        if (head == null) {
            return null;
        }
        String fragment = href == null || href.indexOf('#') < 0
                ? "" : href.substring(href.indexOf('#') + 1).trim();
        if (!fragment.isEmpty()) {
            if (fragment.indexOf('/') >= 0) {
                return null;                        // an element inside a topic
            }
            if (!fragment.equals(rootId(head))) {
                return null;                        // a nested topic, or one we cannot confirm
            }
        }
        String title = firstTitle(head, keys);
        return title == null || UNRESOLVED_REFERENCE.matcher(title).find() ? null : title;
    }

    /** The {@code id} of the document's root element, or null when it has none or cannot be found. */
    private static String rootId(String head) {
        Matcher root = ROOT_TAG.matcher(withoutProlog(head));
        if (!root.find()) {
            return null;
        }
        Matcher id = ID_ATTRIBUTE.matcher(root.group(2));
        return id.find() ? (id.group(1) != null ? id.group(1) : id.group(2)) : null;
    }

    private static String head(File topic) {
        if (topic == null || !topic.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(topic)) {
            return new String(in.readNBytes(HEAD_BYTES), StandardCharsets.UTF_8);
        } catch (IOException cannotRead) {
            return null;
        }
    }

    /**
     * {@code head} without its comments.
     *
     * <p>Markup inside a comment is not markup. A topic that keeps its old start tag in a comment above the
     * real one — which is how anyone edits a file they are unsure about — otherwise hands back the commented
     * id, and a commented-out {@code <title>} is read as the title.
     */
    private static String withoutComments(String head) {
        if (head == null || !head.contains("<!--")) {
            return head;
        }
        StringBuilder out = new StringBuilder(head.length());
        int at = 0;
        while (true) {
            int open = head.indexOf("<!--", at);
            if (open < 0) {
                return out.append(head, at, head.length()).toString();
            }
            out.append(head, at, open);
            int close = head.indexOf("-->", open + 4);
            if (close < 0) {
                return out.toString();              // an unterminated comment runs to the end
            }
            at = close + 3;
        }
    }

    /**
     * {@code head} from its root element's start tag onwards: past the XML declaration, any processing
     * instructions and the DOCTYPE — whose internal subset may itself contain {@code >}, so it is skipped by
     * its brackets and not by the first angle bracket that comes along.
     */
    private static String withoutProlog(String head) {
        int at = 0;
        while (at < head.length()) {
            while (at < head.length() && Character.isWhitespace(head.charAt(at))) {
                at++;
            }
            if (at + 1 >= head.length() || head.charAt(at) != '<') {
                break;
            }
            char next = head.charAt(at + 1);
            if (next == '?') {
                int end = head.indexOf("?>", at + 2);
                at = end < 0 ? head.length() : end + 2;
            } else if (next == '!') {
                at = endOfDoctype(head, at);
            } else {
                break;                              // the root element
            }
        }
        return head.substring(Math.min(at, head.length()));
    }

    /** The index just past the {@code <!...>} declaration starting at {@code from}, internal subset included. */
    private static int endOfDoctype(String head, int from) {
        boolean inSubset = false;
        char quote = 0;
        for (int i = from + 2; i < head.length(); i++) {
            char c = head.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                inSubset = true;
            } else if (c == ']') {
                inSubset = false;
            } else if (c == '>' && !inSubset) {
                return i + 1;
            }
        }
        return head.length();
    }

    /** The first title in {@code head}, with keys, markup and references flattened to text. */
    private static String firstTitle(String head, KeyText keys) {
        Matcher m = TITLE.matcher(head);
        if (!m.find()) {
            return null;
        }
        String markup = withKeysResolved(m.group(1), keys == null ? KeyText.NONE : keys);
        if (markup == null) {
            return null;
        }
        String text = decode(markup.replaceAll("<[^>]+>", "")).replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * {@code markup} with every empty keyref element replaced by the text its key stands for, or null when one
     * of them does not resolve.
     *
     * <p>Null rather than the rest of the sentence. Dropping the element is what the plain markup strip did,
     * and it turned {@code Installing <keyword keyref="product-name"/>} into "Installing" and
     * {@code What is <keyword keyref="product-name"/>?} into "What is ?" — the second of which is not a title
     * of anything (reported 2026-09-30). A caller with no title falls back to something honest.
     */
    private static String withKeysResolved(String markup, KeyText keys) {
        if (markup.indexOf("keyref") < 0) {
            return markup;
        }
        Matcher element = EMPTY_KEYREF.matcher(markup);
        StringBuilder out = new StringBuilder();
        while (element.find()) {
            Matcher keyref = KEYREF_ATTRIBUTE.matcher(element.group(2));
            if (!keyref.find()) {
                // An empty <keyword/> with no keyref carries nothing either way; leave it to the markup strip.
                element.appendReplacement(out, Matcher.quoteReplacement(element.group()));
                continue;
            }
            String key = keyref.group(1) != null ? keyref.group(1) : keyref.group(2);
            String text = keys.of(key);
            if (text == null || text.isBlank()) {
                return null;
            }
            element.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        return element.appendTail(out).toString();
    }

    /** The five named XML entities and any numeric character reference; anything else is left as it stands. */
    private static String decode(String text) {
        String out = text;
        if (out.indexOf("&#") >= 0) {
            Matcher m = NUMERIC_REFERENCE.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String digits = m.group(2) != null ? m.group(2) : m.group(3);
                int radix = m.group(2) != null ? 16 : 10;
                String replacement;
                try {
                    replacement = Character.toString(Integer.parseInt(digits, radix));
                } catch (IllegalArgumentException tooBig) {
                    replacement = m.group();        // not a character anyone can show; leave it visible
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            out = m.appendTail(sb).toString();
        }
        // Last, so a &amp;#8212; in the source does not turn into an em dash
        return out.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
