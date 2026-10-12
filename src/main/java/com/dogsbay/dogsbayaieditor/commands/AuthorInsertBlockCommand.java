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

import java.nio.file.Path;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Inserts a block (with required descendants auto-created) under a parent
 * block id from the author outline. The result message carries the id of the
 * focused (deepest created text) block.
 */
public record AuthorInsertBlockCommand(
    Path file,          // null = active document
    String type,        // block type name, e.g. "step", "p", "ul"
    String parentId,    // parent block id from author-outline
    Integer index       // position among children; null/-1 = append
) implements Command<CommandResult> {}
