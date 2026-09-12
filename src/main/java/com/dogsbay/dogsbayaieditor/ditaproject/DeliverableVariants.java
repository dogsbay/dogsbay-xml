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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.links.Branch;
import com.dogsbay.dogsbayaieditor.links.BranchModel;

/**
 * Projects a {@link Deliverable} into its DITA 1.3 branch-filter variants: each
 * {@code <ditavalref>} in the deliverable's map becomes a <em>derived</em>
 * deliverable whose effective DITAVAL set is the parent's plus the branch's, named
 * {@code <parent> · <branch label>}. Derived variants are computed from the map, not
 * written to project files — they reuse the deliverable model so per-variant
 * validate/build and UI surfacing have a real {@link Deliverable} to work with.
 *
 * <p>The actual subtree duplication / resource renaming remains DITA-OT's job; this
 * is the in-process variant inventory (decisions 2–3 in
 * {@code plans/branch-filtering-key-scopes.md}).
 */
public final class DeliverableVariants {

    private DeliverableVariants() {}

    /** The branch variants of {@code d}, or an empty list when its map has none. */
    public static List<Deliverable> of(Deliverable d) {
        if (d == null || d.map() == null) {
            return List.of();
        }
        List<Branch> branches;
        try {
            branches = BranchModel.enumerate(d.map().toFile());
        } catch (IOException e) {
            return List.of();
        }
        List<Deliverable> out = new ArrayList<>();
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        for (Branch b : branches) {
            List<Path> ditavals = new ArrayList<>(d.ditavals());
            if (b.ditaval() != null) {
                ditavals.add(b.ditaval().toPath());     // parent DITAVALs + the branch's
            }
            // Two branches can share a label (e.g. no dvrResourcePrefix) — keep names
            // unique so a name-keyed map (UI/build dispatch) can't drop a variant.
            String name = d.name() + " · " + b.label();
            for (int i = 2; !usedNames.add(name); i++) {
                name = d.name() + " · " + b.label() + " (" + i + ")";
            }
            out.add(new Deliverable(name, d.map(), ditavals,
                    d.transtype(), d.output(), d.params(), d.sourceFile(), d.contextId()));
        }
        return out;
    }
}
