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

package com.dogsbay.dogsbayaieditor.ditaproject;

import java.util.List;

/**
 * The editable fields of a deliverable, as they should be written into a DITA-OT
 * project file. Paths are <strong>project-file-relative strings</strong> (the form
 * stored in the file) — the caller converts from the absolute {@link Deliverable}
 * paths. Used by {@link DitaProjectWriter} to add or update a deliverable while
 * preserving every other field in the file.
 *
 * @param name      the deliverable name (the upsert key); never null/blank
 * @param input     the context input map, project-file-relative; never null/blank
 * @param ditavals  context profile DITAVALs, project-file-relative (may be empty)
 * @param transtype the publication transtype, or null to leave unset
 * @param output    the output dir, or null to clear
 * @param params    publication params (may be empty)
 */
public record DeliverableEdit(
    String name,
    String input,
    List<String> ditavals,
    String transtype,
    String output,
    List<Param> params
) {
    public DeliverableEdit {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("deliverable name is required");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("deliverable input map is required");
        }
        ditavals = ditavals == null ? List.of() : List.copyOf(ditavals);
        params = params == null ? List.of() : List.copyOf(params);
    }
}
