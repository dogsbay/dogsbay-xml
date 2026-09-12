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
 * One Schematron rule match (a failed {@code <assert>} or a fired
 * {@code <report>}) found by a batch Schematron run, carried as a finding in a
 * {@code BatchResult}.
 *
 * @param file     the file the assertion fired in (absolute path)
 * @param location the XPath location of the offending node (SVRL location), or null
 * @param message  the human-readable assertion text
 * @param role     the assertion role (e.g. "error"/"warning"), or null
 * @param test     the failed XPath test expression, or null
 * @param line     1-based start line of the offending element, or -1 if the SVRL
 *                 location couldn't be resolved to a source line
 */
public record SchematronFinding(
    String file,
    String location,
    String message,
    String role,
    String test,
    int line
) {}
