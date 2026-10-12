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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups a DITA-OT build's Ant targets into named steps, the same names for every
 * transtype and, later, every engine: Initialization, Preprocessing (its targets as
 * stages), Converting to dogsbay Markdown, the dogsbay site steps, and Transforming
 * for the other output types (html5, pdf).
 */
public final class BuildSteps {

    public static final String LOADING = "Loading DITA-OT";
    public static final String INITIALIZATION = "Initialization";
    public static final String PREPROCESSING = "Preprocessing";
    public static final String CONVERTING = "Converting to dogsbay Markdown";

    /**
     * The dogsbay plugin's targets and their step names. dogsbay-site used to be four
     * steps (scaffold, generate Astro source, install packages, Astro build); the
     * dogsbay-ssg builder does all of it in one pass, so there is one step to report.
     */
    private static final Map<String, String> DOGSBAY = Map.of(
            "dogsbay.convert", CONVERTING,
            "dogsbay.site", "Building the site");

    /** Targets that end preprocessing: DITA-OT's preprocess and preprocess2 (map-first). */
    private static final Set<String> PREPROCESS_END = Set.of("preprocess", "preprocess2");

    private BuildSteps() {}

    /** The dogsbay plugin step a target is, or null. */
    static String dogsbayStep(String target) {
        return DOGSBAY.get(target);
    }

    /** Whether a target is part of preprocessing (before its end target runs). */
    static boolean isPreprocessing(String target) {
        return target.startsWith("preprocess") || target.startsWith("map-") || target.startsWith("topic-")
                || target.equals("ditaval-merge") || target.equals("gen-list") || target.equals("debug-filter")
                || target.startsWith("copy-") || target.equals("clean-preprocess") || target.equals("keyref")
                || target.equals("conref") || target.equals("coderef") || target.equals("mapref")
                || target.equals("move-meta-entries") || target.equals("mappull") || target.equals("chunk")
                || target.equals("maplink") || target.equals("topicpull") || target.equals("flag-module")
                || target.equals("clean-map") || target.equals("conrefpush") || target.equals("profile")
                || target.equals("topic-fragment") || target.equals("branch-filter") || target.equals("mapref-check");
    }

    /**
     * The steps of a build from its targets in the order they ran.
     *
     * @param loadMillis time spent loading DITA-OT's build files before the first target (0 to leave out)
     * @param transtype  the output type, named in the Transforming step
     */
    public static List<BuildStep> group(List<TargetTimingListener.TargetTime> targets, long loadMillis, String transtype) {
        List<BuildStep> steps = new ArrayList<>();
        if (loadMillis > 0) {
            steps.add(new BuildStep(LOADING, loadMillis));
        }
        long initialization = 0;
        List<BuildStep> preprocessing = new ArrayList<>();
        long preprocessingMillis = 0;
        List<BuildStep> transforming = new ArrayList<>();
        long transformingMillis = 0;
        boolean preprocessed = false;
        List<BuildStep> dogsbay = new ArrayList<>();

        for (TargetTimingListener.TargetTime t : targets) {
            String step = DOGSBAY.get(t.name());
            if (step != null) {
                dogsbay.add(new BuildStep(step, t.millis()));
            } else if (!preprocessed && isPreprocessing(t.name())) {
                preprocessing.add(new BuildStep(t.name(), t.millis()));
                preprocessingMillis += t.millis();
                preprocessed = PREPROCESS_END.contains(t.name());
            } else if (!preprocessed && preprocessing.isEmpty()) {
                initialization += t.millis();
            } else if (!preprocessed) {
                // A target inside preprocessing that the name rules do not know (a plugin's)
                preprocessing.add(new BuildStep(t.name(), t.millis()));
                preprocessingMillis += t.millis();
            } else if (t.name().startsWith("dita2dogsbay")) {
                // The transtype's own aggregate target: its parts are timed on their own.
                // dogsbay.site used to be one too, when it was four sub-targets; it is now
                // the step that does the work, so DOGSBAY above claims it first.
                continue;
            } else {
                transforming.add(new BuildStep(t.name(), t.millis()));
                transformingMillis += t.millis();
            }
        }
        steps.add(new BuildStep(INITIALIZATION, initialization));
        if (!preprocessing.isEmpty()) {
            steps.add(new BuildStep(PREPROCESSING, preprocessingMillis, preprocessing));
        }
        steps.addAll(dogsbay);
        if (!transforming.isEmpty()) {
            steps.add(new BuildStep("Transforming (" + (transtype == null ? "output" : transtype) + ")",
                    transformingMillis, transforming));
        }
        return steps;
    }

    /** The total time of the steps. */
    public static long total(List<BuildStep> steps) {
        return steps.stream().mapToLong(BuildStep::millis).sum();
    }
}
