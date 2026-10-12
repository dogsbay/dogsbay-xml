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
 * Run a menu command by its id, the way choosing it from the menu does.
 *
 * <p>For anything driving the editor from outside: a recording, a test, a script. The alternative was opening menus by
 * clicking at remembered screen positions, which breaks whenever a menu gains an item or a popup closes early, and which
 * cost a pilot recording most of its effort.
 *
 * <p>An id is what the menus already call the command, such as {@code "XML:  Validate"}. It is not a display label and not
 * a keyboard shortcut; an unknown one is refused with the list of ids that do exist.
 */
public record InvokeActionCommand(String id) implements Command<CommandResult> {
}
