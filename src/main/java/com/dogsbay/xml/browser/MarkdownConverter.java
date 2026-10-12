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

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.Arrays;

/**
 * Converts Markdown text to a styled HTML document using flexmark-java.
 *
 * <p>
 * Configures a CommonMark-compliant parser with GFM extensions
 * (tables, strikethrough, task lists, autolinks, YAML front matter)
 * and wraps the rendered HTML in a full document with GitHub-style CSS.
 * </p>
 *
 * <p>
 * Parser and renderer instances are created once and reused (thread-safe).
 * </p>
 *
 * @author DogsBay Ltd
 */
public class MarkdownConverter {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create(),
                YamlFrontMatterExtension.create()));
        // Soft-wrap lines in paragraphs (don't treat single newlines as <br>)
        options.set(HtmlRenderer.SOFT_BREAK, "\n");

        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    /**
     * Converts Markdown text to a complete, styled HTML document.
     *
     * @param markdown the Markdown source text
     * @return a full HTML document string with embedded CSS
     */
    public static String convert(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return wrapHtml("");
        }
        Node document = PARSER.parse(markdown);
        String htmlBody = RENDERER.render(document);
        return wrapHtml(htmlBody);
    }

    /**
     * Converts Markdown text to an HTML fragment (no wrapping document/CSS).
     *
     * @param markdown the Markdown source text
     * @return the rendered HTML fragment
     */
    public static String convertFragment(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /**
     * Wraps an HTML fragment in a full document with GitHub-style CSS.
     */
    private static String wrapHtml(String bodyContent) {
        return "<!DOCTYPE html>\n"
                + "<html>\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<style>\n" + CSS + "</style>\n"
                + "</head>\n<body>\n"
                + bodyContent
                + "</body>\n</html>";
    }

    /** GitHub-flavored Markdown CSS */
    private static final String CSS = "body {\n"
            + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n"
            + "  font-size: 14px;\n"
            + "  line-height: 1.6;\n"
            + "  color: #24292e;\n"
            + "  max-width: 880px;\n"
            + "  margin: 0 auto;\n"
            + "  padding: 20px 30px;\n"
            + "}\n"
            + "h1, h2, h3, h4, h5, h6 {\n"
            + "  margin-top: 24px;\n"
            + "  margin-bottom: 16px;\n"
            + "  font-weight: 600;\n"
            + "  line-height: 1.25;\n"
            + "}\n"
            + "h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: .3em; }\n"
            + "h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: .3em; }\n"
            + "h3 { font-size: 1.25em; }\n"
            + "h4 { font-size: 1em; }\n"
            + "p { margin-top: 0; margin-bottom: 16px; }\n"
            + "a { color: #0366d6; text-decoration: none; }\n"
            + "a:hover { text-decoration: underline; }\n"
            + "code {\n"
            + "  background-color: rgba(27,31,35,.05);\n"
            + "  border-radius: 3px;\n"
            + "  font-size: 85%;\n"
            + "  margin: 0;\n"
            + "  padding: .2em .4em;\n"
            + "  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;\n"
            + "}\n"
            + "pre {\n"
            + "  background-color: #f6f8fa;\n"
            + "  border-radius: 3px;\n"
            + "  font-size: 85%;\n"
            + "  line-height: 1.45;\n"
            + "  overflow: auto;\n"
            + "  padding: 16px;\n"
            + "}\n"
            + "pre code {\n"
            + "  background-color: transparent;\n"
            + "  border: 0;\n"
            + "  display: inline;\n"
            + "  line-height: inherit;\n"
            + "  margin: 0;\n"
            + "  overflow: visible;\n"
            + "  padding: 0;\n"
            + "  word-wrap: normal;\n"
            + "}\n"
            + "blockquote {\n"
            + "  border-left: .25em solid #dfe2e5;\n"
            + "  color: #6a737d;\n"
            + "  margin: 0;\n"
            + "  padding: 0 1em;\n"
            + "}\n"
            + "table {\n"
            + "  border-collapse: collapse;\n"
            + "  border-spacing: 0;\n"
            + "  margin-top: 0;\n"
            + "  margin-bottom: 16px;\n"
            + "}\n"
            + "table th, table td {\n"
            + "  border: 1px solid #dfe2e5;\n"
            + "  padding: 6px 13px;\n"
            + "}\n"
            + "table th {\n"
            + "  font-weight: 600;\n"
            + "  background-color: #f6f8fa;\n"
            + "}\n"
            + "table tr:nth-child(2n) {\n"
            + "  background-color: #f6f8fa;\n"
            + "}\n"
            + "img { max-width: 100%; }\n"
            + "hr {\n"
            + "  border: 0;\n"
            + "  border-bottom: 1px solid #eaecef;\n"
            + "  height: 0;\n"
            + "  margin: 24px 0;\n"
            + "}\n"
            + "ul, ol { padding-left: 2em; }\n"
            + "li + li { margin-top: .25em; }\n"
            + "del { color: #6a737d; }\n"
            + ".task-list-item {\n"
            + "  list-style-type: none;\n"
            + "}\n"
            + ".task-list-item input {\n"
            + "  margin: 0 .2em .25em -1.6em;\n"
            + "  vertical-align: middle;\n"
            + "}\n";
}
