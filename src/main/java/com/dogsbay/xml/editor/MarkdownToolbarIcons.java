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

package com.dogsbay.xml.editor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates 16x16 icons for the Markdown toolbar programmatically.
 * Uses simple text/shape rendering for a clean, monochrome look
 * that matches the application's icon style.
 */
public class MarkdownToolbarIcons {

    private static final int SIZE = 16;
    private static final Map<String, Icon> cache = new HashMap<>();

    public static Icon getBoldIcon() {
        return getCachedIcon("bold", MarkdownToolbarIcons::drawBold);
    }

    public static Icon getItalicIcon() {
        return getCachedIcon("italic", MarkdownToolbarIcons::drawItalic);
    }

    public static Icon getStrikethroughIcon() {
        return getCachedIcon("strikethrough", MarkdownToolbarIcons::drawStrikethrough);
    }

    public static Icon getHeadingIcon() {
        return getCachedIcon("heading", MarkdownToolbarIcons::drawHeading);
    }

    public static Icon getLinkIcon() {
        return getCachedIcon("link", MarkdownToolbarIcons::drawLink);
    }

    public static Icon getImageIcon() {
        return getCachedIcon("image", MarkdownToolbarIcons::drawImage);
    }

    public static Icon getCodeIcon() {
        return getCachedIcon("code", MarkdownToolbarIcons::drawCode);
    }

    public static Icon getCodeBlockIcon() {
        return getCachedIcon("codeblock", MarkdownToolbarIcons::drawCodeBlock);
    }

    public static Icon getBulletListIcon() {
        return getCachedIcon("bulletlist", MarkdownToolbarIcons::drawBulletList);
    }

    public static Icon getNumberListIcon() {
        return getCachedIcon("numberlist", MarkdownToolbarIcons::drawNumberList);
    }

    public static Icon getTaskListIcon() {
        return getCachedIcon("tasklist", MarkdownToolbarIcons::drawTaskList);
    }

    public static Icon getQuoteIcon() {
        return getCachedIcon("quote", MarkdownToolbarIcons::drawQuote);
    }

    public static Icon getHorizontalRuleIcon() {
        return getCachedIcon("hr", MarkdownToolbarIcons::drawHorizontalRule);
    }

    public static Icon getTableIcon() {
        return getCachedIcon("table", MarkdownToolbarIcons::drawTable);
    }

    private static Icon getCachedIcon(String key, IconPainter painter) {
        return cache.computeIfAbsent(key, k -> {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(UIManager.getColor("Label.foreground"));
            if (g.getColor() == null) {
                g.setColor(Color.DARK_GRAY);
            }
            painter.paint(g);
            g.dispose();
            return new ImageIcon(img);
        });
    }

    @FunctionalInterface
    private interface IconPainter {
        void paint(Graphics2D g);
    }

    private static void drawBold(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("B", 3, 13);
    }

    private static void drawItalic(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD | Font.ITALIC, 13));
        g.drawString("I", 5, 13);
    }

    private static void drawStrikethrough(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("S", 3, 13);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(2, 8, 13, 8);
    }

    private static void drawHeading(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("H", 1, 12);
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.drawString("#", 10, 13);
    }

    private static void drawLink(Graphics2D g) {
        g.setStroke(new BasicStroke(1.5f));
        // Two interlocking chain links
        g.drawArc(1, 4, 8, 8, 45, 180);
        g.drawArc(6, 4, 8, 8, -135, 180);
    }

    private static void drawImage(Graphics2D g) {
        g.setStroke(new BasicStroke(1.2f));
        // Frame
        g.drawRect(1, 2, 13, 11);
        // Mountain
        g.drawLine(1, 11, 5, 7);
        g.drawLine(5, 7, 8, 10);
        g.drawLine(8, 10, 10, 8);
        g.drawLine(10, 8, 14, 13);
        // Sun
        g.fillOval(9, 4, 3, 3);
    }

    private static void drawCode(Graphics2D g) {
        g.setStroke(new BasicStroke(1.5f));
        // < >
        g.drawLine(5, 3, 1, 8);
        g.drawLine(1, 8, 5, 13);
        g.drawLine(10, 3, 14, 8);
        g.drawLine(14, 8, 10, 13);
    }

    private static void drawCodeBlock(Graphics2D g) {
        g.setStroke(new BasicStroke(1.2f));
        // Braces { }
        g.drawLine(4, 2, 3, 2);
        g.drawLine(3, 2, 3, 6);
        g.drawLine(3, 6, 1, 8);
        g.drawLine(1, 8, 3, 10);
        g.drawLine(3, 10, 3, 14);
        g.drawLine(3, 14, 4, 14);

        g.drawLine(11, 2, 12, 2);
        g.drawLine(12, 2, 12, 6);
        g.drawLine(12, 6, 14, 8);
        g.drawLine(14, 8, 12, 10);
        g.drawLine(12, 10, 12, 14);
        g.drawLine(12, 14, 11, 14);
    }

    private static void drawBulletList(Graphics2D g) {
        g.fillOval(1, 3, 3, 3);
        g.fillOval(1, 7, 3, 3);
        g.fillOval(1, 11, 3, 3);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(6, 4, 14, 4);
        g.drawLine(6, 8, 14, 8);
        g.drawLine(6, 12, 14, 12);
    }

    private static void drawNumberList(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.drawString("1", 1, 7);
        g.drawString("2", 1, 13);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(8, 4, 14, 4);
        g.drawLine(8, 10, 14, 10);
    }

    private static void drawTaskList(Graphics2D g) {
        g.setStroke(new BasicStroke(1.2f));
        // Checkbox
        g.drawRect(1, 2, 5, 5);
        // Checkmark
        g.drawLine(2, 5, 3, 6);
        g.drawLine(3, 6, 5, 3);
        // Line
        g.drawLine(8, 4, 14, 4);
        // Empty checkbox
        g.drawRect(1, 9, 5, 5);
        g.drawLine(8, 12, 14, 12);
    }

    private static void drawQuote(Graphics2D g) {
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(2, 2, 2, 14);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(6, 5, 14, 5);
        g.drawLine(6, 8, 12, 8);
        g.drawLine(6, 11, 14, 11);
    }

    private static void drawHorizontalRule(Graphics2D g) {
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(1, 8, 14, 8);
        // Dashes to indicate it's a rule, not just a line
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(1, 5, 3, 5);
        g.drawLine(6, 5, 9, 5);
        g.drawLine(12, 5, 14, 5);
    }

    private static void drawTable(Graphics2D g) {
        g.setStroke(new BasicStroke(1.0f));
        // Outer frame
        g.drawRect(1, 2, 13, 12);
        // Horizontal lines
        g.drawLine(1, 6, 14, 6);
        g.drawLine(1, 10, 14, 10);
        // Vertical line
        g.drawLine(7, 2, 7, 14);
    }
}
