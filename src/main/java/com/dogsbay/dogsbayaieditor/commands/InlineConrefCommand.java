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
 * Inline-conref (inverse of extract-conref): every {@code conref}
 * resolving to {@code target#…/elementId} is replaced by a copy of the
 * element's source text. Copies drop the target's id (a local id
 * survives) and relative paths inside them are rebased per destination.
 * The target element itself stays. Dry-run by default.
 *
 * @param target    the file holding the reused element
 * @param elementId the reused element's id
 * @param root      the project root scanned for conref instances
 * @param file      optional: only inline instances in this file
 * @param apply     true to execute; false returns the plan only
 */
public record InlineConrefCommand(
    String target,
    String elementId,
    String root,
    String file,
    boolean apply
) implements Command<RefactorResult> {}
