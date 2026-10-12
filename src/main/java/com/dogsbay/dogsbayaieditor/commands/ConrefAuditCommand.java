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

import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

/**
 * Audit reuse references for broken <em>element ids</em>: a conref / conkeyref /
 * keyref / href whose target <em>file</em> exists but whose fragment
 * (e.g. {@code #topic/elementId}) names an id that isn't in that file.
 *
 * <p>This is the gap {@code check_links} misses — it only checks the target file
 * exists. In the demo, {@code conref="...#common-notes/backup-warningX"} pointed
 * at a present file but a missing id, and health still read clean.
 *
 * @param root    the project root to scan
 * @param rootMap optional root map — resolves keyref/conkeyref targets via keys
 */
public record ConrefAuditCommand(
    String root,
    String rootMap
) implements Command<List<BrokenRef>>, ReadOnlyCommand {}
