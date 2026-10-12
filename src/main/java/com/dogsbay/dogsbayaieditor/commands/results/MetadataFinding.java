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
 * One required-metadata policy violation found by {@code metadata_audit}: a topic
 * is missing a required field, carries a forbidden one, or holds a value the policy
 * doesn't allow. Carried as a finding in a {@code BatchResult}.
 *
 * @param file     the file with the violation (absolute path)
 * @param line     1-based source line, or -1 (file-level — e.g. a missing field)
 * @param field    the metadata field (DITA element name, e.g. {@code created})
 * @param message  a human-readable description
 * @param severity {@code "error"} (required/forbidden/value) or {@code "warning"}
 *                 (recommended)
 */
public record MetadataFinding(
    String file,
    int line,
    String field,
    String message,
    String severity
) {}
