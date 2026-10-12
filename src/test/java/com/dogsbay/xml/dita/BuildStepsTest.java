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

package com.dogsbay.xml.dita;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.dita.TargetTimingListener.TargetTime;

/** DITA-OT's Ant targets grouped into the build steps the editor shows. */
class BuildStepsTest {

    private static List<TargetTime> targets(Object... nameAndMillis) {
        java.util.List<TargetTime> out = new java.util.ArrayList<>();
        for (int i = 0; i < nameAndMillis.length; i += 2) {
            out.add(new TargetTime((String) nameAndMillis[i], ((Number) nameAndMillis[i + 1]).longValue()));
        }
        return out;
    }

    @Test
    void groupsAnHtml5BuildIntoLoadingInitializationPreprocessingAndTransforming() {
        List<BuildStep> steps = BuildSteps.group(targets(
                "build-init", 40, "preprocess2.init", 5, "map-reader", 300, "map-keyref", 120,
                "topic-conref", 80, "preprocess2.post", 1, "preprocess2", 0,
                "html5.init", 2, "html5.topics", 900, "dita2html5", 0), 250, "html5");

        assertThat(steps).extracting(BuildStep::name).containsExactly(
                "Loading DITA-OT", "Initialization", "Preprocessing", "Transforming (html5)");
        assertThat(steps).extracting(BuildStep::millis).containsExactly(250L, 40L, 506L, 902L);
        assertThat(steps.get(2).stages()).extracting(BuildStep::name)
                .containsExactly("preprocess2.init", "map-reader", "map-keyref", "topic-conref", "preprocess2.post", "preprocess2");
        assertThat(BuildSteps.total(steps)).isEqualTo(1698L);
    }

    @Test
    void namesTheDogsbaySiteStepsAndLeavesOutTheAggregateTargets() {
        List<BuildStep> steps = BuildSteps.group(targets(
                "dogsbay.init", 3, "build-init", 20, "preprocess2.init", 4, "map-reader", 100, "preprocess2", 0,
                "dogsbay.convert", 1500, "dita2dogsbay", 0,
                "dogsbay.site", 2000, "dita2dogsbay-site", 0), 0, "dogsbay-site");

        assertThat(steps).extracting(BuildStep::name).containsExactly(
                "Initialization", "Preprocessing", "Converting to dogsbay Markdown", "Building the site");
        assertThat(steps.get(0).millis()).isEqualTo(23L);
        assertThat(steps.get(3).millis()).isEqualTo(2000L);
    }

    /**
     * dogsbay.site carries the site build's own time now that it runs the builder itself.
     * While it was the aggregate of four sub-targets it was deliberately skipped, so a
     * site build would report 0 ms if that skip outlived the sub-targets.
     */
    @Test
    void timesTheSiteStepRatherThanSkippingItAsAnAggregate() {
        List<BuildStep> steps = BuildSteps.group(targets(
                "dogsbay.init", 1, "preprocess2", 0, "dogsbay.convert", 10, "dogsbay.site", 777), 0, "dogsbay-site");
        assertThat(steps).extracting(BuildStep::name).contains("Building the site");
        assertThat(BuildSteps.total(steps)).isEqualTo(788L);
    }

    @Test
    void keepsAPluginTargetInsidePreprocessingInThatStep() {
        List<BuildStep> steps = BuildSteps.group(targets(
                "preprocess2.init", 1, "my-plugin-step", 50, "topic-conref", 10, "preprocess2", 0, "dita2x", 5), 0, "x");
        assertThat(steps.get(1).stages()).extracting(BuildStep::name).contains("my-plugin-step");
        assertThat(steps.get(2).name()).isEqualTo("Transforming (x)");
    }

    @Test
    void namesTheStepATargetBelongsToForProgress() {
        assertThat(BuildProgress.stepOf("map-keyref")).isEqualTo("Preprocessing (map-keyref)");
        assertThat(BuildProgress.stepOf("dogsbay.site")).isEqualTo("Building the site");
        assertThat(BuildProgress.stepOf("html5.topics")).isEqualTo("Running html5.topics");
    }

    @Test
    void reportsProgressToTheListenerBoundToTheThread() throws Exception {
        List<String> seen = new java.util.ArrayList<>();
        assertThat(BuildProgress.targetCallback()).isNull();
        BuildProgress.with((deliverable, step) -> seen.add(deliverable + ": " + step), () -> {
            BuildProgress.deliverable("site");
            BuildProgress.targetCallback().accept("dogsbay.convert");
            return null;
        });
        assertThat(seen).containsExactly("site: Converting to dogsbay Markdown");
        assertThat(BuildProgress.targetCallback()).isNull();
    }
}
