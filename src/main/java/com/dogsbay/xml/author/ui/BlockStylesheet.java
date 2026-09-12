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

package com.dogsbay.xml.author.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.InlineStyle;

/**
 * Visual styling for blocks and inline runs. All colors and fonts derive from
 * {@link UIManager} at construction time so the panel follows the active
 * look-and-feel (FlatLaf light/dark in the editor, stock LaFs standalone);
 * rebuild the stylesheet after a LaF change.
 */
public class BlockStylesheet {

    private final Font baseFont;
    private final Font monoFont;
    private final Color foreground;
    private final Color mutedForeground;
    private final Color linkColor;
    private final Color inlineCodeBackground;
    private final Color rawBackground;
    private final Color noteAccent;
    private final Color selectionBackground;
    private final Color gutterForeground;

    /** Author-view font scale, shared by every pane in the session. */
    private static volatile double fontScale = 1.0;

    private static final double MIN_SCALE = 0.7;
    private static final double MAX_SCALE = 2.5;

    public static double getFontScale() {
        return fontScale;
    }

    /** Sets the scale, clamped; panes pick it up on their next rebuild. Returns the value in force. */
    public static double setFontScale(double scale) {
        fontScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        return fontScale;
    }

    public BlockStylesheet() {
        Font label = UIManager.getFont("Label.font");
        Font base = label != null ? label.deriveFont(Font.PLAIN, label.getSize2D() + 1f)
                : new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        baseFont = base.deriveFont((float) Math.max(6.0, base.getSize2D() * fontScale));
        monoFont = new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, baseFont.getSize() - 1));
        Color fg = UIManager.getColor("Label.foreground");
        foreground = fg != null ? fg : Color.BLACK;
        mutedForeground = mix(foreground, background(), 0.45);
        Color link = UIManager.getColor("Component.linkColor");
        linkColor = link != null ? link : new Color(0x2675BF);
        inlineCodeBackground = mix(background(), foreground, 0.08);
        rawBackground = mix(background(), foreground, 0.95);
        noteAccent = new Color(0xE8A33D);
        Color sel = UIManager.getColor("List.selectionBackground");
        selectionBackground = sel != null
                ? new Color(sel.getRed(), sel.getGreen(), sel.getBlue(), 40)
                : new Color(0, 120, 215, 40);
        gutterForeground = mix(foreground, background(), 0.55);
    }

    public Color background() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : Color.WHITE;
    }

    public Color foreground() {
        return foreground;
    }

    public Color mutedForeground() {
        return mutedForeground;
    }

    public Color gutterForeground() {
        return gutterForeground;
    }

    public Color selectionBackground() {
        return selectionBackground;
    }

    public Color rawBackground() {
        return rawBackground;
    }

    public Color noteAccent() {
        return noteAccent;
    }

    public Font monoFont() {
        return monoFont;
    }

    public Color codeCommentColor() {
        return mix(new Color(0x4E9A06), foreground, 0.2);
    }

    public Color codeStringColor() {
        return mix(new Color(0xA6541F), foreground, 0.2);
    }

    public Font gutterFont() {
        return baseFont.deriveFont(Font.PLAIN, baseFont.getSize2D() - 3f);
    }

    /** Block-level font; {@code depth} is the block's depth from the root. */
    public Font fontFor(BlockType type, int depth) {
        return switch (type.getName()) {
            case "title" -> depth <= 1
                    ? baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 9f)
                    : baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 3f);
            case "shortdesc" -> baseFont.deriveFont(Font.ITALIC);
            // prolog metadata reads as an aside, not as body text
            case "author", "source", "publisher", "copyrholder", "category", "keywords" ->
                    baseFont.deriveFont(Font.PLAIN, Math.max(7f, baseFont.getSize2D() - 2f));
            case "codeblock" -> monoFont;
            case "cmd" -> baseFont.deriveFont(Font.PLAIN);
            case "proptype", "propvalue" -> monoFont;
            default -> type.getCategory() == BlockType.Category.RAW ? monoFont : baseFont;
        };
    }

    /** Character attributes for one inline run, layered over the block font. */
    public SimpleAttributeSet attributesFor(Map<String, String> runAttrs) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        if (InlineStyle.isBold(runAttrs)) {
            StyleConstants.setBold(attrs, true);
        }
        if (InlineStyle.isItalic(runAttrs)) {
            StyleConstants.setItalic(attrs, true);
        }
        if (InlineStyle.isUnderline(runAttrs)) {
            StyleConstants.setUnderline(attrs, true);
        }
        String semantic = runAttrs.get(InlineStyle.DITA_INLINE);
        if (semantic != null) {
            // every semantic inline gets a tint so tag boundaries stay
            // visible while editing (hover names the tag)
            switch (semantic) {
                case "sup" -> StyleConstants.setSuperscript(attrs, true);
                case "sub" -> StyleConstants.setSubscript(attrs, true);
                case "line-through" -> StyleConstants.setStrikeThrough(attrs, true);
                case "overline" -> StyleConstants.setUnderline(attrs, true);
                case "q", "cite" -> StyleConstants.setItalic(attrs, true);
                case "fn", "indexterm", "draft-comment", "required-cleanup" -> {
                    StyleConstants.setBackground(attrs, tint(new Color(0xE0B800)));
                    StyleConstants.setFontSize(attrs, Math.max(8, baseFont.getSize() - 2));
                }
                case "codeph", "filepath", "varname", "apiname", "parmname", "msgph", "userinput", "systemoutput",
                        "cmdname", "option", "synph", "tt", "xmlelement", "xmlatt", "xmlnsname", "xmlpi", "msgnum",
                        "markupname", "numcharref", "parameterentity", "textentity" -> {
                    StyleConstants.setFontFamily(attrs, monoFont.getFamily());
                    StyleConstants.setBackground(attrs, inlineCodeBackground);
                    if ("varname".equals(semantic)) {
                        StyleConstants.setItalic(attrs, true);
                    }
                    if ("userinput".equals(semantic) || "cmdname".equals(semantic)) {
                        StyleConstants.setBold(attrs, true);
                    }
                }
                case "uicontrol", "menucascade", "shortcut", "wintitle" -> {
                    StyleConstants.setBold(attrs, true);
                    StyleConstants.setBackground(attrs, tint(new Color(0x2675BF)));
                }
                case "term" -> {
                    StyleConstants.setItalic(attrs, true);
                    StyleConstants.setBackground(attrs, tint(new Color(0x4E9A06)));
                }
                case "keyword", "abbreviated-form" ->
                        StyleConstants.setBackground(attrs, tint(new Color(0xE8A33D)));
                default -> StyleConstants.setBackground(attrs, inlineCodeBackground); // tm, ...
            }
        }
        if (runAttrs.containsKey(InlineStyle.HREF)) {
            StyleConstants.setForeground(attrs, linkColor);
            StyleConstants.setUnderline(attrs, true);
        }
        if (runAttrs.containsKey(InlineStyle.KEYREF)) {
            StyleConstants.setForeground(attrs, linkColor);
            StyleConstants.setItalic(attrs, true);
        }
        return attrs;
    }

    /** A faint wash of {@code color} over the panel background. */
    private Color tint(Color color) {
        return mix(background(), color, 0.14);
    }

    /**
     * Accent for a block's family, painted as a thin left bar: blue for
     * structure, indigo for task elements, grey for lists, teal for tables.
     */
    public Color categoryAccent(String typeName) {
        return switch (typeName) {
            case "section", "conbody", "taskbody", "refbody", "body", "example",
                 "glossdef" -> new Color(0x2675BF);
            case "steps", "step", "substeps", "substep", "choices", "cmd", "info",
                 "prereq", "context", "result", "postreq", "stepresult" -> new Color(0x5856D6);
            case "ul", "ol", "li", "dl", "dlentry" -> mix(foreground, background(), 0.5);
            case "simpletable", "table", "tgroup", "properties", "choicetable", "chhead", "chrow" -> new Color(0x1A7F74);
            case "prolog", "metadata", "critdates", "copyright" -> mix(foreground, background(), 0.65);
            default -> null;
        };
    }

    private static Color mix(Color a, Color b, double towardsB) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * towardsB),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * towardsB),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * towardsB));
    }
}
