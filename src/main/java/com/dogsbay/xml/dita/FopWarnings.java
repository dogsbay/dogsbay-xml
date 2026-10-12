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

package com.dogsbay.xml.dita;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Collects what FOP says during one build, instead of letting it onto the terminal.
 *
 * <p>A book of any size produces dozens of warnings — a glyph missing from the font, a line that overflows its
 * column, a table FOP lays out its own way — and they arrived in the middle of a command-line check, ahead of the
 * report someone was waiting for. They are worth knowing and not worth that: counted here, and reported as one line
 * naming the kinds.
 *
 * <p>FOP logs through {@code java.util.logging}, bridged to SLF4J. This attaches to the {@code org.apache.fop}
 * logger for the length of a build and puts it back afterwards, so nothing else is affected and a nested build
 * cannot leave the logger detached.
 *
 * <p><b>What the fonts say is counted apart from what the document says.</b> The first PDF build on a machine
 * parses every font FOP auto-detects and reports what it cannot read in them — on this one, 57 of "coverage set
 * class table not yet supported" and 11 more from a Noto face. {@code ~/.fop/fop-fonts.cache} then answers for
 * every later build and they never appear again, so the same deliverable reported 84 warnings in the editor
 * (which ran first) and 11 on the command line, and the tutorial could not print a number at all (reported
 * 2026-09-30). They are about the machine's fonts and not about the document, so they are counted separately
 * and named as such, leaving a document count that is the same on a cold machine and a warm one.
 */
final class FopWarnings implements AutoCloseable {

    private static final String FOP_LOGGER = "org.apache.fop";

    /** How many distinct messages to name in the summary before counting the rest. */
    private static final int KINDS_NAMED = 3;

    private final Logger logger;
    private final boolean wasAdditive;
    private final Level wasLevel;
    private final Collector collector = new Collector();

    private FopWarnings(Logger logger) {
        this.logger = logger;
        this.wasAdditive = logger.isAdditive();
        this.wasLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        logger.setAdditive(false); // ours, and not also the console
        collector.start();
        logger.addAppender(collector);
    }

    /**
     * Start collecting, or return a no-op when the logging framework is not the one we can attach to.
     *
     * <p>SLF4J can be bound to something other than logback, and a build is not the place to insist.
     */
    static FopWarnings collect() {
        org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof ch.qos.logback.classic.LoggerContext context)) {
            return null;
        }
        return new FopWarnings(context.getLogger(FOP_LOGGER));
    }

    /** One line saying what FOP said, or null when it said nothing. */
    String summary() {
        int total = collector.total;
        int fonts = collector.fromFonts;
        if (total == 0 && fonts == 0) {
            return null;
        }
        String aboutTheFonts = fonts == 0 ? ""
                : (total == 0 ? "PDF rendering read " : ", and read ") + fonts + " warning"
                        + (fonts == 1 ? "" : "s") + " from fonts installed on this machine";
        if (total == 0) {
            return aboutTheFonts;
        }
        List<String> kinds = new ArrayList<>();
        collector.byKind.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(KINDS_NAMED)
                .forEach(e -> kinds.add(e.getValue() + " " + e.getKey()));
        int rest = collector.byKind.size() - kinds.size();
        return "PDF rendering reported " + total + " warning" + (total == 1 ? "" : "s")
                + " (" + String.join(", ", kinds) + (rest > 0 ? ", and " + rest + " other kind"
                        + (rest == 1 ? "" : "s") : "") + ")" + aboutTheFonts;
    }

    @Override
    public void close() {
        logger.detachAppender(collector);
        collector.stop();
        logger.setAdditive(wasAdditive);
        logger.setLevel(wasLevel);
    }

    /** Counts events, and groups them by the shape of the message rather than its particulars. */
    private static final class Collector extends AppenderBase<ILoggingEvent> {

        private int total;
        private int fromFonts;
        private final Map<String, Integer> byKind = new LinkedHashMap<>();

        @Override
        protected void append(ILoggingEvent event) {
            if (!event.getLevel().isGreaterOrEqual(Level.WARN)) {
                return;
            }
            if (isAboutAFont(event.getLoggerName(), event.getFormattedMessage())) {
                fromFonts++;
                return;
            }
            total++;
            byKind.merge(kindOf(event.getFormattedMessage()), 1, Integer::sum);
        }

        /** FOP's two auto-detection events, which name a file on this machine and no part of the document. */
        private static final String[] AUTO_DETECTION = {
            "Unable to load font file:",     // FontEventProducer#fontLoadingErrorAtAutoDetection
            "The font directory ",           // FontEventProducer#fontDirectoryNotFound
        };

        /**
         * The classes that read font files. Named one by one rather than by the {@code org.apache.fop.fonts}
         * package, which is not the line: {@code Font}, {@code Typeface} and {@code MultiByteFont} sit directly
         * in it and warn about glyphs missing for <em>the document's</em> characters — the PDF comes out with
         * {@code #} where the text should be. Taking the whole package would have declared such a book clean.
         */
        private static final String[] READING_A_FONT_FILE = {
            "org.apache.fop.complexscripts.fonts.",     // the advanced-typographic tables in the file
            "org.apache.fop.fonts.truetype.",
            "org.apache.fop.fonts.type1.",
            "org.apache.fop.fonts.cff.",
            "org.apache.fop.fonts.autodetect.",
            "org.apache.fop.fonts.FontCache",
            "org.apache.fop.fonts.LazyFont",            // "Failed to read font file ..."
        };

        /**
         * Whether this is about a font on this machine rather than about laying out the document.
         *
         * <p>Mostly by the logger, which is the class that said it — a package will not move under us the way
         * a sentence will. Two more have to be recognised by their words, because they do not come from a font
         * class at all: FOP raises them as <em>events</em>, which its {@code LoggingEventListener} logs under
         * its own name, and the formatted message is all that survives. Those two are the ones that fire while
         * FOP is auto-detecting the machine's fonts, so they are what still differed between a cold and a warm
         * font cache once the parsing warnings were out of the way.
         *
         * <p>When in doubt this says no. A machine warning left in the document's count is a number that moves;
         * a document warning moved out of it is a defect nobody is told about.
         */
        private static boolean isAboutAFont(String logger, String message) {
            if (logger != null) {
                for (String reader : READING_A_FONT_FILE) {
                    if (logger.startsWith(reader)) {
                        return true;
                    }
                }
            }
            if (message == null) {
                return false;
            }
            for (String event : AUTO_DETECTION) {
                if (message.startsWith(event)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * What kind of warning this is.
         *
         * <p>"Glyph "−" (0x2212, minus) not available in font "Times-Roman"" and the same for every other character
         * are one problem, not forty. The quoted particulars are what differ, so they come out.
         */
        private static String kindOf(String message) {
            if (message == null) {
                return "unknown";
            }
            String kind = message.replaceAll("\"[^\"]*\"", "\u2026")
                    .replaceAll("\\(0x[0-9a-fA-F]+[^)]*\\)", "")
                    .replaceAll("\\b\\d+(\\.\\d+)?(pt|mm|px)?\\b", "n")
                    .replaceAll("\\s+", " ")
                    .trim();
            return kind.length() > 90 ? kind.substring(0, 90) + "\u2026" : kind;
        }
    }
}
