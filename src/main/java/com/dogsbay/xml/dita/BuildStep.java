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

import java.util.List;

/**
 * A timed step of a DITA-OT build (such as "Preprocessing"), with the stages it is
 * made of (for preprocessing, each Ant target: {@code map-keyref}, {@code topic-conref}).
 *
 * @param name   the step name, the same for every engine ("Preprocessing")
 * @param millis how long the step took
 * @param stages the step's parts, in the order they ran (empty when it has none)
 */
public record BuildStep(String name, long millis, List<BuildStep> stages) {

    public BuildStep {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** A step without stages. */
    public BuildStep(String name, long millis) {
        this(name, millis, List.of());
    }
}
