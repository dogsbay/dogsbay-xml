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
 * The resolved <em>DITA project</em> — the shared build/validate context every
 * project-wide operation needs: catalog(s), and the set of named
 * {@link Deliverable}s (each a root map · DITAVAL · transtype).
 *
 * <p>This is <em>not</em> the editor "workspace" (which folder is open, plus
 * editor prefs — that's {@code project.ProjectProperties}). A workspace
 * <em>contains</em> 0..N DITA projects, discovered from a
 * {@code project.&#123;xml,json,yaml&#125;} file or synthesized from the
 * workspace's default root map. See {@link ProjectContextLoader}.
 *
 * <p>Batch operations run against a chosen deliverable (or the
 * {@link #defaultDeliverable()}); preview, publish, and validate-all all read
 * from one of these so they agree on scope, catalog, and conditions.
 *
 * @param projectRoot   the workspace folder; never null
 * @param projectFile   the {@code project.*} file this came from, or null if synthesized
 * @param sourceFormat  {@code "xml"}, {@code "json"}, {@code "yaml"}, or {@code "synthesized"}
 * @param catalogs      OASIS catalogs for entity/DTD resolution (may be empty)
 * @param deliverables  the named deliverables (may be empty for a non-DITA workspace)
 * @param ditaOtPath    resolved DITA-OT / engine path, or null if none
 */
public record ProjectContext(
    Path projectRoot,
    Path projectFile,
    String sourceFormat,
    List<Path> catalogs,
    List<Deliverable> deliverables,
    Path ditaOtPath,
    List<Path> projectFiles
) {
    public ProjectContext {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot is required");
        }
        catalogs = catalogs == null ? List.of() : List.copyOf(catalogs);
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        projectFiles = projectFiles == null
                ? (projectFile == null ? List.of() : List.of(projectFile))
                : List.copyOf(projectFiles);
    }

    /**
     * Convenience constructor for a single-file (or synthesized) context;
     * {@code projectFiles} defaults to the single {@code projectFile} (or empty).
     */
    public ProjectContext(Path projectRoot, Path projectFile, String sourceFormat,
            List<Path> catalogs, List<Deliverable> deliverables, Path ditaOtPath) {
        this(projectRoot, projectFile, sourceFormat, catalogs, deliverables, ditaOtPath, null);
    }

    /** True when no deliverable is defined (e.g. a non-DITA workspace). */
    public boolean isEmpty() {
        return deliverables.isEmpty();
    }

    /** The default deliverable (the first), or null when {@link #isEmpty()}. */
    public Deliverable defaultDeliverable() {
        return deliverables.isEmpty() ? null : deliverables.get(0);
    }

    /** The default deliverable's root map, or null when {@link #isEmpty()}. */
    public Path rootMap() {
        Deliverable d = defaultDeliverable();
        return d == null ? null : d.map();
    }

    /**
     * The deliverable uniquely identified by its source file and name — the
     * reliable lookup, since deliverable names collide across project files (e.g.
     * both {@code rosa-html5.json} and {@code enterprise-html5.json} name theirs
     * {@code "HTML5"}). A null {@code sourceFile} falls back to name-only.
     */
    public Deliverable deliverable(Path sourceFile, String name) {
        if (name == null) {
            return null;
        }
        Path want = sourceFile == null ? null : sourceFile.toAbsolutePath().normalize();
        for (Deliverable d : deliverables) {
            if (!name.equals(d.name())) {
                continue;
            }
            if (want == null || (d.sourceFile() != null
                    && want.equals(d.sourceFile().toAbsolutePath().normalize()))) {
                return d;
            }
        }
        return null;
    }

    /** The deliverable with the given name (first match), or null if not found. */
    public Deliverable deliverable(String name) {
        if (name == null) {
            return null;
        }
        for (Deliverable d : deliverables) {
            if (name.equals(d.name())) {
                return d;
            }
        }
        return null;
    }

    /** True when this context was synthesized rather than read from a file. */
    public boolean isSynthesized() {
        return "synthesized".equals(sourceFormat);
    }
}
