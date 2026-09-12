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

import java.nio.file.Path;
import java.util.List;

/**
 * One publishable/validatable unit of a {@link ProjectContext}: a root map,
 * optionally filtered by DITAVAL(s), optionally bound to an output type and
 * publication parameters.
 *
 * <p>This is the user-facing "active selection" (map · profile) surfaced in the
 * status bar, and the scope a batch operation runs against. It maps onto a
 * DITA-OT project {@code <deliverable>}: its {@code <context>} ({@code input}
 * map + {@code <profile>/<ditaval>}), its {@code <output>}, and its
 * {@code <publication>} ({@code transtype} + {@code <param>}s, which may also
 * carry a {@code <profile>/<ditaval>}).
 *
 * @param name       deliverable id/name (e.g. {@code "mac-beginner"}); never null
 * @param map        the input root map, absolute; never null
 * @param ditavals   conditional-processing filters, absolute, in declared order
 *                   (context profile then publication profile); may be empty
 * @param transtype  the output type (e.g. {@code "html5"}), or null if unspecified
 * @param output     the output directory (relative to the CLI {@code --output}), or null
 * @param params     publication parameters, in declared order; may be empty
 * @param sourceFile the project file this deliverable was declared in, or null
 *                   if synthesized (no project file)
 * @param contextId  the {@code <context>} id, or null
 */
public record Deliverable(
    String name,
    Path map,
    List<Path> ditavals,
    String transtype,
    Path output,
    List<Param> params,
    Path sourceFile,
    String contextId
) {
    public Deliverable {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("deliverable name is required");
        }
        if (map == null) {
            throw new IllegalArgumentException("deliverable map is required");
        }
        ditavals = ditavals == null ? List.of() : List.copyOf(ditavals);
        params = params == null ? List.of() : List.copyOf(params);
    }

    /**
     * Backward-compatible constructor for a simple deliverable (single optional
     * ditaval, no output/params/source). Used by the synthesizer and tests.
     */
    public Deliverable(String name, Path map, Path ditaval, String transtype) {
        this(name, map, ditaval == null ? List.of() : List.of(ditaval),
                transtype, null, List.of(), null, null);
    }

    /** The primary (first) DITAVAL, or null when none — the common single-filter case. */
    public Path ditaval() {
        return ditavals.isEmpty() ? null : ditavals.get(0);
    }

    /** True when this deliverable applies at least one conditional filter. */
    public boolean hasDitaval() {
        return !ditavals.isEmpty();
    }
}
