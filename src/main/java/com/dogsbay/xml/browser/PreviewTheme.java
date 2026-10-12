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

/**
 * CSS themes for the HTML preview panel.
 * Each theme provides a complete stylesheet that is injected into
 * the HTML content before rendering in the WebView.
 */
public enum PreviewTheme {

    LIGHT("Light",
        "body {\n" +
        "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n" +
        "  font-size: 14px;\n" +
        "  line-height: 1.6;\n" +
        "  color: #24292e;\n" +
        "  background-color: #ffffff;\n" +
        "  padding: 16px 32px;\n" +
        "  max-width: 960px;\n" +
        "}\n" +
        "a { color: #0366d6; }\n" +
        "h1, h2, h3, h4, h5, h6 {\n" +
        "  margin-top: 24px;\n" +
        "  margin-bottom: 16px;\n" +
        "  font-weight: 600;\n" +
        "  line-height: 1.25;\n" +
        "}\n" +
        "h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; }\n" +
        "h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; }\n" +
        "code {\n" +
        "  background-color: rgba(27,31,35,0.05);\n" +
        "  border-radius: 3px;\n" +
        "  font-size: 85%;\n" +
        "  padding: 0.2em 0.4em;\n" +
        "}\n" +
        "pre {\n" +
        "  background-color: #f6f8fa;\n" +
        "  border-radius: 3px;\n" +
        "  padding: 16px;\n" +
        "  overflow: auto;\n" +
        "}\n" +
        "pre code { background-color: transparent; padding: 0; }\n" +
        "blockquote {\n" +
        "  border-left: 4px solid #dfe2e5;\n" +
        "  color: #6a737d;\n" +
        "  margin: 0;\n" +
        "  padding: 0 16px;\n" +
        "}\n" +
        "table {\n" +
        "  border-collapse: collapse;\n" +
        "  margin: 16px 0;\n" +
        "}\n" +
        "table th, table td {\n" +
        "  border: 1px solid #dfe2e5;\n" +
        "  padding: 6px 13px;\n" +
        "}\n" +
        "table tr:nth-child(2n) { background-color: #f6f8fa; }\n" +
        "hr { border: none; border-top: 1px solid #eaecef; margin: 24px 0; }\n" +
        "img { max-width: 100%; }\n"
    ),

    DARK("Dark",
        "body {\n" +
        "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n" +
        "  font-size: 14px;\n" +
        "  line-height: 1.6;\n" +
        "  color: #c9d1d9;\n" +
        "  background-color: #0d1117;\n" +
        "  padding: 16px 32px;\n" +
        "  max-width: 960px;\n" +
        "}\n" +
        "a { color: #58a6ff; }\n" +
        "h1, h2, h3, h4, h5, h6 {\n" +
        "  margin-top: 24px;\n" +
        "  margin-bottom: 16px;\n" +
        "  font-weight: 600;\n" +
        "  line-height: 1.25;\n" +
        "  color: #e6edf3;\n" +
        "}\n" +
        "h1 { font-size: 2em; border-bottom: 1px solid #21262d; padding-bottom: 0.3em; }\n" +
        "h2 { font-size: 1.5em; border-bottom: 1px solid #21262d; padding-bottom: 0.3em; }\n" +
        "code {\n" +
        "  background-color: rgba(110,118,129,0.4);\n" +
        "  border-radius: 3px;\n" +
        "  font-size: 85%;\n" +
        "  padding: 0.2em 0.4em;\n" +
        "}\n" +
        "pre {\n" +
        "  background-color: #161b22;\n" +
        "  border-radius: 3px;\n" +
        "  padding: 16px;\n" +
        "  overflow: auto;\n" +
        "}\n" +
        "pre code { background-color: transparent; padding: 0; }\n" +
        "blockquote {\n" +
        "  border-left: 4px solid #3b434b;\n" +
        "  color: #8b949e;\n" +
        "  margin: 0;\n" +
        "  padding: 0 16px;\n" +
        "}\n" +
        "table {\n" +
        "  border-collapse: collapse;\n" +
        "  margin: 16px 0;\n" +
        "}\n" +
        "table th {\n" +
        "  border: 1px solid #30363d;\n" +
        "  padding: 6px 13px;\n" +
        "  background-color: #21262d;\n" +
        "  color: #e6edf3;\n" +
        "  font-weight: 600;\n" +
        "}\n" +
        "table td {\n" +
        "  border: 1px solid #30363d;\n" +
        "  padding: 6px 13px;\n" +
        "}\n" +
        "table tr:nth-child(2n) { background-color: #161b22; }\n" +
        "hr { border: none; border-top: 1px solid #21262d; margin: 24px 0; }\n" +
        "img { max-width: 100%; }\n"
    ),

