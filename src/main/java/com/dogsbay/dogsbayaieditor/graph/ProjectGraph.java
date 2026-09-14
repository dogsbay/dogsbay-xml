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

package com.dogsbay.dogsbayaieditor.graph;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A project relationship graph: deliverables, file/key nodes, typed edges and
 * structural issues, built by {@link ProjectGraphBuilder}.
 *
 * @param generated    ISO-8601 instant the graph was built
 * @param root         absolute project root
 * @param scope        {@code "all"}, {@code "map:<rel>"} or {@code "deliverable:<name>"}
 * @param deliverables the in-scope deliverables
 * @param nodes        files ({@code map|topic|ditaval}) and keys
 * @param edges        typed relationships between nodes
 * @param issues       structural findings
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectGraph(String generated, String root, String scope,
                           List<GraphDeliverable> deliverables, List<GraphNode> nodes,
                           List<GraphEdge> edges, List<GraphIssue> issues) {
}
