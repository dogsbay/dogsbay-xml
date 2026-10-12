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

package com.dogsbay.dogsbayaieditor.samples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ProjectHealthCommand;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;

/**
 * The bundled sample is what a first-time user opens, so its own health is the
 * editor's first impression: Check Project on a fresh copy says "Ready".
 *
 * <p>This is a gate on the bundle, not on the editor. {@code bundleDemo} takes a
 * snapshot of one branch of a repository that also carries twenty-seven
 * unfinished stages of the same project, and the snapshot is committed as a
 * binary resource — so a wrong branch, or a finished branch that later grew a
 * broken reference, reaches a release looking exactly like a right one. Nothing
 * else in the build opens the zip and reads what is inside it.
 *
 * <p>The legs are the ones the sample's own gate runs. {@code validation} is
 * left out for the reason that gate leaves it out: the sample's
 * {@code learning/} topics are Learning and Training specialisations, and the
 * editor's bundled catalog has no L&amp;T DTDs, so every one of them would be
 * reported unresolvable here. The tutorial repository validates them against
 * DITA-OT's catalog instead. {@code conditions} needs a subject scheme passed
 * explicitly, which is that gate's separate {@code validate-conditions} step.
 */
class BundledSampleHealthTest {

    @TempDir Path tmp;

    /** Reuse, element ids, metadata policy, conref push, index, format, markers, and the house rules. */
    private static final List<String> LEGS = List.of("reuse", "elementIds", "metadata", "proposals",
            "conrefPush", "index", "format", "markers", "schematron");

    @Test
    void aFreshCopyOfTheBundledSampleIsClean() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);

        // Null root map and null schematron: the project's own .dogsbay/config.xml names both,
        // which is also how the editor finds them when the user opens the copy.
        ProjectHealthReport health = new HeadlessExecutor().execute(
                new ProjectHealthCommand(dest.getAbsolutePath(), null, null, LEGS, null, false));

        assertThat(health.isClean())
                .as("the bundled sample must open clean; health was %s", health)
                .isTrue();
    }

    @Test
    void theSampleStillCarriesWhatTheLessonsBuiltUpTo() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);

        // The finished guide, not an early stage: the last lessons add the bookmap, the
        // collection, the subject scheme and the house rules, and each is one file whose
        // absence would mean a branch before stage 26 had been bundled.
        assertThat(new File(dest, "audacity-book.ditamap")).isFile();
        assertThat(new File(dest, "audacity-collection.ditamap")).isFile();
        assertThat(new File(dest, "subject-scheme.ditamap")).isFile();
        assertThat(new File(dest, "house-style.sch")).isFile();
        assertThat(new File(dest, "project.json")).isFile();

        // The tutorial repository's branch machinery is export-ignored: it acts on refs
        // that a copied folder does not have, and rebase-stages.sh rewrites them.
        assertThat(new File(dest, "scripts/rebase-stages.sh")).doesNotExist();
        assertThat(new File(dest, "scripts/check-all-stages.sh")).doesNotExist();
        assertThat(new File(dest, "docs-dev")).doesNotExist();

        // And the old demo's answer key, which named every planted fault, is gone with it.
        assertThat(new File(dest, "ISSUES.md")).doesNotExist();
        assertThat(new File(dest, "DEMO.md")).doesNotExist();
    }
}
