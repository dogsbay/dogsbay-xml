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

package com.dogsbay.xml.author.model;

import java.util.Map;

/**
 * Attribute keys for inline text styling within a text block.
 *
 * <p>Inline formatting is stored as attribute maps on {@link InlineRun}s (the
 * "delta" model): boolean styles use the value "true", DITA semantic inline
 * elements are stored under {@link #DITA_INLINE} with the element name as the
 * value, and links carry their target under {@link #HREF} / {@link #KEYREF}.
 */
public final class InlineStyle {

    /** Maps to DITA {@code <b>}. */
    public static final String BOLD = "bold";

    /** Maps to DITA {@code <i>}. */
    public static final String ITALIC = "italic";

    /** Maps to DITA {@code <u>}. */
    public static final String UNDERLINE = "underline";

    /**
     * Semantic DITA inline element name: {@code codeph}, {@code uicontrol},
     * {@code filepath}, {@code varname}, {@code keyword}, {@code term}, {@code tm}.
     */
    public static final String DITA_INLINE = "ditaInline";

    /** Link target; maps to DITA {@code <xref href="...">}. */
    public static final String HREF = "href";

    /** Link scope for {@code <xref>} ({@code external}, {@code local}, ...). */
    public static final String SCOPE = "scope";

    /** Key reference; maps to DITA {@code keyref} attribute on {@code <xref>}/{@code <ph>}. */
    public static final String KEYREF = "keyref";

    /**
     * A plain {@code <ph>} (no attributes, text only) around the run. The value
     * numbers the phrase within its block so two adjacent phrases stay two
     * elements on export instead of merging into one.
     */
    public static final String PH = "ph";

    /**
     * Semantic inline elements wrapped around the run's innermost one, outermost
     * first and joined by {@code /}: {@code menucascade} for a {@code uicontrol}
     * inside a {@code menucascade}. Absent when the run has one semantic inline.
     */
    public static final String DITA_INLINE_OUTER = "ditaInlineOuter";

    /** The element name of one {@link #DITA_INLINE_OUTER} chain entry ({@code menucascade#3} to {@code menucascade}). */
    public static String chainName(String link) {
        int hash = link.indexOf('#');
        return hash < 0 ? link : link.substring(0, hash);
    }

    /**
     * Prefix for attributes carried by an inline element other than the ones
     * with a key of their own: {@code b:outputclass}, {@code uicontrol:id}.
     * The element name before the colon says which element of the run's chain
     * gets the attribute back on export.
     */
    public static final String INLINE_ATTR_SEPARATOR = ":";

    /** Recognised values for {@link #DITA_INLINE}: the DITA 1.3 inline elements the run model carries. */
    public static final java.util.Set<String> DITA_INLINE_NAMES =
            java.util.Set.of("codeph", "uicontrol", "filepath", "varname", "keyword", "term", "tm",
                    "abbreviated-form", "sup", "sub", "q", "cite", "menucascade", "shortcut", "apiname",
                    "parmname", "msgph", "userinput", "systemoutput", "cmdname", "option", "synph",
                    "wintitle", "tt", "line-through", "overline", "boolean", "state", "fn", "indexterm",
                    "text", "xmlelement", "xmlatt", "xmlnsname", "xmlpi", "numcharref", "parameterentity",
                    "textentity", "msgnum", "markupname", "data", "sort-as", "mathml", "svg-container",
                    "foreign", "equation-inline", "draft-comment", "required-cleanup", "indextermref");

    /**
     * Which inline element instance a run came from, numbered within its
     * block: two neighbouring {@code <uicontrol>}s with the same styling keep
     * separate runs, so they stay two elements through editing and export.
     * Not exported; ignored by every style decision.
     */
    public static final String ELEMENT = "#element";

    /** Inline elements shown in monospace. */
    public static final java.util.Set<String> MONOSPACE_INLINES = java.util.Set.of("codeph", "filepath",
            "varname", "apiname", "parmname", "msgph", "userinput", "systemoutput", "cmdname", "option",
            "synph", "tt", "xmlelement", "xmlatt", "xmlnsname", "xmlpi", "numcharref", "parameterentity",
            "textentity", "msgnum", "markupname");

    public static final String TRUE = "true";

    private InlineStyle() {
    }

    public static boolean isBold(Map<String, String> attrs) {
        return TRUE.equals(attrs.get(BOLD));
    }

    public static boolean isItalic(Map<String, String> attrs) {
        return TRUE.equals(attrs.get(ITALIC));
    }

    public static boolean isUnderline(Map<String, String> attrs) {
        return TRUE.equals(attrs.get(UNDERLINE));
    }
}
