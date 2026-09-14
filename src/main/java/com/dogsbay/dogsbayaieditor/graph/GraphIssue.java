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

package com.dogsbay.dogsbayaieditor.graph;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A structural finding in a {@link ProjectGraph}.
 *
 * @param severity {@code error|warning|info}
 * @param rule     rule id, e.g. {@code broken-reference}
 * @param file     project-relative file, or null
 * @param line     line, or null
 * @param message  human-readable description
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphIssue(String severity, String rule, String file, Integer line,
                         String message) {
}
