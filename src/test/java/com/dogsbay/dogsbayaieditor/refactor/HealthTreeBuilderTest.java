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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;
import com.dogsbay.dogsbayaieditor.refactor.HealthTreeBuilder.Node;

class HealthTreeBuilderTest {

    private final File root = new File("/projects/docs");

    @Test
    void buildsCategoriesWithCountsAndNavigationTargets() {
        HealthReport report = new HealthReport(
            List.of(new BrokenRef("/projects/docs/map.ditamap", 8, "href",
                "missing.dita", "target not found")),
            List.of(new BrokenRef("/projects/docs/topics/intro.dita", 14, "keyref",
                "ghost", "key not defined")),
            List.of(new KeyInfo("never-used", "/projects/docs/map.ditamap", 5,
                "topics/a.dita", "/projects/docs/topics/a.dita", "A")),
            List.of("/projects/docs/topics/orphan.dita"));

        Node tree = HealthTreeBuilder.build(report, root);

        assertEquals("Project Health — 4 issues", tree.label());
        assertEquals(4, tree.children().size());
        assertEquals("Broken references (1)", tree.children().get(0).label());
        assertEquals("Undefined keys (1)", tree.children().get(1).label());
        assertEquals("Unused keys (1)", tree.children().get(2).label());
        assertEquals("Orphan topics (1)", tree.children().get(3).label());

        Node broken = tree.children().get(0).children().get(0);
        assertEquals("map.ditamap:8 — href=\"missing.dita\" (target not found)", broken.label());
        assertEquals(new File("/projects/docs/map.ditamap"), broken.file());
        assertEquals(8, broken.line());
        assertTrue(broken.isLeaf());

        Node unused = tree.children().get(2).children().get(0);
        assertEquals("never-used — defined in map.ditamap:5", unused.label());
        assertEquals(5, unused.line());

        Node orphan = tree.children().get(3).children().get(0);
        assertEquals("topics/orphan.dita", orphan.label());
        assertEquals(-1, orphan.line());
        assertEquals(new File("/projects/docs/topics/orphan.dita"), orphan.file());
    }

    @Test
    void omitsEmptyCategories() {
        HealthReport report = new HealthReport(
            List.of(new BrokenRef("/projects/docs/map.ditamap", 8, "href",
                "missing.dita", "target not found")),
            List.of(), List.of(), List.of());

        Node tree = HealthTreeBuilder.build(report, root);

        assertEquals("Project Health — 1 issue", tree.label());
        assertEquals(1, tree.children().size());
        assertEquals("Broken references (1)", tree.children().get(0).label());
    }

    @Test
    void cleanReportSaysNoIssues() {
        HealthReport report = new HealthReport(List.of(), List.of(), List.of(), List.of());

        Node tree = HealthTreeBuilder.build(report, root);

        assertEquals("Project Health — no issues found", tree.label());
        assertEquals(1, tree.children().size());
        assertEquals("No issues found", tree.children().get(0).label());
    }
}
