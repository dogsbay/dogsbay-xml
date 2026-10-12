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

package com.dogsbay.xml.browser;

import java.io.File;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

/**
 * Converts AsciiDoc source text to HTML using AsciidoctorJ.
 * The Asciidoctor instance is lazily initialized on first use
 * (JRuby startup takes ~2-3 seconds).
 */
public class AsciiDocConverter {

    private static volatile Asciidoctor asciidoctor;

    private static Asciidoctor getAsciidoctor() {
        if (asciidoctor == null) {
            synchronized (AsciiDocConverter.class) {
                if (asciidoctor == null) {
                    asciidoctor = Asciidoctor.Factory.create();
                }
            }
        }
        return asciidoctor;
    }

    /**
     * Converts AsciiDoc source to a full HTML document.
     * Includes are not resolved (no base directory).
     */
    public static String convert(String source) {
        return convert(source, null);
    }

    /**
     * Converts AsciiDoc source to a full HTML document.
     * If baseDir is provided, include:: directives are resolved relative to it.
     *
     * @param source the AsciiDoc source text
     * @param baseDir the directory for resolving includes, or null
     */
    public static String convert(String source, File baseDir) {
        if (source == null || source.isEmpty()) {
            return wrapInHtml("");
        }

        try {
            Attributes attributes = Attributes.builder()
                .showTitle(true)
                .sourceHighlighter("highlight.js")
                .build();

            org.asciidoctor.OptionsBuilder optionsBuilder = Options.builder()
                .standalone(false)
                .safe(SafeMode.UNSAFE)
                .attributes(attributes);

            if (baseDir != null && baseDir.isDirectory()) {
                optionsBuilder.baseDir(baseDir);
            }

            String body = getAsciidoctor().convert(source, optionsBuilder.build());
            return wrapInHtml(body);
        } catch (Exception e) {
            return wrapInHtml("<div class=\"error\"><p>Preview error: "
                + escapeHtml(e.getMessage()) + "</p></div>"
                + "<pre>" + escapeHtml(source) + "</pre>");
        }
    }

    private static String wrapInHtml(String body) {
        return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n" +
            "<style>\n" + getDefaultCss() + "\n</style>\n</head>\n<body class=\"article\">\n" +
            body + "\n</body>\n</html>";
    }

    private static String getDefaultCss() {
        return "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; " +
            "line-height: 1.6; padding: 20px; max-width: 900px; margin: 0 auto; color: #333; }\n" +
            "h1, h2, h3, h4, h5 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; line-height: 1.25; }\n" +
            "h1 { font-size: 2em; border-bottom: 1px solid #eee; padding-bottom: .3em; }\n" +
            "h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: .3em; }\n" +
            "h3 { font-size: 1.25em; }\n" +
            "code { background: #f6f8fa; padding: 2px 6px; border-radius: 3px; font-size: 85%; }\n" +
            "pre { background: #f6f8fa; padding: 16px; border-radius: 6px; overflow: auto; }\n" +
            "pre code { background: none; padding: 0; }\n" +
            "a { color: #0366d6; text-decoration: none; }\n" +
            "a:hover { text-decoration: underline; }\n" +
            "img { max-width: 100%; }\n" +
            ".admonitionblock { margin: 16px 0; }\n" +
            ".admonitionblock td.icon { padding-right: 12px; font-weight: bold; vertical-align: top; }\n" +
            ".admonitionblock td.content { padding: 8px 12px; border-left: 3px solid #ddd; }\n" +
            ".admonitionblock.note td.content { border-color: #0366d6; background: #f1f8ff; }\n" +
            ".admonitionblock.tip td.content { border-color: #28a745; background: #f0fff4; }\n" +
            ".admonitionblock.warning td.content { border-color: #ffa500; background: #fff8f0; }\n" +
            ".admonitionblock.caution td.content { border-color: #d73a49; background: #ffeef0; }\n" +
            ".admonitionblock.important td.content { border-color: #6f42c1; background: #f5f0ff; }\n" +
            "table { border-collapse: collapse; margin: 16px 0; }\n" +
            "table th, table td { border: 1px solid #ddd; padding: 6px 13px; }\n" +
            "table th { background: #f6f8fa; font-weight: 600; }\n" +
            "li { margin: 4px 0; }\n" +
            "figure, .imageblock { margin: 16px 0; }\n" +
            ".error { color: #d73a49; background: #ffeef0; padding: 12px; border-radius: 4px; margin: 16px 0; }\n";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
