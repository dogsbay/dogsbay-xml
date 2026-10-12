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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A typed edge of a {@link ProjectGraph}.
 *
 * @param from        source node id
 * @param to          target node id
 * @param kind        {@code mapref|topicref|reltable|keydef|keytarget|keyref|conkeyref|conref|link|ditavalref|profile}
 * @param line        start-tag line of the reference, or null
 * @param via         for {@code keytarget}: the map whose definition binds the key
 * @param fragment    the part after {@code #} (paths) or the first {@code /} (keys)
 * @param broken      true when the target file does not exist, else null
 * @param deliverable for {@code profile}: the deliverable name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphEdge(String from, String to, String kind, Integer line, String via,
                        String fragment, Boolean broken, String deliverable) {
}
