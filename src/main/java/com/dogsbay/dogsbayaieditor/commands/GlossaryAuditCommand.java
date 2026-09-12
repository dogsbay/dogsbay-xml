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

import com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult;

/**
 * Survey the DITA 1.3 glossary across a project: the {@code <glossentry>} inventory
 * (id + {@code <glossterm>} + surface forms), {@code <abbreviated-form>}/{@code <term>}
 * references whose key doesn't resolve to a glossary entry (undefined), and glossentries
 * that nothing references (unused). The undefined/unused checks need a {@code rootMap}
 * for key resolution; without one the result is the inventory only.
 *
 * @param root    the project root to scan
 * @param scope   {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 * @param rootMap optional root map whose key space resolves {@code keyref}s, or null
 */
public record GlossaryAuditCommand(String root, String scope, String rootMap)
        implements Command<GlossaryAuditResult>, ReadOnlyCommand {}
