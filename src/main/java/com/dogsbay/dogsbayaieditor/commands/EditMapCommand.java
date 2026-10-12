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

import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;

/**
 * Structurally edit a DITA map, reference-safely and formatting-preservingly.
 *
 * <p>Topicrefs are addressed by a {@code selector}: an element {@code @id}, or a
 * 1-based child-path like {@code /1/3} ({@code /} is the map root). The four ops:
 * <ul>
 *   <li>{@code set-attr} — set ({@code value} given) or remove ({@code value} empty)
 *       an attribute {@code name} on {@code ref}.</li>
 *   <li>{@code insert} — add a {@code type} element ({@code topicref}/{@code
 *       topichead}/{@code mapref}, default topicref) under {@code parent} at
 *       {@code index} (omit/large ⇒ append), with optional {@code href}/{@code
 *       navtitle}.</li>
 *   <li>{@code remove} — delete {@code ref} and its subtree (warns on inbound
 *       references).</li>
 *   <li>{@code move} — reparent/reorder {@code ref} under {@code parent} at
 *       {@code index}; {@code toMap} moves it into another map (rebasing hrefs).</li>
 * </ul>
 *
 * @param map      path to the {@code .ditamap}
 * @param op       {@code set-attr} | {@code insert} | {@code remove} | {@code move}
 * @param ref      target selector (set-attr / remove / move)
 * @param parent   parent selector (insert / move); null/{@code /} ⇒ map root
 * @param index    child index for insert / move (null ⇒ append)
 * @param type     element type for insert (default {@code topicref})
 * @param name     attribute name for set-attr
 * @param value    attribute value for set-attr (empty ⇒ remove the attribute)
 * @param href     {@code @href} for insert
 * @param navtitle {@code @navtitle} for insert
 * @param toMap    destination map for a cross-map move, or null
 * @param dryRun   compute the plan (files + warnings) without writing
 */
public record EditMapCommand(
    String map,
    String op,
    String ref,
    String parent,
    Integer index,
    String type,
    String name,
    String value,
    String href,
    String navtitle,
    String toMap,
    boolean dryRun
) implements Command<MapEditResult> {}
