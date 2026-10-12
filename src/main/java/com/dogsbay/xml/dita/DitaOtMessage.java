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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.event.Level;

/**
 * One diagnostic captured from a DITA-OT run (the {@code validate} transtype or a
 * build). DITA-OT logs messages of the shape {@code [<CODE>][<SEVERITY>] <text>}
 * (e.g. {@code [DOTJ012F][FATAL] Failed to parse 'topics/x.dita'.}); this record
 * carries the parsed pieces plus a best-effort source location for click-to-open.
 *
 * @param code     the DITA-OT message code (e.g. {@code DOTJ012F}), or null
 * @param severity normalized severity: {@code FATAL}, {@code ERROR}, {@code WARN},
 *                 or {@code INFO}
 * @param message  the human-readable text (with the {@code [code][sev]} tags stripped)
 * @param file     the source file the message refers to, or null if none was found
 * @param line     1-based line within {@code file}, or -1 if unknown
 * @param column   1-based column, or -1 if unknown
 */
public record DitaOtMessage(
    String code,
    String severity,
    String message,
    String file,
    int line,
    int column
) {

    /** True for severities that should fail a validate/build (FATAL or ERROR). */
    public boolean isError() {
        return "FATAL".equals(severity) || "ERROR".equals(severity);
    }

    // [DOTJ012F] / [DOTX029W] / [DOTA001E] …
    private static final Pattern CODE = Pattern.compile("\\[(DOT[A-Z]\\d+[A-Z]?)\\]");
    // [FATAL] / [ERROR] / [WARN] / [WARNING] / [INFO]
    private static final Pattern SEV =
        Pattern.compile("\\[(FATAL|ERROR|WARN|WARNING|INFO)\\]", Pattern.CASE_INSENSITIVE);
    // a DITA source path, optionally followed by :line or :line:col
    // ditamap/ditaval before dita: the alternation is ordered, and "dita" is a
    // prefix of the others, so it must come last or "guide.ditamap" → "guide.dita".
    private static final Pattern LOC = Pattern.compile(
        "([^\\s'\"<>()]+\\.(?:ditamap|ditaval|dita))(?::(\\d+)(?::(\\d+))?)?",
        Pattern.CASE_INSENSITIVE);

    /**
     * Parse one DITA-OT log line into a {@link DitaOtMessage}. The SLF4J
     * {@code level} is the fallback severity when the text carries no
     * {@code [SEVERITY]} tag (DITA-OT logs FATAL via {@code error()}, so the inline
     * tag is what distinguishes FATAL from ERROR). Location extraction is
     * best-effort: a {@code .dita}/{@code .ditamap}/{@code .ditaval} path and an
     * optional {@code :line(:col)} suffix; absent → file null / line -1.
     */
    public static DitaOtMessage parse(Level level, String text) {
        String raw = text == null ? "" : text;

        String code = null;
        Matcher cm = CODE.matcher(raw);
        if (cm.find()) {
            code = cm.group(1);
        }

        String severity;
        Matcher sm = SEV.matcher(raw);
        if (sm.find()) {
            String tag = sm.group(1).toUpperCase();
            severity = "WARNING".equals(tag) ? "WARN" : tag;
        } else {
            severity = switch (level) {
                case ERROR -> "ERROR";
                case WARN -> "WARN";
                default -> "INFO";
            };
        }

        String file = null;
        int line = -1;
        int column = -1;
        Matcher lm = LOC.matcher(raw);
        if (lm.find()) {
            file = lm.group(1);
            if (lm.group(2) != null) {
                line = Integer.parseInt(lm.group(2));
            }
            if (lm.group(3) != null) {
                column = Integer.parseInt(lm.group(3));
            }
        }

        // Clean the human text: DITA-OT prefixes many messages with the source
        // location and/or [code][severity] tags (e.g.
        // "file:/x/a.dita:10:10: [DOTJ088E][ERROR] XML parsing error: ..."). Strip a
        // leading "<path>:line:col:" location, then any run of leading bracket tags,
        // then a leftover colon, so the message reads as just the diagnostic text.
        String message = raw
                .replaceFirst("^\\s*\\S+\\.(?:ditamap|ditaval|dita)(?::\\d+){0,2}:?\\s*", "")
                .replaceAll("^\\s*(\\[[^\\]]*\\]\\s*)+", "")
                .replaceFirst("^\\s*:\\s*", "")
                .strip();
        if (message.isEmpty()) {
            message = raw.strip();
        }

        return new DitaOtMessage(code, severity, message, file, line, column);
    }
}
