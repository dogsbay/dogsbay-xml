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

import java.util.List;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext;

/**
 * Information about a project (the editor <em>workspace</em>), enriched with the
 * resolved <em>DITA project</em> it contains: catalogs and named deliverables.
 *
 * <p>The DITA fields let the agent discover the root map(s) and conditions
 * declaratively (via {@code get_project}) instead of hunting the filesystem.
 * They are empty for non-DITA workspaces or lightweight listings.
 *
 * @param name           workspace name
 * @param folderPath     workspace folder
 * @param projectType    workspace type
 * @param defaultRootMap the workspace's default root map (editor setting)
 * @param active         whether this is the active workspace
 * @param projectFile    the discovered {@code project.*} file, or null if synthesized/none
 * @param catalogs       catalogs used for validation (e.g. the bundled DITA catalog)
 * @param deliverables   the DITA project's deliverables (map · ditaval · transtype)
 */
public record ProjectInfo(
    String name,
    String folderPath,
    String projectType,
    String defaultRootMap,
    boolean active,
    String projectFile,
    List<String> catalogs,
    List<DeliverableInfo> deliverables
) {
    public ProjectInfo {
        catalogs = catalogs == null ? List.of() : List.copyOf(catalogs);
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
    }

    /** Lightweight workspace info with no resolved DITA project. */
    public ProjectInfo(String name, String folderPath, String projectType,
            String defaultRootMap, boolean active) {
        this(name, folderPath, projectType, defaultRootMap, active, null, List.of(), List.of());
    }

    /**
     * Workspace info enriched with a resolved DITA {@link ProjectContext}
     * (its project file, catalogs, and deliverables).
     */
    public static ProjectInfo of(String name, String folderPath, String projectType,
            String defaultRootMap, boolean active, ProjectContext ctx) {
        if (ctx == null) {
            return new ProjectInfo(name, folderPath, projectType, defaultRootMap, active);
        }
        List<String> catalogs = ctx.catalogs().stream().map(p -> p.toString()).toList();
        List<DeliverableInfo> deliverables = ctx.deliverables().stream()
                .map(ProjectInfo::toDeliverableInfo)
                .toList();
        String projectFile = ctx.projectFile() != null ? ctx.projectFile().toString() : null;
        return new ProjectInfo(name, folderPath, projectType, defaultRootMap, active,
                projectFile, catalogs, deliverables);
    }

    private static DeliverableInfo toDeliverableInfo(Deliverable d) {
        return new DeliverableInfo(
                d.name(),
                d.map().toString(),
                d.ditaval() != null ? d.ditaval().toString() : null,
                d.transtype());
    }
}
