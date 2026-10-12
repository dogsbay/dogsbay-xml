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
 * Merge duplicate keydefs: for every key defined more than once in the
 * root map's closure, the first (effective) definition is kept and the
 * shadowed ones are removed — whole keydefs when all their keys are
 * shadowed, otherwise just the shadowed tokens. Resolution is unchanged
 * by construction (the winners were already winning). Dry-run by default.
 *
 * @param rootMap the root map whose closure is scanned
 * @param key     optional: merge just this key; null merges all duplicates
 * @param apply   true to execute; false returns the plan only
 */
public record MergeKeydefsCommand(
    String rootMap,
    String key,
    boolean apply
) implements Command<RefactorResult> {}
