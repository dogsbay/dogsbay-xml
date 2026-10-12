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

import java.nio.file.Files;
import java.nio.file.Path;

import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/**
 * The root map and house rules a project check uses: what the caller named, else what the project's
 * {@code .dogsbay/config.xml} names ({@code default-root-map}, {@code default-schematron}), else none.
 * {@code none} from the caller means none, whatever the project says. A file the project names that does
 * not exist is {@link #MISSING}: the check reports it as a problem rather than running without it. A map the caller names also scopes
 * the check to its publication set; the project's default map is used for keys only, so a file outside
 * the publication set is not silently skipped.
 */
public final class ProjectDefaults {

    /** Where a default came from. */
    public static final String ARGUMENT = "argument";
    public static final String CONFIG = "project config";
    public static final String NONE = "none";
    public static final String NOT_CONFIGURED = "none configured";
    /** The project config names a file that does not exist: a problem to report, not "none configured". */
    public static final String MISSING = "project config, file missing";

    /**
     * @param map              absolute root map, or null
     * @param mapSource        where it came from
     * @param mapScopes        whether the map scopes the check (the caller named it)
     * @param schematron       absolute Schematron schema, or null
     * @param schematronSource where it came from
     */
    public record Resolved(String map, String mapSource, boolean mapScopes, String schematron,
            String schematronSource) {
    }

    private ProjectDefaults() {
    }

    public static Resolved resolve(Path root, String mapArg, String schematronArg) {
        DogsbayProjectConfig config = null;
        try {
            config = DogsbayProjectConfig.load(root);
        } catch (RuntimeException e) {
            // an unreadable config names nothing
        }
        String map;
        String mapSource;
        boolean scopes = false;
        if (isNone(mapArg)) {
            map = null;
            mapSource = NONE;
        } else if (mapArg != null && !mapArg.isBlank()) {
            map = against(root, mapArg);
            mapSource = ARGUMENT;
            scopes = true;
        } else if (config != null && existing(root, config.getDefaultRootMap()) != null) {
            map = existing(root, config.getDefaultRootMap());
            mapSource = CONFIG;
        } else if (config != null && config.getDefaultRootMap() != null && !config.getDefaultRootMap().isBlank()) {
            map = against(root, config.getDefaultRootMap());
            mapSource = MISSING;
        } else {
            map = null;
            mapSource = NOT_CONFIGURED;
        }
        String sch;
        String schSource;
        if (isNone(schematronArg)) {
            sch = null;
            schSource = NONE;
        } else if (schematronArg != null && !schematronArg.isBlank()) {
            sch = against(root, schematronArg);
            schSource = ARGUMENT;
        } else if (config != null && existing(root, config.getDefaultSchematron()) != null) {
            sch = existing(root, config.getDefaultSchematron());
            schSource = CONFIG;
        } else if (config != null && config.getDefaultSchematron() != null && !config.getDefaultSchematron().isBlank()) {
            sch = against(root, config.getDefaultSchematron());
            schSource = MISSING;
        } else {
            sch = null;
            schSource = NOT_CONFIGURED;
        }
        return new Resolved(map, mapSource, scopes, sch, schSource);
    }

    private static boolean isNone(String arg) {
        return arg != null && arg.trim().equalsIgnoreCase(NONE);
    }

    private static String against(Path root, String path) {
        Path p = Path.of(path);
        return (p.isAbsolute() ? p : root.resolve(p)).normalize().toString();
    }

    private static String existing(Path root, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String resolved = against(root, path);
        return Files.isRegularFile(Path.of(resolved)) ? resolved : null;
    }
}
