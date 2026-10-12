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
 * Bring one of the panels along the bottom to the front: {@code errors}, {@code terminal} or {@code projectValidation}.
 *
 * <p>Selecting a tab was possible in the editor's own code and nowhere else, so anything driving it from outside had to
 * click at a remembered position. The panels are shown first if they were collapsed, since selecting a tab behind a hidden
 * panel changes what is selected and puts nothing on screen.
 */
public record SelectPanelCommand(String panel) implements Command<CommandResult>, ReadOnlyCommand {
}
