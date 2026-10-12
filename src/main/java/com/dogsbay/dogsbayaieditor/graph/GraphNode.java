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
 * A node of a {@link ProjectGraph}.
 *
 * @param id      project-relative path with forward slashes, or {@code key:<name>}
 * @param kind    {@code map|topic|key|ditaval}
 * @param type    root element local name (maps/topics), {@code "ditaval"}, or null
 * @param title   first title, or null
 * @param missing true when the referenced file does not exist, else null
 * @param ships   names of the deliverables that publish this file, or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphNode(String id, String kind, String type, String title,
                        Boolean missing, List<String> ships) {
}
