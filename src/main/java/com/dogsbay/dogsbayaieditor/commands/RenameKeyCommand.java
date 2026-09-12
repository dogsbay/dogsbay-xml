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
 * Rename a key: every definition (keydef {@code @keys} token, all maps
 * under the root) and every {@code keyref}/{@code conkeyref} usage, with
 * element-id segments preserved. Dry-run by default — the result's
 * from/to fields carry the key names (no file move).
 *
 * @param oldKey the key to rename
 * @param newKey the new key name
 * @param root   the project root to scan and rewrite
 * @param apply  true to execute; false returns the plan only
 */
public record RenameKeyCommand(
    String oldKey,
    String newKey,
    String root,
    boolean apply
) implements Command<RefactorResult> {}
