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
 * Create a text keydef ("extract variable"): adds
 * {@code <keydef keys="key"><topicmeta><keywords><keyword>text</keyword>
 * ...} to the chosen map. With {@code replaceRoot}, every whole-word
 * occurrence of the text in regular text content of {@code .dita} topics
 * under that root is replaced with {@code <ph keyref="key"/>} — never
 * inside attributes, comments, CDATA, or code-like elements
 * (codeblock/codeph/filepath/...). Dry-run by default.
 *
 * @param key         the key name to define
 * @param text        the keyword text the key resolves to (plain text)
 * @param map         the map that receives the keydef (often a keys submap)
 * @param rootMap     optional root map for context: collision checks use
 *                    its effective key space, and the plan warns when
 *                    {@code map} isn't included from it
 * @param replaceRoot optional directory: also replace occurrences in
 *                    topics under it; null defines the key only
 * @param apply       true to execute; false returns the plan only
 */
public record CreateKeydefCommand(
    String key,
    String text,
    String map,
    String rootMap,
    String replaceRoot,
    boolean apply
) implements Command<RefactorResult> {}
