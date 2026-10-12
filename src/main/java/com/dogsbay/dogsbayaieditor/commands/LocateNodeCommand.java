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
 * Where a part of a node is on screen, found by XPath.
 *
 * <p>{@link LocateRangeCommand} boxes a stretch a caller already knows the offsets of.
 * This one finds the offsets: it asks the document's own tree, which has carried the source
 * position of every element and attribute since long before anything asked for it, so naming
 * {@code //tgroup[1]/@cols} is enough to box that attribute.
 *
 * <p>{@code part} is what to box — the element, one of its tags, its content, a name, an attribute
 * or its value. Finding the node is XPath's job and choosing the part is this command's, which is
 * why the two are separate parameters rather than a cleverer expression.
 *
 * <p>It changes nothing, so it needs no {@code --allow-driving}. It does ask the editor to bring its
 * model up to date with the buffer first, since the positions are only as current as that, and
 * refuses when the text is not well-formed — a tree built from text that is mid-edit would point at
 * the wrong characters with every appearance of being right.
 *
 * @param select     an XPath expression for the node, in the open document
 * @param part       which part of it to box; the element itself when not given
 * @param nth        which match, counting from 1, when the expression finds several; 0 means there
 *                   should be only one
 * @param namespaces prefix to URI, for an expression that uses a prefix
 */
public record LocateNodeCommand(String select, String part, int nth, Map<String, String> namespaces)
        implements Command<RangeHit>, ReadOnlyCommand {
}
