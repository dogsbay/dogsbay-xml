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
 * Keyify: convert direct references to a file into key references and add
 * a {@code <keydef>} to the chosen map. Converts {@code href} on
 * xref/link/image to {@code keyref} and {@code conref} to
 * {@code conkeyref}; map references (topicref) stay direct. Dry-run by
 * default.
 *
 * @param file    the referenced file to key
 * @param key     the key name to introduce
 * @param map     the map that receives the keydef (often a keys submap)
 * @param root    the project root whose references are converted
 * @param rootMap optional root map for context: collision checks use its
 *                effective key space, and the plan warns when {@code map}
 *                isn't included from it (the key wouldn't resolve)
 * @param apply   true to execute; false returns the plan only
 */
public record KeyifyCommand(
    String file,
    String key,
    String map,
    String root,
    String rootMap,
    boolean apply
) implements Command<RefactorResult> {}
