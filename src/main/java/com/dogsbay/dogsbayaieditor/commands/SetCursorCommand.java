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

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Move the caret in the active editor.
 *
 * <p>Covers both legacy scripting calls: `exchanger.setCursorPosition` when
 * {@code relative} is false, and `exchanger.moveCursorPosition` when it is true.
 * Out-of-range positions are clamped to the document rather than rejected.
 *
 * @param position absolute offset, or a signed delta when {@code relative}
 * @param relative whether {@code position} is a delta from the current caret
 */
public record SetCursorCommand(
    int position,
    boolean relative
) implements Command<CommandResult>, ReadOnlyCommand {}
