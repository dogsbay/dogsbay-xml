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

import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Run a Schematron schema against a single document — the business / style rules
 * DTD, XSD and RelaxNG cannot express, applied to the file you are working on.
 *
 * <p>The document-scoped peer of {@link SchematronProjectCommand}, sharing its
 * engine (ph-schematron) and its {@link SchematronFinding} results, so a rule
 * reports identically whether it is run over one file or a whole project.
 *
 * @param file   the document to check
 * @param schema the {@code .sch} Schematron schema to apply
 */
public record SchematronCommand(
    String file,
    String schema
) implements Command<BatchResult<SchematronFinding>>, ReadOnlyCommand {}
