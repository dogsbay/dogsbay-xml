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

import java.util.List;

/**
 * A refactoring plan, and — when applied — its outcome.
 *
 * @param applied      false for a dry-run (nothing was changed)
 * @param moveFrom     absolute path being renamed/moved
 * @param moveTo       absolute destination path
 * @param edits        every attribute rewrite the refactor performs
 * @param warnings     non-fatal concerns surfaced while planning
 * @param editsApplied rewrites performed (0 for dry-run)
 * @param filesChanged files rewritten (0 for dry-run)
 * @param failures     per-file apply failures (empty for dry-run/success)
 */
public record RefactorResult(
    boolean applied,
    String moveFrom,
    String moveTo,
    List<AttributeEditInfo> edits,
    List<String> warnings,
    int editsApplied,
    int filesChanged,
    List<String> failures
) {
    /** One attribute-value rewrite in one file. */
    public record AttributeEditInfo(
        String file,
        int line,
        String attribute,
        String oldValue,
        String newValue
    ) {}
}
