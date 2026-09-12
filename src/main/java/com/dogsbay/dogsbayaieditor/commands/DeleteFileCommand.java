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
 * Safe delete: report every inbound reference before deleting. Map
 * references (topicref/keydef/...) can be removed automatically with
 * {@code removeRefs}; content reuse (conref), links, and images are only
 * warned about — they're left for the health report, never auto-edited.
 * Dry-run by default: the result is the plan; nothing changes unless
 * {@code apply} is true.
 *
 * @param file       the file to delete
 * @param root       the project root scanned for inbound references
 * @param removeRefs true to also remove referencing map elements
 * @param apply      true to execute; false returns the plan only
 */
public record DeleteFileCommand(
    String file,
    String root,
    boolean removeRefs,
    boolean apply
) implements Command<RefactorResult> {}
