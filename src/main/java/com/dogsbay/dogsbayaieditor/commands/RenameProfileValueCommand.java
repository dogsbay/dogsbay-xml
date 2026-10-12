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

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * Rename a profiling attribute value project-wide: the token in
 * {@code attribute="..."} lists across every topic and map (only the
 * matching token changes), plus matching
 * {@code <prop att="..." val="...">} rules in {@code .ditaval} files so
 * filters keep working. Dry-run by default.
 *
 * @param attribute the profiling attribute (product, audience, platform,
 *                  otherprops, props, ...)
 * @param oldValue  the token to rename
 * @param newValue  the new token
 * @param root      the project root scanned and rewritten
 * @param apply     true to execute; false returns the plan only
 */
public record RenameProfileValueCommand(
    String attribute,
    String oldValue,
    String newValue,
    String root,
    boolean apply
) implements Command<RefactorResult> {}
