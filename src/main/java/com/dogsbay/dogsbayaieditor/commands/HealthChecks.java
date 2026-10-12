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

package com.dogsbay.dogsbayaieditor.commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.dogsbay.dogsbayaieditor.commands.results.HealthMarker;
import com.dogsbay.xml.review.DecodedFile;
import com.dogsbay.xml.review.ProposalIndex;
import com.dogsbay.xml.review.ProposalOps;
import com.dogsbay.xml.review.XmlTokens;
import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * The parts of project_health that read files directly: authoring leftovers, and the text that would ship
 * for a file with open review proposals (every change accepted), against which findings are re-judged.
 */
final class HealthChecks {

    private static final Pattern MARKER = Pattern.compile("\\b(TODO|FIXME|TBD)\\b");

    private HealthChecks() {
    }

    /** Whether a path is a DITA or XML document the checks read. */
    static boolean isDocument(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".dita") || n.endsWith(".ditamap") || n.endsWith(".bookmap") || n.endsWith(".xml");
    }

    static String read(Path file) {
        try {
            return DecodedFile.read(file).text();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The text that would ship for each file with open review changes: every change accepted (by builds,
     * text marked deleted is left out). Comments stay: a writer's draft-comment is not a change.
     */
    static Map<Path, String> acceptedViews(List<Path> files) {
        Map<Path, String> out = new LinkedHashMap<>();
        for (Path f : files) {
            if (!isDocument(f)) {
                continue;
            }
            String text = read(f);
            if (text == null || !ProposalIndex.mayHaveProposals(text)) {
                continue;
            }
            try {
                String accepted = ProposalOps.acceptAll(text, null);
                if (!accepted.equals(text)) {
                    out.put(key(f), accepted);
                }
            } catch (RuntimeException e) {
                // marks the editor cannot settle: judge the file as it stands
            }
        }
        return out;
    }

    /** A file's identity for matching findings, whatever form the finding names it in (path or file: URI). */
    static Path key(String file) {
        if (file == null) {
            return null;
        }
        try {
            Path p = file.startsWith("file:") ? Path.of(java.net.URI.create(file)) : Path.of(file);
            return key(p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Path key(Path file) {
        return WriteGate.real(file.toAbsolutePath().normalize());
    }

    /** Elements whose text is content, where a TODO is an example rather than a note to the author. */
    private static final java.util.Set<String> VERBATIM = java.util.Set.of("codeblock", "pre", "screen", "msgblock",
            "lines", "codeph");

    /**
     * Whether the element holding {@code attribute="value"} on {@code line} of {@code file} is marked deleted
     * by a review proposal (it or an ancestor): accepting the proposals removes the reference. False when no
     * such element is found, so a finding is only ever set aside on evidence.
     */
    static boolean inDeletedProposal(Path file, int line, String attribute, String value) {
        String text = read(file);
        if (text == null || attribute == null || value == null) {
            return false;
        }
        List<Token> tokens = XmlTokens.tokenize(text);
        boolean found = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if ((t.kind() != Kind.START && t.kind() != Kind.EMPTY) || !t.attrs().containsKey(attribute)
                    || !normalize(t.attrs().get(attribute)).equals(normalize(value))) {
                continue;
            }
            // the parser reports a tag's line where the tag ends; a tag may span lines
            if (line > 0 && (line < line(text, t.start()) || line > line(text, t.end()))) {
                continue;
            }
            found = true;
            if (!deleted(tokens, i)) {
                return false;
            }
        }
        return found;
    }

    /** Whether every index redirect to {@code target} in {@code file} is inside a deleted proposal. */
    static boolean seeInDeletedProposal(Path file, String target) {
        String text = read(file);
        if (text == null || target == null) {
            return false;
        }
        List<Token> tokens = XmlTokens.tokenize(text);
        boolean found = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Kind.START || !("index-see".equals(t.name()) || "index-see-also".equals(t.name()))) {
                continue;
            }
            String content = text.substring(t.end(), tokens.get(XmlTokens.closeOf(tokens, i)).start())
                    .replaceAll("<[^>]*>", "");
            if (!normalize(XmlTokens.unescape(content)).equals(normalize(target))) {
                continue;
            }
            found = true;
            if (!deleted(tokens, i)) {
                return false;
            }
        }
        return found;
    }

    /** Whether the element at {@code index}, or an ancestor, is marked deleted by a review proposal. */
    private static boolean deleted(List<Token> tokens, int index) {
        for (int p = index; p >= 0; p = tokens.get(p).parent()) {
            Token t = tokens.get(p);
            String props = t.attrs().get("otherprops");
            if ("deleted".equals(t.attrs().get("status")) && props != null
                    && props.contains(com.dogsbay.xml.review.Marks.DELETED)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    /**
     * Authoring leftovers in the files as they stand (so lines match the file): TODO/FIXME/TBD, draft-comments,
     * required-cleanup, tasks without steps. Skipped: what a proposal deletes, and text in code, where a TODO
     * is an example rather than a note.
     */
    static List<HealthMarker> markers(List<Path> files) {
        List<HealthMarker> out = new ArrayList<>();
        for (Path f : files) {
            if (!isDocument(f)) {
                continue;
            }
            String text = read(f);
            if (text != null) {
                out.addAll(markers(f.toString(), text));
            }
        }
        return out;
    }

    static List<HealthMarker> markers(String file, String text) {
        List<HealthMarker> out = new ArrayList<>();
        List<Token> tokens = XmlTokens.tokenize(text);
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.parent() >= 0 && (deleted(tokens, t.parent()) || insideAny(tokens, i, VERBATIM))) {
                continue;
            }
            switch (t.kind()) {
                case COMMENT -> {
                    if (MARKER.matcher(t.text(text)).find()) {
                        out.add(new HealthMarker(file, line(text, t.start()), "todo-comment", excerpt(t.text(text)
                                .replaceAll("^<!--|-->$", ""))));
                    }
                }
                case TEXT -> {
                    if (MARKER.matcher(t.text(text)).find() && !inside(tokens, i, "draft-comment")) {
                        out.add(new HealthMarker(file, line(text, t.start()), "todo-text", excerpt(t.text(text))));
                    }
                }
                case START, EMPTY -> {
                    if (deleted(tokens, i)) {
                        continue;
                    }
                    if ("draft-comment".equals(t.name()) || "required-cleanup".equals(t.name())) {
                        String content = t.kind() == Kind.EMPTY ? ""
                                : text.substring(t.end(), tokens.get(XmlTokens.closeOf(tokens, i)).start());
                        out.add(new HealthMarker(file, line(text, t.start()), t.name(),
                                excerpt(content.replaceAll("<[^>]*>", ""))));
                    } else if ("taskbody".equals(t.name()) && !t.attrs().containsKey("conref")
                            && !t.attrs().containsKey("conkeyref") && !hasChild(tokens, i, "steps", "steps-unordered",
                            "steps-informal")) {
                        out.add(new HealthMarker(file, line(text, t.start()), "task-without-steps",
                                "a task body with no steps"));
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static boolean insideAny(List<Token> tokens, int index, java.util.Set<String> names) {
        for (int p = tokens.get(index).parent(); p >= 0; p = tokens.get(p).parent()) {
            if (names.contains(tokens.get(p).name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(List<Token> tokens, int index, String name) {
        for (int p = tokens.get(index).parent(); p >= 0; p = tokens.get(p).parent()) {
            if (name.equals(tokens.get(p).name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChild(List<Token> tokens, int index, String... names) {
        if (tokens.get(index).kind() == Kind.EMPTY) {
            return false;
        }
        int close = XmlTokens.closeOf(tokens, index);
        for (int i = index + 1; i < close; i++) {
            Token c = tokens.get(i);
            if (c.parent() == index && (c.kind() == Kind.START || c.kind() == Kind.EMPTY)) {
                for (String n : names) {
                    if (n.equals(c.name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int line(String text, int offset) {
        int n = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static String excerpt(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() > 100 ? t.substring(0, 99) + "…" : t;
    }
}
