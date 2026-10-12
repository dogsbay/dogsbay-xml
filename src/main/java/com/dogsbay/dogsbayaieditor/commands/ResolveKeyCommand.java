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

import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;

/**
 * Resolve one key against a root map's key space.
 *
 * @param key     the key name (bare, or scope-qualified {@code scope.key})
 * @param rootMap the root map file
 * @param ditaval optional DITAVAL conditioning the key space
 * @param scope   the key-scope context to resolve from (dotted path), or null/root
 */
public record ResolveKeyCommand(
    String key,
    String rootMap,
    String ditaval,
    String scope
) implements Command<KeyInfo>, ReadOnlyCommand {

    /** Back-compat: resolve from the root scope. */
    public ResolveKeyCommand(String key, String rootMap, String ditaval) {
        this(key, rootMap, ditaval, null);
    }
}
