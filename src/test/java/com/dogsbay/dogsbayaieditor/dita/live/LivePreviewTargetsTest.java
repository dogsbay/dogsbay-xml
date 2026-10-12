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

package com.dogsbay.dogsbayaieditor.dita.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/** Which deliverable an agent's live preview request means. */
class LivePreviewTargetsTest {

    private static Deliverable d(String name, String transtype) {
        return new Deliverable(name, Path.of("guide.ditamap"), List.of(), transtype, Path.of("out/" + name), List.of(), null, name);
    }

    private static final Deliverable PDF = d("pdf", "pdf");
    private static final Deliverable SITE = d("site", "dogsbay-site");
    private static final Deliverable MD = d("md", "dogsbay");

    @Test
    void takesTheNamedDeliverable() {
        assertThat(LivePreviewTargets.choose(List.of(PDF, SITE, MD), "md", "site")).isEqualTo(MD);
    }

    @Test
    void refusesAMissingOrNonDogsbayDeliverableAndNamesThePreviewableOnes() {
        assertThatThrownBy(() -> LivePreviewTargets.choose(List.of(PDF, SITE), "web", null))
                .hasMessageContaining("No deliverable named 'web'");
        assertThatThrownBy(() -> LivePreviewTargets.choose(List.of(PDF, SITE), "pdf", null))
                .hasMessageContaining("publishes pdf").hasMessageContaining("Previewable: site.");
    }

    @Test
    void withoutANameTakesTheOnlyPreviewableDeliverable() {
        assertThat(LivePreviewTargets.choose(List.of(PDF, SITE), null, "pdf")).isEqualTo(SITE);
        assertThat(LivePreviewTargets.choose(List.of(PDF, SITE), " ", null)).isEqualTo(SITE);
    }

    @Test
    void withoutANameAndSeveralTakesTheActiveOneOrAsks() {
        assertThat(LivePreviewTargets.choose(List.of(PDF, SITE, MD), null, "md")).isEqualTo(MD);
        assertThatThrownBy(() -> LivePreviewTargets.choose(List.of(PDF, SITE, MD), null, "pdf"))
                .hasMessageContaining("name one").hasMessageContaining("Previewable: site, md.");
    }

    @Test
    void refusesAProjectWithoutADogsbayDeliverable() {
        assertThatThrownBy(() -> LivePreviewTargets.choose(List.of(PDF), null, null))
                .hasMessageContaining("No deliverable in this project publishes a dogsbay site");
    }
}
