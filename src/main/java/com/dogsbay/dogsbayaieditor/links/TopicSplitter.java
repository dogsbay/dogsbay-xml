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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Split topic: every top-level {@code <section>} in a topic's body
 * becomes a standalone concept topic in the same directory, the map
 * gains nested {@code topicref}s, and references into the split-out
 * content are rewritten. Two-phase (plan/apply) like the other refactor
 * engines.
 */
public final class TopicSplitter {

    private static final Set<String> BODY_TAGS = Set.of(
            "conbody", "body", "refbody", "taskbody");

    /**
     * One section becoming a topic.
     *
     * @param title       title text (markup stripped) for display
     * @param newTopicId  the new topic's root id
     * @param newFile     the file to create
     * @param sectionText the section exactly as written in the source
     * @param topicText   the new topic file's full content
     * @param line        1-based line of the section in the source
     * @param ids         ids defined inside the section (incl. its own)
     */
    public record Split(String title, String newTopicId, File newFile,
                        String sectionText, String topicText, int line,
                        Set<String> ids) {
    }

    /**
     * @param source   the topic being split
     * @param map      the map to wire topicrefs into; null = no wiring
     * @param splits   one per top-level section
     * @param refEdits reference rewrites into the split-out content
     * @param warnings non-fatal concerns
     */
    public record Plan(File source, File map, List<Split> splits,
                       List<ReferenceRewriter.AttributeEdit> refEdits,
                       List<String> warnings) {
    }

    /** Outcome of applying a plan. */
    public record ApplyResult(int filesCreated, int editsApplied,
                              List<String> failures) {
    }

    private TopicSplitter() {
    }

    // -------------------------------------------------------------------------
    // Planning
    // -------------------------------------------------------------------------

    /**
     * Plans the split.
     *
     * @throws IllegalArgumentException when the topic has no body or no
     *                                  top-level sections
     */
    public static Plan plan(File source, File map, ReverseLinkIndex index)
            throws IOException {
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        int[] body = bodySpan(content);
        if (body == null) {
            throw new IllegalArgumentException("No body element (conbody/body/"
                    + "refbody/taskbody) in " + source.getName());
        }
        List<int[]> sectionSpans = topLevelSections(content, body[0], body[1]);
        if (sectionSpans.isEmpty()) {
            throw new IllegalArgumentException("No top-level <section> elements "
                    + "to split out in " + source.getName());
        }

        String sourceTopicId = ReferenceRewriter.rootTopicId(source);
        Set<String> usedNames = new LinkedHashSet<>();
        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < sectionSpans.size(); i++) {
            splits.add(planSection(source, content, sectionSpans.get(i),
                    i + 1, usedNames, warnings));
        }

