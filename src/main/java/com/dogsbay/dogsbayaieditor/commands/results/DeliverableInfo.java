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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * A serializable view of a DITA-project deliverable for {@link ProjectInfo}:
 * a root map, optionally filtered by a DITAVAL, optionally bound to an output
 * type. Paths are strings so the record serializes cleanly to JSON for the CLI
 * and the agent.
 *
 * @param name      deliverable name (e.g. "mac-beginner", or "default" when synthesized)
 * @param map       root map path
 * @param ditaval   conditional filter path, or null
 * @param transtype output type (e.g. "html5"), or null
 */
public record DeliverableInfo(
    String name,
    String map,
    String ditaval,
    String transtype
) {}
