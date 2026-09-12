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

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * Inline a key: rewrite every {@code keyref}/{@code conkeyref} usage to
 * the direct path the key resolves to (keyref → href, conkeyref →
 * conref). Text-pulling keyrefs (ph/keyword) are warned about, not
 * touched; the keydef stays (health reports it once unused). Dry-run by
 * default.
 *
 * @param key   the key to inline
 * @param map   the context map that resolves the key
 * @param root  the project root whose usages are rewritten
 * @param apply true to execute; false returns the plan only
 */
public record InlineKeyCommand(
    String key,
    String map,
    String root,
    boolean apply
) implements Command<RefactorResult> {}