    GITHUB("GitHub",
        "body {\n" +
        "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n" +
        "  font-size: 16px;\n" +
        "  line-height: 1.5;\n" +
        "  color: #1f2328;\n" +
        "  background-color: #ffffff;\n" +
        "  padding: 32px;\n" +
        "  max-width: 1012px;\n" +
        "}\n" +
        "a { color: #0969da; text-decoration: none; }\n" +
        "a:hover { text-decoration: underline; }\n" +
        "h1, h2, h3, h4, h5, h6 {\n" +
        "  margin-top: 24px;\n" +
        "  margin-bottom: 16px;\n" +
        "  font-weight: 600;\n" +
        "  line-height: 1.25;\n" +
        "}\n" +
        "h1 { font-size: 2em; border-bottom: 1px solid hsla(210,18%,87%,1); padding-bottom: 0.3em; }\n" +
        "h2 { font-size: 1.5em; border-bottom: 1px solid hsla(210,18%,87%,1); padding-bottom: 0.3em; }\n" +
        "h3 { font-size: 1.25em; }\n" +
        "code {\n" +
        "  background-color: rgba(175,184,193,0.2);\n" +
        "  border-radius: 6px;\n" +
        "  font-size: 85%;\n" +
        "  padding: 0.2em 0.4em;\n" +
        "  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;\n" +
        "}\n" +
        "pre {\n" +
        "  background-color: #f6f8fa;\n" +
        "  border-radius: 6px;\n" +
        "  padding: 16px;\n" +
        "  overflow: auto;\n" +
        "  font-size: 85%;\n" +
        "  line-height: 1.45;\n" +
        "}\n" +
        "pre code { background-color: transparent; padding: 0; font-size: 100%; }\n" +
        "blockquote {\n" +
        "  border-left: 4px solid #d0d7de;\n" +
        "  color: #656d76;\n" +
        "  margin: 0 0 16px 0;\n" +
        "  padding: 0 16px;\n" +
        "}\n" +
        "table {\n" +
        "  border-collapse: collapse;\n" +
        "  margin: 16px 0;\n" +
        "  display: block;\n" +
        "  width: max-content;\n" +
        "  max-width: 100%;\n" +
        "  overflow: auto;\n" +
        "}\n" +
        "table th {\n" +
        "  font-weight: 600;\n" +
        "  border: 1px solid #d0d7de;\n" +
        "  padding: 6px 13px;\n" +
        "}\n" +
        "table td {\n" +
        "  border: 1px solid #d0d7de;\n" +
        "  padding: 6px 13px;\n" +
        "}\n" +
        "table tr:nth-child(2n) { background-color: #f6f8fa; }\n" +
        "hr {\n" +
        "  border: none;\n" +
        "  border-top: 1px solid hsla(210,18%,87%,1);\n" +
        "  margin: 24px 0;\n" +
        "  height: 0.25em;\n" +
        "  background-color: #d0d7de;\n" +
        "}\n" +
        "img { max-width: 100%; box-sizing: border-box; }\n" +
        "ul, ol { padding-left: 2em; }\n" +
        "li + li { margin-top: 0.25em; }\n" +
        "input[type='checkbox'] { margin-right: 0.5em; }\n"
    );

    private final String displayName;
    private final String css;

    PreviewTheme(String displayName, String css) {
        this.displayName = displayName;
        this.css = css;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCss() {
        return css;
    }

    /**
     * Wraps HTML content with this theme's CSS stylesheet.
     */
    public String applyTo(String html) {
        if (html == null) {
            return null;
        }
        String styleBlock = "<style>\n" + css + "</style>\n";

        // If the HTML has a <head>, inject the style there
        int headEnd = html.indexOf("</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + styleBlock + html.substring(headEnd);
        }

        // If the HTML has a <body>, inject before it
        int bodyStart = html.indexOf("<body");
        if (bodyStart >= 0) {
            return html.substring(0, bodyStart) + "<head>" + styleBlock + "</head>" + html.substring(bodyStart);
        }

        // Otherwise, prepend the style
        return "<html><head>" + styleBlock + "</head><body>" + html + "</body></html>";
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Finds a theme by its display name, or returns LIGHT as default.
     */
    public static PreviewTheme fromName(String name) {
        if (name != null) {
            for (PreviewTheme theme : values()) {
                if (theme.displayName.equals(name)) {
                    return theme;
                }
            }
        }
        return LIGHT;
    }
}