        // References into split-out content: #srcTopic/elem → new location.
        List<ReferenceRewriter.AttributeEdit> refEdits = new ArrayList<>();
        int keyBasedHits = 0;
        for (Reference ref : index.usagesOf(source)) {
            if (ref.source() == null || ref.fragment() == null) {
                continue;
            }
            String fragment = ref.fragment();
            int slash = fragment.indexOf('/');
            if (slash < 0) {
                continue;                           // whole-topic ref — unchanged
            }
            String elemId = fragment.substring(slash + 1);
            Split home = splits.stream()
                    .filter(s -> s.ids().contains(elemId)).findFirst().orElse(null);
            if (home == null) {
                continue;                           // element stays in the source
            }
            String newFragment = elemId.equals(home.newTopicId())
                    ? home.newTopicId()
                    : home.newTopicId() + "/" + elemId;
            String newValue = ReferenceRewriter.relativePath(
                    ref.source().getParentFile(), home.newFile())
                    + "#" + newFragment;
            refEdits.add(new ReferenceRewriter.AttributeEdit(ref.source(),
                    ref.line(), ref.attribute(), ref.rawValue(), newValue));
        }
        for (Reference ref : index.allReferences()) {
            if (!ref.isKeyReference() || ref.fragment() == null) {
                continue;
            }
            for (Split split : splits) {
                if (split.ids().contains(ref.fragment())) {
                    keyBasedHits++;
                    break;
                }
            }
        }
        if (keyBasedHits > 0) {
            warnings.add(keyBasedHits + " key-based reference(s) (conkeyref/"
                    + "keyref with element ids) may target split-out content — "
                    + "keys still point at " + source.getName()
                    + "; review them after the split");
        }
        if (sourceTopicId == null) {
            warnings.add("Source topic id not found — fragment rewrites assume "
                    + "element ids are unique in " + source.getName());
        }
        if (map == null) {
            warnings.add("No map given — the new topics are created but not "
                    + "referenced anywhere (health will report them as orphans)");
        } else if (findTopicrefSpan(map, source) == null) {
            warnings.add("No topicref to " + source.getName() + " found in "
                    + map.getName() + " — new topicrefs are appended at the "
                    + "end of the map");
        }
        return new Plan(source, map, splits, refEdits, warnings);
    }

    private static Split planSection(File source, String content, int[] span,
                                     int ordinal, Set<String> usedNames,
                                     List<String> warnings) {
        String sectionText = content.substring(span[0], span[2]);
        int line = lineOf(content, span[0]);

        String inner = sectionText.substring(
                sectionText.indexOf('>') + 1,
                sectionText.lastIndexOf("</"));
        String titleMarkup = "";
        String body = inner;
        Matcher title = Pattern.compile("<title\\b[^>]*>(.*?)</title>",
                Pattern.DOTALL).matcher(inner);
        if (title.find()) {
            titleMarkup = title.group(1).trim();
            body = (inner.substring(0, title.start())
                    + inner.substring(title.end()));
        }
        String titleText = titleMarkup.replaceAll("<[^>]*>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").trim();
        if (titleText.isEmpty()) {
            titleText = "Section " + ordinal;
            warnings.add("Section " + ordinal + " has no title — named \""
                    + titleText + "\"");
        }

        String sectionId = attributeInStartTag(sectionText, "id");
        String slug = titleText.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            slug = "section-" + ordinal;
        }
        String newTopicId = sectionId != null && !sectionId.isEmpty()
                ? sectionId : slug;

        File dir = source.getParentFile();
        String base = slug;
        File newFile = new File(dir, base + ".dita");
        int n = 2;
        while (newFile.exists() || !usedNames.add(newFile.getName())) {
            newFile = new File(dir, base + "-" + n++ + ".dita");
        }

        Set<String> ids = new LinkedHashSet<>();
        if (sectionId != null && !sectionId.isEmpty()) {
            ids.add(sectionId);
        }
        Matcher idMatcher = Pattern.compile("\\sid\\s*=\\s*([\"'])(.*?)\\1")
                .matcher(sectionText);
        while (idMatcher.find()) {
            ids.add(idMatcher.group(2));
        }

        // Carry the source topic's prolog metadata (created, author, keywords, …)
        // into each split-out topic so they don't re-open the metadata gaps that
        // an audit just closed. Placed after the title, per the concept model.
        String prologMarkup = "";
        Matcher prolog = Pattern.compile("<prolog\\b.*?</prolog>", Pattern.DOTALL)
                .matcher(content);
        if (prolog.find()) {
            // Carry shared metadata (created, author, keywords, …) but drop
            // identity-specific <resourceid> — duplicating it would make several
            // split topics claim the same resource id.
            String p = prolog.group()
                    .replaceAll("(?s)<resourceid\\b[^>]*/>", "")
                    .replaceAll("(?s)<resourceid\\b.*?</resourceid>", "");
            prologMarkup = "  " + p.strip() + "\n";
        }

        String topicText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
                + "<concept id=\"" + newTopicId + "\">\n"
                + "  <title>" + (titleMarkup.isEmpty() ? titleText : titleMarkup)
                + "</title>\n"
                + prologMarkup
                + "  <conbody>\n"
                + body.strip() + "\n"
                + "  </conbody>\n"
                + "</concept>\n";
        return new Split(titleText, newTopicId, newFile, sectionText,
                topicText, line, ids);
    }

    // -------------------------------------------------------------------------
    // Applying
    // -------------------------------------------------------------------------

    /**
     * Applies a plan in dependency order: new files, map wiring,
     * reference edits, then section removal from the source (located by
     * exact captured text — refused per section if the source changed).
     */
    public static ApplyResult apply(Plan plan) {
        List<String> failures = new ArrayList<>();
        int filesCreated = 0;
        int editsApplied = 0;

        for (Split split : plan.splits()) {
            try {
                if (split.newFile().exists()) {
                    failures.add(split.newFile() + ": already exists");
                    continue;
                }
                Files.writeString(split.newFile().toPath(), split.topicText(),
                        StandardCharsets.UTF_8);
                filesCreated++;
            } catch (IOException e) {
                failures.add(split.newFile() + ": " + e.getMessage());
            }
        }

        if (plan.map() != null) {
            try {
                patchMap(plan);
                editsApplied++;
            } catch (Exception e) {
                failures.add(plan.map() + ": " + e.getMessage());
            }
        }

        if (!plan.refEdits().isEmpty()) {
            ReferenceRewriter.ApplyResult refs = ReferenceRewriter.apply(
                    new ReferenceRewriter.Plan(plan.refEdits(), null, null,
                            List.of()));
            editsApplied += refs.editsApplied();
            failures.addAll(refs.failures());
        }

        try {
            String content = Files.readString(plan.source().toPath(),
                    StandardCharsets.UTF_8);
            int removed = 0;
            for (Split split : plan.splits()) {
                int idx = content.indexOf(split.sectionText());
                if (idx < 0) {
                    failures.add(plan.source() + ": section \"" + split.title()
                            + "\" changed since the plan — not removed");
                    continue;
                }
                content = content.substring(0, idx)
                        + content.substring(idx + split.sectionText().length());
                removed++;
            }
            if (removed > 0) {
                Files.writeString(plan.source().toPath(), content,
                        StandardCharsets.UTF_8);
                editsApplied += removed;
            }
        } catch (IOException e) {
            failures.add(plan.source() + ": " + e.getMessage());
        }
        return new ApplyResult(filesCreated, editsApplied, failures);
    }

    private static void patchMap(Plan plan) throws IOException {
        File map = plan.map();
        String content = Files.readString(map.toPath(), StandardCharsets.UTF_8);
        StringBuilder children = new StringBuilder();
        for (Split split : plan.splits()) {
            children.append("<topicref href=\"")
                    .append(ReferenceRewriter.relativePath(map.getParentFile(),
                            split.newFile()))
                    .append("\"/>");
        }

        int[] span = findTopicrefSpan(map, plan.source());
        String updated;
        if (span == null) {
            int close = content.lastIndexOf("</");
            if (close < 0) {
                throw new IOException("no closing tag to insert before");
            }
            updated = content.substring(0, close) + children + "\n"
                    + content.substring(close);
        } else if (content.charAt(span[1] - 2) == '/') {
            // self-closing topicref → paired with nested children
            String startTag = content.substring(span[0], span[1]);
            String paired = startTag.substring(0, startTag.length() - 2)
                    .stripTrailing() + ">" + children + "</topicref>";
            updated = content.substring(0, span[0]) + paired
                    + content.substring(span[1]);
        } else {
            int closeStart = content.lastIndexOf("</topicref", span[2]);
            updated = content.substring(0, closeStart) + children
                    + content.substring(closeStart);
        }
        Files.writeString(map.toPath(), updated, StandardCharsets.UTF_8);
    }

    /**
     * Span of the map's topicref to {@code source}:
     * {@code {startTagStart, startTagEnd+1, elementEnd}}, or null.
     */
    static int[] findTopicrefSpan(File map, File source) {
        try {
            for (Reference ref : LinkExtractor.extract(map).references()) {
                if (ref.targetPath() == null || !"href".equals(ref.attribute())) {
                    continue;
                }
                if (!new File(ref.targetPath()).getCanonicalFile()
                        .equals(source.getCanonicalFile())) {
                    continue;
                }
                String content = Files.readString(map.toPath(), StandardCharsets.UTF_8);
                int[] span = ReferenceRewriter.elementSpan(content,
                        "href", ref.rawValue());
                if (span != null) {
                    int startTagEnd = content.indexOf('>', span[0]);
                    return new int[] {span[0], startTagEnd + 1, span[2]};
                }
            }
        } catch (Exception ignored) {
            // treated as not found
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Text scanning
    // -------------------------------------------------------------------------

    /** {@code {innerStart, innerEnd}} of the topic's body element. */
    private static int[] bodySpan(String content) {
        for (String tag : BODY_TAGS) {
            Matcher m = Pattern.compile("<" + tag + "[\\s>]").matcher(content);
            if (!m.find()) {
                continue;
            }
            int start = m.start();
            int startTagEnd = content.indexOf('>', start);
            int close = content.lastIndexOf("</" + tag);
            if (startTagEnd >= 0 && close > startTagEnd) {
                return new int[] {startTagEnd + 1, close};
            }
        }
        return null;
    }

    /**
     * Spans of {@code <section>} elements that are direct children of the
     * body: {@code {start, startTagEnd, end}} each.
     */
    private static List<int[]> topLevelSections(String content, int innerStart,
                                                int innerEnd) {
        List<int[]> spans = new ArrayList<>();
        int depth = 0;
        int i = innerStart;
        while (i < innerEnd) {
            int lt = content.indexOf('<', i);
            if (lt < 0 || lt >= innerEnd) {
                break;
            }
            if (content.startsWith("<!--", lt)) {
                int close = content.indexOf("-->", lt);
                i = close < 0 ? innerEnd : close + 3;
                continue;
            }
            if (content.startsWith("<![CDATA[", lt)) {
                int close = content.indexOf("]]>", lt);
                i = close < 0 ? innerEnd : close + 3;
                continue;
            }
            if (content.startsWith("<?", lt) || content.startsWith("<!", lt)) {
                int gt = content.indexOf('>', lt);
                i = gt < 0 ? innerEnd : gt + 1;
                continue;
            }
            int gt = tagEnd(content, lt);
            if (gt < 0) {
                break;
            }
            if (content.charAt(lt + 1) == '/') {
                depth--;
            } else if (content.charAt(gt - 1) == '/') {
                // self-closing: depth unchanged
            } else {
                if (depth == 0 && "section".equals(tagName(content, lt))) {
                    int end = sectionEnd(content, lt, gt);
                    if (end > 0) {
                        spans.add(new int[] {lt, gt + 1, end});
                        i = end;
                        continue;
                    }
                }
                depth++;
            }
            i = gt + 1;
        }
        return spans;
    }

    /** End offset (exclusive) of the section starting at {@code lt}. */
    private static int sectionEnd(String content, int lt, int startTagGt) {
        int depth = 1;
        int i = startTagGt + 1;
        while (i < content.length()) {
            int open = content.indexOf("<section", i);
            int close = content.indexOf("</section", i);
            if (close < 0) {
                return -1;
            }
            if (open >= 0 && open < close
                    && (Character.isWhitespace(content.charAt(open + 8))
                        || content.charAt(open + 8) == '>')) {
                int gt = tagEnd(content, open);
                if (gt < 0) {
                    return -1;
                }
                if (content.charAt(gt - 1) != '/') {
                    depth++;
                }
                i = gt + 1;
            } else {
                depth--;
                int gt = content.indexOf('>', close);
                if (gt < 0) {
                    return -1;
                }
                if (depth == 0) {
                    return gt + 1;
                }
                i = gt + 1;
            }
        }
        return -1;
    }

    private static String attributeInStartTag(String elementText, String name) {
        int gt = elementText.indexOf('>');
        if (gt < 0) {
            return null;
        }
        Matcher m = Pattern.compile("\\s" + name + "\\s*=\\s*([\"'])(.*?)\\1")
                .matcher(elementText.substring(0, gt + 1));
        return m.find() ? m.group(2) : null;
    }

    private static String tagName(String content, int lt) {
        int p = lt + 1;
        while (p < content.length() && !Character.isWhitespace(content.charAt(p))
                && content.charAt(p) != '>' && content.charAt(p) != '/') {
            p++;
        }
        return content.substring(lt + 1, p);
    }

    private static int tagEnd(String content, int lt) {
        char inQuote = 0;
        for (int i = lt; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuote != 0) {
                if (c == inQuote) {
                    inQuote = 0;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int lineOf(String content, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
