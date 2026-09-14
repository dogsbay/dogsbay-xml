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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ProposalIndex;

/**
 * Proposals across a project: every DITA or XML file under the root that
 * carries at least one. No editor types, so it is testable without a window.
 */
public final class ProjectProposals {

    /** One file's proposals; {@code file} is a {@link #key}. */
    public record FileProposals(Path file, List<Proposal> proposals) {
        public FileProposals {
            file = key(file);
            proposals = List.copyOf(proposals);
        }
    }

    private static final Set<String> EXTENSIONS = Set.of(".dita", ".ditamap", ".bookmap", ".xml");
    /** Build output and tooling folders; hidden folders are skipped as well. */
    private static final Set<String> SKIPPED = Set.of("out", "build", "dist", "temp", "node_modules", "target");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern ENCODING = Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    private ProjectProposals() {
    }

    /**
     * The form paths are compared in: the real path when the file exists, so
     * a symlink or a differently cased name is one file; otherwise absolute
     * and normalized.
     */
    public static Path key(Path file) {
        Path normal = file.toAbsolutePath().normalize();
        try {
            return normal.toRealPath();
        } catch (IOException | RuntimeException e) {
            return normal;
        }
    }

    /**
     * Scan the project.
     *
     * @param root     the project root
     * @param liveText text of open documents by {@link #key}, preferred over disk
     * @return files with proposals, in path order
     */
    public static List<FileProposals> scan(Path root, Map<Path, String> liveText) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        Path start = key(root);   // a symlinked root is walked through its target
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return dir.equals(start) || !skipped(dir.getFileName())
                            ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && isCandidate(file)
                            && (attrs.size() <= MAX_BYTES || liveText.containsKey(key(file)))) {
                        files.add(key(file));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return List.of();
        }
        files.sort(null);
        List<FileProposals> out = new ArrayList<>();
        for (Path f : files) {
            String text = liveText.get(f);
            if (text == null) {
                try {
                    text = read(f);
                } catch (IOException | RuntimeException e) {
                    continue;   // unreadable: not something an agent proposed into
                }
            }
            FileProposals fp = of(f, text);
            if (fp != null) {
                out.add(fp);
            }
        }
        return out.stream().distinct().toList();
    }

    /**
     * Whether {@link #scan} of {@code root} would look at {@code file}: under
     * the root, not in a skipped folder, with a DITA or XML extension.
     */
    public static boolean inScope(Path root, Path file) {
        if (root == null || file == null) {
            return false;
        }
        Path r = key(root);
        Path f = key(file);
        if (!f.startsWith(r) || f.equals(r) || !isCandidate(f)) {
            return false;
        }
        Path rel = r.relativize(f);
        for (int i = 0; i < rel.getNameCount() - 1; i++) {
            if (skipped(rel.getName(i))) {
                return false;
            }
        }
        return true;
    }

    /** One file's proposals from its text, or null when it has none. */
    public static FileProposals of(Path file, String text) {
        if (!ProposalIndex.mayHaveProposals(text)) {
            return null;
        }
        List<Proposal> ps = ProposalIndex.scan(text);
        return ps.isEmpty() ? null : new FileProposals(file, ps);
    }

    /**
     * {@code files} with {@code file}'s entry replaced by {@code live}, or
     * removed when {@code live} is null; path order is kept.
     */
    public static List<FileProposals> replace(List<FileProposals> files, Path file, FileProposals live) {
        Path k = key(file);
        List<FileProposals> out = new ArrayList<>();
        for (FileProposals fp : files) {
            if (!fp.file().equals(k)) {
                out.add(fp);
            }
        }
        if (live != null) {
            int at = 0;
            while (at < out.size() && out.get(at).file().compareTo(live.file()) < 0) {
                at++;
            }
            out.add(at, live);
        }
        return out;
    }

    /**
     * A file's text in the encoding it declares: a byte order mark, else the
     * XML declaration, else UTF-8. The mark is dropped, as the editor does.
     */
    static String read(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        Charset charset = StandardCharsets.UTF_8;
        Matcher m = ENCODING.matcher(new String(b, 0, Math.min(b.length, 200), StandardCharsets.ISO_8859_1));
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1));
            } catch (RuntimeException unknown) {
                // keep UTF-8
            }
        }
        return charset.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
    }

    private static boolean skipped(Path name) {
        String n = name == null ? "" : name.toString();
        return n.startsWith(".") || SKIPPED.contains(n);
    }

    static boolean isCandidate(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(n.substring(dot));
    }
}
