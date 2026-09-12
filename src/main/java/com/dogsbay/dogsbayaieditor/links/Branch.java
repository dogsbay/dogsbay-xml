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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;

/**
 * One DITA 1.3 branch-filter variant: a {@code <ditavalref>} placed inside a
 * topicref/topicgroup tells DITA-OT to duplicate that subtree and filter the copy
 * with the referenced DITAVAL. Sibling {@code <ditavalref>}s under one parent fan
 * out to several branches. {@code <ditavalmeta>} renames the generated copy's
 * resources/keys ({@code dvrResource*}/{@code dvrKeyscope*}).
 *
 * <p>This is the in-process <em>inventory</em> of variants (count, each one's DITAVAL
 * + generated keyscope) — the actual subtree duplication / renaming is DITA-OT's job.
 *
 * @param appliesTo      the topicref/topicgroup the branch filters (href or navtitle)
 * @param ditavalHref    the branch DITAVAL href as written, or null
 * @param ditaval        the resolved DITAVAL file (relative to the map), or null
 * @param keyscopePrefix {@code <dvrKeyscopePrefix>}, or ""
 * @param keyscopeSuffix {@code <dvrKeyscopeSuffix>}, or ""
 * @param resourcePrefix {@code <dvrResourcePrefix>}, or ""
 * @param resourceSuffix {@code <dvrResourceSuffix>}, or ""
 */
public record Branch(String appliesTo, String ditavalHref, File ditaval,
        String keyscopePrefix, String keyscopeSuffix,
        String resourcePrefix, String resourceSuffix) {

    public Branch {
        keyscopePrefix = keyscopePrefix == null ? "" : keyscopePrefix;
        keyscopeSuffix = keyscopeSuffix == null ? "" : keyscopeSuffix;
        resourcePrefix = resourcePrefix == null ? "" : resourcePrefix;
        resourceSuffix = resourceSuffix == null ? "" : resourceSuffix;
    }

    /** The keyscope DITA-OT generates for this branch (prefix+suffix), or "" if none.
     *  Best-effort: the engine combines these with the branch's own @keyscope. */
    public String generatedKeyscope() {
        return keyscopePrefix + keyscopeSuffix;
    }

    /** A display label: the resource prefix, else the DITAVAL name, else the href. */
    public String label() {
        if (!resourcePrefix.isBlank()) {
            return resourcePrefix.trim();
        }
        if (ditaval != null) {
            return ditaval.getName();
        }
        return ditavalHref != null ? ditavalHref : "(branch)";
    }
}
