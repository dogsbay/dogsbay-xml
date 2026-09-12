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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * One DITA 1.3 branch-filter variant of a map (from a {@code <ditavalref>}).
 *
 * @param label            a display name (resource prefix / DITAVAL name)
 * @param appliesTo        the topicref/topicgroup the branch filters
 * @param ditaval          the branch DITAVAL href, or null
 * @param resolvedDitaval  the resolved DITAVAL path, or null
 * @param generatedKeyscope the keyscope DITA-OT generates for this branch, or ""
 */
public record BranchInfo(String label, String appliesTo, String ditaval,
        String resolvedDitaval, String generatedKeyscope) {}
