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

import com.dogsbay.dogsbayaieditor.graph.ProjectGraph;

/**
 * How a project connects: maps, topics, keys and DITAVALs as nodes, and every
 * reference between them as a typed edge, per deliverable. Every other audit
 * returns findings; this returns structure, for diagramming, impact analysis
 * before a rename, dead-content sweeps and "what does this deliverable ship".
 *
 * <p>Node ids are project-relative. A key's binding carries {@code via}, the map
 * that made it, because one key name is bound differently by different maps.
 *
 * @param root        the project root
 * @param map         optional root map: the graph of that map alone
 * @param deliverable optional deliverable name: the graph of that deliverable alone
 * @param checks      also run DTD validation of the shipped files, the conref element-id
 *                    audit and the conref push audit, and add their findings to the issues
 */
public record ProjectGraphCommand(
    String root,
    String map,
    String deliverable,
    boolean checks
) implements Command<ProjectGraph>, ReadOnlyCommand {}
