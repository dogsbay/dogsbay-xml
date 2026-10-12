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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * Which deliverable the reader is looking at, asked without a running editor.
 *
 * <p>Manage Deliverables' Add, Edit and Delete each have to know whether the deliverable in hand is the active
 * one — Add and Edit so they can leave a selection the reader made alone, Delete so it does not leave
 * {@code local.xml} naming something the project no longer has. All three now ask the same question, and the
 * rule Add and Edit apply on top of it is here too.
 */
class ActiveDeliverableIdentityTest {

    private static Deliverable deliverable(String name, String sourceFile) {
        return new Deliverable(name, Path.of("guide.ditamap"), List.of(), "html5",
                null, List.of(), sourceFile == null ? null : Path.of(sourceFile), null);
    }

    @Test
    void theSameFileAndNameIsTheActiveOne() {
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(
                deliverable("full", "/p/project.json"), deliverable("full", "/p/project.json"))).isTrue();
    }

    @Test
    void aBranchVariantOfItCountsAsIt() {
        // A variant is derived from its base and is never one of ProjectContext.deliverables(), so identity
        // would answer no for exactly the reader whose selection is most easily lost.
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(
                deliverable("full · mac", "/p/project.json"),
                deliverable("full", "/p/project.json"))).isTrue();
    }

    @Test
    void anotherDeliverableIsNotIt() {
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(
                deliverable("full", "/p/project.json"),
                deliverable("book-pdf", "/p/project.json"))).isFalse();
    }

    @Test
    void aNamePrefixIsNotABranchVariant() {
        // "full-again" starts with "full" and is a different deliverable; only "full · <branch>" is a variant
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(
                deliverable("full-again", "/p/project.json"),
                deliverable("full", "/p/project.json"))).isFalse();
    }

    @Test
    void theSameNameInAnotherProjectFileIsNotIt() {
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(
                deliverable("full", "/p/project.json"),
                deliverable("full", "/p/other.json"))).isFalse();
    }

    @Test
    void nothingActiveIsNotIt() {
        assertThat(DogsBayAIEditor.isTheActiveDeliverable(null, deliverable("full", "/p/project.json"))).isFalse();
    }

    // -------------------------------------------------------------------------
    // Whether an add or an edit changes which deliverable is active
    // -------------------------------------------------------------------------

    @Test
    void addingADeliverableLeavesTheActiveOneAlone() {
        // The reported defect: adding book-pdf while "full" was active switched the status bar to book-pdf,
        // and the lessons say full stays active. Set active is the button for that.
        assertThat(DogsBayAIEditor.editShouldBecomeActive(
                deliverable("full", "/p/project.json"), null)).isFalse();
    }

    @Test
    void theFirstDeliverableInAProjectBecomesActive() {
        // Nothing to lose, and a project with a deliverable and no active one would make the reader go and
        // set it before anything else worked.
        assertThat(DogsBayAIEditor.editShouldBecomeActive(null, null)).isTrue();
    }

    @Test
    void editingTheActiveDeliverableFollowsIt() {
        // This edit may have just renamed it, and the old name would no longer resolve.
        Deliverable full = deliverable("full", "/p/project.json");

        assertThat(DogsBayAIEditor.editShouldBecomeActive(full, full)).isTrue();
    }

    @Test
    void editingTheActiveDeliverablesBranchVariantFollowsItToo() {
        assertThat(DogsBayAIEditor.editShouldBecomeActive(
                deliverable("full · mac", "/p/project.json"),
                deliverable("full", "/p/project.json"))).isTrue();
    }

    @Test
    void editingSomeOtherDeliverableLeavesTheActiveOneAlone() {
        assertThat(DogsBayAIEditor.editShouldBecomeActive(
                deliverable("full", "/p/project.json"),
                deliverable("book-pdf", "/p/project.json"))).isFalse();
    }
}

