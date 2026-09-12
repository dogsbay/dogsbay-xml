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
 * One reltable validation finding.
 *
 * @param source   the map file
 * @param line     1-based line of the offending element (or the reltable), -1 if unknown
 * @param table    a label for the reltable (its title or index)
 * @param cell     row/column locator, e.g. {@code r2c1}
 * @param value    the offending value (href/keyref), if any
 * @param severity {@code error} | {@code warning} | {@code info}
 * @param reason   human-readable explanation
 */
public record ReltableIssue(String source, int line, String table, String cell,
        String value, String severity, String reason) {}
