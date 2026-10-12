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

import java.util.function.Consumer;

/**
 * Live progress of a DITA-OT build, for a UI: a listener bound to the thread that runs
 * the build, so the build commands need no progress parameter. The builder reports the
 * step each Ant target belongs to as it starts.
 */
public final class BuildProgress {

    /** A build's progress: the deliverable being built and the step it is in. */
    public interface Listener {
        void step(String deliverable, String step);
    }

    private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<String> DELIVERABLE = new ThreadLocal<>();

    private BuildProgress() {}

    /** Run {@code work} on this thread with {@code listener} receiving its builds' progress. */
    public static <T> T with(Listener listener, java.util.concurrent.Callable<T> work) throws Exception {
        Listener previous = LISTENER.get();
        LISTENER.set(listener);
        try {
            return work.call();
        } finally {
            if (previous == null) {
                LISTENER.remove();
            } else {
                LISTENER.set(previous);
            }
        }
    }

    /** Name the deliverable the next builds on this thread are for. */
    public static void deliverable(String name) {
        DELIVERABLE.set(name);
    }

    /** A target-started callback that reports its step, or null when nothing listens. */
    static Consumer<String> targetCallback() {
        Listener listener = LISTENER.get();
        if (listener == null) {
            return null;
        }
        String deliverable = DELIVERABLE.get();
        return target -> listener.step(deliverable, stepOf(target));
    }

    /** The step a target belongs to, for a progress line ("Preprocessing (map-keyref)", "Site: install packages"). */
    static String stepOf(String target) {
        String dogsbay = BuildSteps.dogsbayStep(target);
        if (dogsbay != null) {
            return dogsbay;
        }
        return BuildSteps.isPreprocessing(target) ? BuildSteps.PREPROCESSING + " (" + target + ")" : "Running " + target;
    }
}
