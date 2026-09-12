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

import com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult;

/**
 * Audit DITA 1.3 conref push ({@code @conaction}) across a project: the inventory of
 * push operations (mark / pushbefore / pushafter / pushreplace) and problems — broken
 * sibling pairing (a push with no {@code mark}, a {@code mark} with nothing to push,
 * a {@code pushreplace} with no target) and push targets ({@code @conref}/{@code
 * @conkeyref}) that don't resolve. The static safety net for a construct DITA-OT only
 * resolves at build time. {@code conkeyref} resolution needs a {@code rootMap}.
 *
 * @param root    the project root to scan
 * @param scope   {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 * @param rootMap optional root map whose key space resolves {@code conkeyref}s, or null
 */
public record ConrefPushAuditCommand(String root, String scope, String rootMap)
        implements Command<ConrefPushAuditResult>, ReadOnlyCommand {}
