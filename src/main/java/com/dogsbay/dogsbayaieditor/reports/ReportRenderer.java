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

package com.dogsbay.dogsbayaieditor.reports;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Renders a self-contained, offline HTML report from a template, a JSON data document and a small
 * metadata map. Templates reference shared kits with {@code <!--@kit:NAME-->} markers and carry a
 * {@code <script id="report-data" type="application/json">} slot that receives the data. See
 * {@code docs-dev/reports.md} for the template contract.
 *
 * <p>Pure string processing; no Swing, safe to call from headless code.
 */
public final class ReportRenderer {

    /** Id of the mandatory data slot. */
    public static final String DATA_SLOT = "report-data";
    /** Id of the optional metadata slot. */
    public static final String META_SLOT = "report-meta";

    private static final List<String> BUILT_IN_TEMPLATES = List.of("health", "relationship-map");
    private static final String KIT_PREFIX = "<!--@kit:";
    private static final Pattern KIT_MARKER = Pattern.compile("<!--@kit:([^>]*?)-->");
    private static final Pattern BARE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private static final Pattern[] EXTERNAL_PATTERNS = {
        Pattern.compile("(?<![\\w:-])(?:src|href)\\s*=\\s*[\"']?\\s*((?:https?:)?//[^\"'\\s>]+)",
                Pattern.CASE_INSENSITIVE),
        Pattern.compile("url\\(\\s*[\"']?\\s*((?:https?:)?//[^\"')\\s]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("@import\\s+(?:url\\(\\s*)?[\"']?\\s*((?:https?:)?//[^\"')\\s;]+)",
                Pattern.CASE_INSENSITIVE),
    };

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ReportRenderer() {}

    /**
     * Renders a complete HTML document.
     *
     * @param templateHtml the template text
     * @param json the report data; must be valid JSON
     * @param meta metadata such as {@code generated}, {@code editorVersion}, {@code source}; may be null
     * @return the rendered HTML
     * @throws IllegalArgumentException on invalid JSON, a missing or duplicated data slot, a duplicated
     *     meta slot, or an unknown kit
     */
    public static String render(String templateHtml, String json, Map<String, String> meta) {
        Objects.requireNonNull(templateHtml, "templateHtml");
        validateJson(json);
        String html = fillSlot(templateHtml, DATA_SLOT, escapeForScript(json), true);
        String metaJson;
        try {
            metaJson = MAPPER.writeValueAsString(meta == null ? Map.of() : new LinkedHashMap<>(meta));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise report metadata: " + e.getMessage(), e);
        }
        html = fillSlot(html, META_SLOT, escapeForScript(metaJson), false);
        // Slots are filled first: escaped JSON cannot contain "<", so it can never look like a kit marker.
        return inlineKits(html);
    }

    /**
     * Loads a template. A bare name such as {@code relationship-map} loads the built-in template; anything
     * else is a file path, resolved against {@code projectRoot} when relative (for example
     * {@code .dogsbay/reports/mine.html}).
     *
     * @throws IllegalArgumentException if the template cannot be found
     */
    public static String loadTemplate(String nameOrPath, Path projectRoot) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            throw new IllegalArgumentException("No report template given. Built-in templates: " + BUILT_IN_TEMPLATES);
        }
        String spec = nameOrPath.strip();
        if (BARE_NAME.matcher(spec).matches()) {
            String text = readResource("templates/" + spec + ".html");
            if (text == null) {
                throw new IllegalArgumentException(
                        "Unknown report template '" + spec + "'. Built-in templates: " + BUILT_IN_TEMPLATES);
            }
            return text;
        }
        Path path = Path.of(spec);
        if (!path.isAbsolute()) {
            path = (projectRoot != null ? projectRoot : Path.of("")).resolve(path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Report template not found: " + path
                    + ". Built-in templates: " + BUILT_IN_TEMPLATES);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read report template " + path, e);
        }
    }

    /** Names of the templates bundled with the editor. */
    public static List<String> builtInTemplates() {
        return BUILT_IN_TEMPLATES;
    }

    /** Writes the report as UTF-8, creating parent directories, via a temp file and a move. */
    public static void write(Path output, String html) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(html, "html");
        Path target = output.toAbsolutePath();
        Path dir = target.getParent();
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "." + target.getFileName(), ".tmp");
            try {
                Files.writeString(tmp, html, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write report " + target, e);
        }
    }

    /**
     * Returns absolute or protocol-relative URLs referenced from {@code src=}, {@code href=},
     * {@code url(} or {@code @import}; the SVG namespace URI and {@code xmlns} attributes are ignored.
     * An empty result means the page loads nothing from the network.
     */
    public static List<String> externalReferences(String html) {
        List<String> found = new ArrayList<>();
        for (Pattern p : EXTERNAL_PATTERNS) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String url = m.group(1);
                if (!url.startsWith(SVG_NS)) {
                    found.add(url);
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------

    private static void validateJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Report data is null");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Report data is not valid JSON: " + e.getOriginalMessage(), e);
        }
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("Report data is not valid JSON: empty document");
        }
    }

    /** Escapes characters that could end the script element or break JavaScript parsing. */
    static String escapeForScript(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Pattern slotPattern(String id) {
        return Pattern.compile("(<script\\b[^>]*\\bid\\s*=\\s*[\"']" + Pattern.quote(id)
                + "[\"'][^>]*>)(.*?)(</script\\s*>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static String fillSlot(String html, String id, String content, boolean required) {
        Matcher m = slotPattern(id).matcher(html);
        if (!m.find()) {
            if (required) {
                throw new IllegalArgumentException("Template has no <script id=\"" + id
                        + "\" type=\"application/json\"> element");
            }
            return html;
        }
        int start = m.end(1);
        int end = m.start(3);
        if (m.find()) {
            throw new IllegalArgumentException("Template has more than one <script id=\"" + id + "\"> element");
        }
        return html.substring(0, start) + content + html.substring(end);
    }

    private static String inlineKits(String html) {
        Matcher m = KIT_MARKER.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 32_000);
        int last = 0;
        while (m.find()) {
            String name = m.group(1).strip();
            out.append(html, last, m.start());
            out.append(kit(name));
            last = m.end();
        }
        out.append(html, last, html.length());
        return out.toString();
    }

    private static String kit(String name) {
        String css = BARE_NAME.matcher(name).matches() ? readResource("kits/" + name + ".css") : null;
        String js = BARE_NAME.matcher(name).matches() ? readResource("kits/" + name + ".js") : null;
        if (css == null && js == null) {
            throw new IllegalArgumentException("Unknown report kit '" + name + "'");
        }
        StringBuilder sb = new StringBuilder();
        if (css != null) {
            checkKit(name + ".css", css, "</style");
            sb.append("<style>\n").append(css).append("\n</style>");
        }
        if (js != null) {
            checkKit(name + ".js", js, "</script");
            sb.append("<script>\n").append(js).append("\n</script>");
        }
        return sb.toString();
    }

    private static void checkKit(String file, String text, String closer) {
        if (text.contains(KIT_PREFIX)) {
            throw new IllegalStateException("Report kit " + file + " must not contain kit markers");
        }
        if (text.toLowerCase(java.util.Locale.ROOT).contains(closer)) {
            throw new IllegalStateException("Report kit " + file + " must not contain " + closer);
        }
    }

    private static String readResource(String relative) {
        try (InputStream in = ReportRenderer.class.getResourceAsStream(relative)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read report resource " + relative, e);
        }
    }
}
