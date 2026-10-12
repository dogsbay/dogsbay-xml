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

package com.dogsbay.dogsbayaieditor.commands;

import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;

/**
 * Is this project ready? Project health, then the build, then the built output — each stage only when the one before it
 * passed.
 *
 * <p>The one command to run before publishing, and the one to run in CI. {@code project-health} answers whether the
 * source is sound; this also answers whether the thing the source produces is sound, which is a different question and
 * the one a reader is really asking. A topic filtered out of a deliverable, a chunk that moved content to another page,
 * an image never carried across: all of them pass every source-level check and all of them reach the reader.
 *
 * <p>Stopping at the first failing stage is deliberate. A project with a broken reference builds output with a broken
 * link in the same place, so a run that pressed on would report one fault twice under two headings; and a build over an
 * invalid project fails for reasons that are consequences of the invalidity, burying the one that matters.
 *
 * @param root          the project root to check
 * @param rootMap       optional root map — as {@link ProjectHealthCommand}, {@code none} for no map
 * @param schematron    optional house rules (.sch) — as {@link ProjectHealthCommand}, {@code none} to skip
 * @param deliverable   a single deliverable to build and check, or null/blank for all of them
 * @param outputBaseDir base output directory for the build, or null for each deliverable's own
 * @param ditaOtHome    DITA-OT home override (the editor injects its path), or null
 * @param build         false to stop after project health, which makes this a superset of {@code project-health}
 *                      that costs the same when you are not ready to build yet
 */
public record CheckProjectCommand(
    String root,
    String rootMap,
    String schematron,
    String deliverable,
    String outputBaseDir,
    String ditaOtHome,
    boolean build
) implements Command<ProjectCheck> {

    /** Every stage, over every deliverable, with the project's own root map and house rules. */
    public CheckProjectCommand(String root) {
        this(root, null, null, null, null, null, true);
    }
}
