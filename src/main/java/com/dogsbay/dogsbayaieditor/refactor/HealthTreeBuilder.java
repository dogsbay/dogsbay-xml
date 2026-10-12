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

package com.dogsbay.dogsbayaieditor.refactor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;

/**
 * Builds a display tree from a {@link HealthReport}. Pure data — the
 * dialog renders it, tests assert on it.
 */
public final class HealthTreeBuilder {

    /**
     * One tree node. Leaf nodes carry a navigation target
     * ({@code file} non-null, {@code line} 1-based or -1).
     */
    public record Node(String label, File file, int line, List<Node> children) {

        public static Node category(String label, List<Node> children) {
            return new Node(label, null, -1, children);
        }

        public static Node leaf(String label, File file, int line) {
            return new Node(label, file, line, List.of());
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }
    }

    private HealthTreeBuilder() {}

    /**
     * Builds the report tree. Empty categories are omitted; a clean report
     * yields a single "No issues found" node.
     */
    public static Node build(HealthReport report, File root) {
        List<Node> categories = new ArrayList<>();

        if (!report.brokenReferences().isEmpty()) {
            categories.add(Node.category(
                "Broken references (" + report.brokenReferences().size() + ")",
                report.brokenReferences().stream().map(r -> brokenRefLeaf(r, root)).toList()));
        }
        if (!report.undefinedKeys().isEmpty()) {
            categories.add(Node.category(
                "Undefined keys (" + report.undefinedKeys().size() + ")",
                report.undefinedKeys().stream().map(r -> brokenRefLeaf(r, root)).toList()));
        }
        if (!report.unusedKeys().isEmpty()) {
            categories.add(Node.category(
                "Unused keys (" + report.unusedKeys().size() + ")",
                report.unusedKeys().stream().map(k -> unusedKeyLeaf(k, root)).toList()));
        }
        if (!report.orphanTopics().isEmpty()) {
            categories.add(Node.category(
                "Orphan topics (" + report.orphanTopics().size() + ")",
                report.orphanTopics().stream()
                    .map(p -> Node.leaf(relative(p, root), new File(p), -1)).toList()));
        }

        if (categories.isEmpty()) {
            return new Node("Project Health — no issues found", null, -1,
                List.of(Node.category("No issues found", List.of())));
        }
        int total = categories.stream()
            .mapToInt(c -> c.children().size())
            .sum();
        return new Node("Project Health — " + total + (total == 1 ? " issue" : " issues"),
            null, -1, categories);
    }

    private static Node brokenRefLeaf(BrokenRef ref, File root) {
        String label = relative(ref.source(), root) + ":" + ref.line()
            + " — " + ref.attribute() + "=\"" + ref.value() + "\" (" + ref.reason() + ")";
        return Node.leaf(label, new File(ref.source()), ref.line());
    }

    private static Node unusedKeyLeaf(KeyInfo key, File root) {
        String label = key.name() + " — defined in "
            + relative(key.definedIn(), root) + ":" + key.line();
        return Node.leaf(label, new File(key.definedIn()), key.line());
    }

    private static String relative(String absolutePath, File root) {
        if (root == null) {
            return absolutePath;
        }
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path filePath = new File(absolutePath).toPath().toAbsolutePath().normalize();
            if (filePath.startsWith(rootPath)) {
                return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {
            // fall through to absolute
        }
        return absolutePath;
    }
}
