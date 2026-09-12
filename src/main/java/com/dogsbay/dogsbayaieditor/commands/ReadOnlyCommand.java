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

/**
 * Marker for commands that cannot change a document, a file, or project
 * state. The one declaration of mutability: the built-in agent's approval
 * gate, the MCP {@code readOnlyHint}, the write gate and the audit log all
 * consult it. A command that is not marked is mutating, so a new command is
 * gated and audited until someone marks it on purpose.
 *
 * <p>UI-only commands (moving the caret, switching a sidebar, opening a
 * document) are read-only in this sense: they change what is shown, not what
 * is stored. Opening a <em>project</em> is not: it moves the root that
 * contains external agents. Rendering a preview is a read unless an output
 * path is given, and the class is marked mutating because the marker is per
 * class; {@code CommandTargets} says {@code NONE} for the no-output case.
 */
public interface ReadOnlyCommand {
}
