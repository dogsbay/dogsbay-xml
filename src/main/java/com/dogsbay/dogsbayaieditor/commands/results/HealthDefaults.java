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
 * Which root map and house rules a project_health run used, and where each came from: {@code argument}
 * (the caller named it), {@code project config} (the project's {@code .dogsbay/config.xml}),
 * {@code none} (the caller said none) or {@code none configured} (neither).
 *
 * @param map              the root map used for key analysis, or null
 * @param mapSource        where it came from
 * @param schematron       the house rules (Schematron) that ran, or null
 * @param schematronSource where they came from
 */
public record HealthDefaults(String map, String mapSource, String schematron, String schematronSource) {
}
