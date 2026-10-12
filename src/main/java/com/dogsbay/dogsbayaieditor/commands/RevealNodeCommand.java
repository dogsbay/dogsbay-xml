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

import java.util.Map;

import com.dogsbay.dogsbayaieditor.commands.results.RangeHit;

/**
 * Scroll a part of a node into view, and say where it ended up.
 *
 * <p>{@link LocateNodeCommand}'s other half, and the pair work the way {@code locate} and
 * {@code reveal} do: ask where something is, and when the answer is that it is not on screen, bring
 * it there and ask again. It takes the same {@code select}, {@code part}, {@code nth} and
 * {@code namespaces}, and answers with the same {@link RangeHit}.
 *
 * <p>What it adds is {@code align}. {@code scrollRectToVisible} scrolls as little as it can, which
 * leaves a long element clinging to the bottom edge with its start tag above the view — fine for
 * pointing at something, useless for a recording about to discuss it. {@code top} puts its first
 * line just below the top edge with what follows underneath; {@code center} puts it in the middle;
 * {@code nearest} is the old behaviour and the default.
 *
 * <p>It scrolls and nothing else: no caret moved, no selection changed, no document touched. That
 * is why it needs no {@code --allow-driving}, on the same grounds as {@code reveal}.
 *
 * @param select     an XPath expression for the node, in the open document
 * @param part       which part of it to bring into view; the element itself when not given
 * @param nth        which match, counting from 1; 0 means there should be only one
 * @param namespaces prefix to URI, for an expression whose prefix the document does not declare
 * @param align      {@code top}, {@code center} or {@code nearest}; nearest when not given
 */
public record RevealNodeCommand(String select, String part, int nth,
        Map<String, String> namespaces, String align)
        implements Command<RangeHit>, ReadOnlyCommand {
}
