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

/**
 * Compile the project's required-metadata policy to ISO Schematron, for portable
 * validation in any Schematron pipeline (CI, DITA-OT, oXygen). Returns the
 * Schematron text; writes it to {@code output} when given.
 *
 * <p>The policy is resolved like {@code metadata_audit}: an explicit {@code policy}
 * file → the shared {@code .dogsbay/config.xml} → empty.
 *
 * @param root   the project root (to locate {@code .dogsbay/config.xml})
 * @param policy an explicit policy file (overrides the project config), or null
 * @param output a file to write the {@code .sch} to, or null to only return it
 */
public record ExportMetadataSchematronCommand(
    String root,
    String policy,
    String output
) implements Command<String> {}
