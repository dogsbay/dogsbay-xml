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
package com.dogsbay.xml.review;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * How a proposal is written into an element's attributes, and read back.
 *
 * <ul>
 * <li>{@code status}: new, deleted or changed. If the element already had a
 *     status of its own, it is kept as an {@code otherprops} token
 *     {@code review-prev-status-VALUE} and restored when the mark comes off.</li>
 * <li>{@code rev}: the author id is one token, appended to whatever revision
 *     text the element already carried. An author id has a scheme prefix
 *     ({@code ai:}, {@code user:}, {@code mcp:}, {@code rpc:}); a plain
 *     {@code rev="2.1"} is human revision metadata, never a proposal.</li>
 * <li>{@code otherprops} tokens: {@code review-mark} on the synthetic
 *     {@code <ph>} wrappers the engine writes (so a real {@code <ph>} is never
 *     unwrapped); {@code otherprops="review-deleted"}, alone, on everything a
 *     proposal removes, for DITAVAL exclusion at build; {@code review-wrapper}
 *     on an element whose insertion or removal wrapped or unwrapped existing
 *     content (its content stays, so it is never struck). The stamps other
 *     than {@code review-deleted} are tokens of {@code rev}.</li>
 * </ul>
 */
public final class Marks {

    public static final String MARK = "review-mark";
    public static final String DELETED = "review-deleted";
    public static final String WRAPPER = "review-wrapper";
    static final String PREV_STATUS = "review-prev-status-";
    static final String PREV_PROPS = "review-prev-props-";
    private static final Pattern AUTHOR = Pattern.compile("^(ai|user|mcp|rpc):\\S+$");

    private Marks() {
    }

    public static boolean isAuthorId(String token) {
        return token != null && AUTHOR.matcher(token).matches();
    }

    /** The author id in a {@code rev} value, or null when it carries none. */
    public static String authorOf(String rev) {
        if (rev == null) {
            return null;
        }
        for (String tok : rev.trim().split("\\s+")) {
            if (isAuthorId(tok)) {
                return tok;
            }
        }
        return null;
    }

    static boolean hasToken(String value, String token) {
        if (value == null) {
            return false;
        }
        for (String tok : value.trim().split("\\s+")) {
            if (tok.equals(token)) {
                return true;
            }
        }
        return false;
    }

    static String addToken(String value, String token) {
        if (hasToken(value, token)) {
            return value;
        }
        return value == null || value.isBlank() ? token : value.trim() + " " + token;
    }

    static String removeToken(String value, String token) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String tok : value.trim().split("\\s+")) {
            if (!tok.isEmpty() && !tok.equals(token)) {
                sb.append(sb.isEmpty() ? "" : " ").append(tok);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * The attributes of {@code t} with a proposal mark applied.
     *
     * <p>Everything the engine needs to undo the mark rides in {@code rev}
     * beside the author id: {@code review-mark}, {@code review-wrapper}, the
     * prior status as {@code review-prev-status-*}. On a struck element,
     * {@code otherprops} becomes exactly {@code review-deleted} and its prior
     * tokens move to {@code rev} as {@code review-prev-props-*}: DITA-OT only
     * excludes an element when every token of the attribute is excluded, so
     * the build filter needs that value to stand alone.
     */
    static Map<String, String> marked(Token t, String status, String author, List<String> props) {
        Map<String, String> a = new LinkedHashMap<>(t.attrs());
        String rev = addToken(a.get("rev"), author);
        String prevStatus = a.get("status");
        if (prevStatus != null && !prevStatus.isBlank()) {
            rev = addToken(rev, PREV_STATUS + prevStatus);
        }
        a.put("status", status);
        a.put("rev", rev);   // before otherprops when new, so tags read status, rev, otherprops
        boolean struck = false;
        for (String p : props) {
            if (DELETED.equals(p)) {
                struck = true;
            } else {
                rev = addToken(rev, p);
            }
        }
        if (struck) {
            String existing = a.get("otherprops");
            if (existing != null) {
                for (String tok : existing.trim().split("\\s+")) {
                    if (!tok.isEmpty() && !DELETED.equals(tok)) {
                        rev = addToken(rev, PREV_PROPS + tok);
                    }
                }
            }
            a.put("otherprops", DELETED);
        }
        a.put("rev", rev);
        return a;
    }

    /** The attributes of {@code t} with its proposal mark removed and any prior status and props restored. */
    static Map<String, String> unmarked(Token t) {
        Map<String, String> a = new LinkedHashMap<>(t.attrs());
        String prevStatus = null;
        String prevProps = null;
        String rev = a.get("rev");
        if (rev != null) {
            for (String tok : rev.trim().split("\\s+")) {
                if (tok.isEmpty()) {
                    continue;
                }
                if (isAuthorId(tok) || MARK.equals(tok) || WRAPPER.equals(tok)) {
                    rev = removeToken(rev, tok);
                } else if (tok.startsWith(PREV_STATUS)) {
                    prevStatus = tok.substring(PREV_STATUS.length());
                    rev = removeToken(rev, tok);
                } else if (tok.startsWith(PREV_PROPS)) {
                    prevProps = addToken(prevProps, tok.substring(PREV_PROPS.length()));
                    rev = removeToken(rev, tok);
                }
            }
        }
        if (rev == null) {
            a.remove("rev");
        } else {
            a.put("rev", rev);
        }
        if (prevStatus != null) {
            a.put("status", prevStatus);   // in place, so attribute order survives the round trip
        } else {
            a.remove("status");
        }
        if (a.containsKey("otherprops")) {
            String props = removeToken(a.get("otherprops"), DELETED);
            if (prevProps != null) {
                props = props == null ? prevProps : props + " " + prevProps;
            }
            if (props == null) {
                a.remove("otherprops");
            } else {
                a.put("otherprops", props);
            }
        }
        return a;
    }

    /** {@code t}'s start tag re-rendered with {@code attrs}. */
    static String retag(Token t, Map<String, String> attrs) {
        return XmlTokens.renderTag(t.name(), attrs, t.kind() == XmlTokens.Kind.EMPTY);
    }
}
