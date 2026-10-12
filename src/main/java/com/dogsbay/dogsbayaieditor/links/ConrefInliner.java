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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inline-conref: permanently transclude — every {@code conref} pointing
 * at a given target element is replaced by a copy of that element's
 * source text. The inverse of {@link ConrefExtractor}. Two-phase like
 * the other refactor engines: {@link #plan} computes the instances,
 * {@link #apply} rewrites.
 *
 * <p>The copied content is adjusted per destination: the target's own
 * {@code id} is dropped (a local id on the referencing element survives
 * instead), and relative {@code href}/{@code conref}/{@code src} paths
 * inside the copy are rebased from the target's directory to each
 * destination's directory so nested references keep resolving.</p>
 */
public final class ConrefInliner {

    /** One conref instance that will be replaced by the content. */
    public record Instance(File file, int line, String element, String conrefValue) {
    }

    /**
     * @param target      the file holding the reused element
     * @param elementId   the reused element's id
     * @param elementName the reused element's local name
     * @param elementText the element exactly as written in the target
     * @param instances   every conref to be inlined
     * @param warnings    non-fatal concerns
     */
    public record Plan(File target, String elementId, String elementName,
                       String elementText, List<Instance> instances,
                       List<String> warnings) {
    }

    /** Outcome of applying a plan. */
    public record ApplyResult(int inlined, int filesChanged, List<String> failures) {
    }

    private ConrefInliner() {
    }

    /**
     * Plans inlining every conref that resolves to
     * {@code target#...​/elementId}.
     *
     * @param onlyFile restrict to instances in this file; null = all
     * @throws IllegalArgumentException when the element can't be located
     */
    public static Plan plan(File target, String elementId, ReverseLinkIndex index,
                            File onlyFile) throws IOException {
        String content = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        int[] span = ReferenceRewriter.elementSpan(content, "id", elementId);
        if (span == null) {
            throw new IllegalArgumentException("No element with id=\"" + elementId
                    + "\" found in " + target.getName());
        }
        String elementText = content.substring(span[0], span[2]);
        String elementName = tagNameAt(content, span[0]);

        List<String> warnings = new ArrayList<>();
        List<Instance> instances = new ArrayList<>();
        for (Reference ref : index.usagesOf(target)) {
            if (ref.source() == null || !"conref".equals(ref.attribute())) {
                continue;
            }
            String fragment = ref.fragment();
            String elemPart = fragment == null ? null
                    : fragment.contains("/")
                            ? fragment.substring(fragment.indexOf('/') + 1) : fragment;
            if (!elementId.equals(elemPart)) {
                continue;
            }
            if (onlyFile != null && !sameFile(ref.source(), onlyFile)) {
                continue;
            }
            if (ref.element() != null && !ref.element().equals(elementName)) {
                warnings.add(ref.source().getName() + ":" + ref.line() + " — <"
                        + ref.element() + "> conrefs a <" + elementName
                        + "> — inlining changes the element type");
            }
            instances.add(new Instance(ref.source(), ref.line(),
                    ref.element(), ref.rawValue()));
        }
        if (instances.isEmpty()) {
            warnings.add("No conref instances for " + target.getName()
                    + "#…/" + elementId + (onlyFile != null
                            ? " in " + onlyFile.getName() : ""));
        }
        if (elementText.contains("conref=") || elementText.contains("conkeyref=")) {
            warnings.add("The inlined content itself reuses content — its own "
                    + "conrefs stay references");
        }
        return new Plan(target, elementId, elementName, elementText,
                instances, warnings);
    }

    /**
     * Applies a plan: each instance's element is replaced by the rebased
     * content copy. The target element itself is untouched (other
     * references may still point at it; safe delete reports when it goes
     * unused).
     */
    public static ApplyResult apply(Plan plan) {
        List<String> failures = new ArrayList<>();
        int inlined = 0;
        int filesChanged = 0;

        Map<File, List<Instance>> byFile = new LinkedHashMap<>();
        for (Instance instance : plan.instances()) {
            byFile.computeIfAbsent(instance.file(), f -> new ArrayList<>()).add(instance);
        }
        File targetDir = plan.target().getParentFile();

        for (Map.Entry<File, List<Instance>> entry : byFile.entrySet()) {
            File file = entry.getKey();
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                int doneHere = 0;
                for (Instance instance : entry.getValue()) {
                    int[] span = ReferenceRewriter.elementSpan(content,
                            "conref", instance.conrefValue());
                    if (span == null) {
                        failures.add(file + ": could not find conref=\""
                                + instance.conrefValue() + "\"");
                        continue;
                    }
                    String localId = idAttributeOf(content, span[0]);
                    String copy = stripId(plan.elementText());
                    copy = rebaseRelativeReferences(copy, targetDir, file.getParentFile());
                    if (localId != null) {
                        copy = injectId(copy, plan.elementName(), localId);
                    }
                    content = content.substring(0, span[0]) + copy
                            + content.substring(span[2]);
                    doneHere++;
                }
                if (doneHere > 0) {
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                    filesChanged++;
                    inlined += doneHere;
                }
            } catch (IOException e) {
                failures.add(file + ": " + e.getMessage());
            }
        }
        return new ApplyResult(inlined, filesChanged, failures);
    }

    // -------------------------------------------------------------------------
    // Copy adjustment
    // -------------------------------------------------------------------------

    private static final Pattern ID_IN_TAG = Pattern.compile("\\s+id\\s*=\\s*([\"']).*?\\1");
    private static final Pattern RELATIVE_REF = Pattern.compile(
            "\\b(href|conref|src)\\s*=\\s*([\"'])(.*?)\\2");

    /** Drops the id attribute from the copy's start tag. */
    static String stripId(String elementText) {
        int tagEnd = elementText.indexOf('>');
        if (tagEnd < 0) {
            return elementText;
        }
        String startTag = elementText.substring(0, tagEnd + 1);
        Matcher m = ID_IN_TAG.matcher(startTag);
        return m.find()
                ? startTag.substring(0, m.start()) + startTag.substring(m.end())
                        + elementText.substring(tagEnd + 1)
                : elementText;
    }

    /** Injects {@code id="localId"} after the element name in the copy. */
    static String injectId(String elementText, String elementName, String localId) {
        return "<" + elementName + " id=\"" + localId + "\""
                + elementText.substring(1 + elementName.length());
    }

    /**
     * Rewrites relative href/conref/src paths in the copy so they resolve
     * from {@code toDir} the way they did from {@code fromDir}.
     */
    static String rebaseRelativeReferences(String elementText, File fromDir, File toDir) {
        if (fromDir == null || toDir == null || sameFile(fromDir, toDir)) {
            return elementText;
        }
        Matcher m = RELATIVE_REF.matcher(elementText);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = m.group(3);
            String rebased = rebaseValue(value, fromDir, toDir);
            m.appendReplacement(out, Matcher.quoteReplacement(
                    m.group(1) + "=" + m.group(2) + rebased + m.group(2)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String rebaseValue(String value, File fromDir, File toDir) {
        int hash = value.indexOf('#');
        String path = hash >= 0 ? value.substring(0, hash) : value;
        String fragment = hash >= 0 ? value.substring(hash) : "";
        if (path.isEmpty() || new File(path).isAbsolute()
                || path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return value;                           // same-doc, absolute, or URL
        }
        File resolved = new File(fromDir, path);
        return ReferenceRewriter.relativePath(toDir, resolved) + fragment;
    }

    private static String idAttributeOf(String content, int elementStart) {
        int tagEnd = content.indexOf('>', elementStart);
        if (tagEnd < 0) {
            return null;
        }
        Matcher m = Pattern.compile("\\sid\\s*=\\s*([\"'])(.*?)\\1")
                .matcher(content.substring(elementStart, tagEnd + 1));
        return m.find() ? m.group(2) : null;
    }

    private static String tagNameAt(String content, int lt) {
        int p = lt + 1;
        while (p < content.length() && !Character.isWhitespace(content.charAt(p))
                && content.charAt(p) != '>' && content.charAt(p) != '/') {
            p++;
        }
        return content.substring(lt + 1, p);
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
