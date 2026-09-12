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
 * The outcome of an {@code edit_map} operation.
 *
 * @param success  whether the edit succeeded
 * @param files    the files written (or, in dry-run, the files that would change)
 * @param warnings reference-safety warnings (e.g. inbound references to a removed topic)
 * @param dryRun   true when nothing was written (preview only)
 */
public record MapEditResult(boolean success, List<String> files,
        List<String> warnings, boolean dryRun) {

    public MapEditResult {
        files = files == null ? List.of() : List.copyOf(files);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
