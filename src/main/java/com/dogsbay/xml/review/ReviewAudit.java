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
package com.dogsbay.xml.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The accept-time provenance record: who decided what about each proposal.
 * One JSON object per line in {@code <root>/.dogsbay/agent-audit/review.jsonl},
 * beside the command audit log and under the same self-ignoring folder. No
 * JSON library, so the review package stays free of dependencies.
 */
public final class ReviewAudit {

    public static final String DIR = ".dogsbay/agent-audit";
    public static final String FILE = "review.jsonl";

    public record Entry(Instant at, String decidedBy, String decision, String proposalAuthor,
            String kind, String file, String excerpt) {}

    private final Supplier<Path> root;
    private final Object lock = new Object();

    public ReviewAudit(Supplier<Path> projectRoot) {
        this.root = projectRoot;
    }

    /** Record a decision; a null root means no project and nothing is written. */
    public void record(String decidedBy, String decision, Proposal p, Path file) {
        Path dir = root.get();
        if (dir == null) {
            return;
        }
        Entry e = new Entry(Instant.now(), decidedBy, decision, p.author(), p.kind().name(),
                file == null ? null : file.toString(), excerpt(p.text()));
        try {
            Path target = dir.resolve(DIR).resolve(FILE);
            synchronized (lock) {
                Files.createDirectories(target.getParent());
                Path ignore = target.getParent().resolve(".gitignore");
                if (!Files.exists(ignore)) {
                    Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
                }
                Files.writeString(target, toJson(e) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ignored) {
            // provenance is best-effort; never fail the decision
        }
    }

    public static List<Entry> read(Path projectRoot) {
        Path file = projectRoot.resolve(DIR).resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Entry e = fromJson(line);
                if (e != null) {
                    out.add(e);
                }
            }
        } catch (IOException ignored) {
            // unreadable log: nothing to show
        }
        return out;
    }

    static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").strip();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    static String toJson(Entry e) {
        return "{" + field("at", e.at().toString()) + "," + field("by", e.decidedBy()) + ","
                + field("decision", e.decision()) + "," + field("author", e.proposalAuthor()) + ","
                + field("kind", e.kind()) + "," + field("file", e.file()) + "," + field("excerpt", e.excerpt()) + "}";
    }

    private static String field(String k, String v) {
        return "\"" + k + "\":" + (v == null ? "null" : "\"" + escape(v) + "\"");
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** A deliberately small parser for the flat objects this class writes. */
    static Entry fromJson(String line) {
        try {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            java.util.regex.Matcher x = java.util.regex.Pattern
                    .compile("\"([a-z]+)\":(null|\"((?:[^\"\\\\]|\\\\.)*)\")").matcher(line);
            while (x.find()) {
                m.put(x.group(1), x.group(2).equals("null") ? null : unescape(x.group(3)));
            }
            if (!m.containsKey("at")) {
                return null;
            }
            return new Entry(Instant.parse(m.get("at")), m.get("by"), m.get("decision"), m.get("author"),
                    m.get("kind"), m.get("file"), m.get("excerpt"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
