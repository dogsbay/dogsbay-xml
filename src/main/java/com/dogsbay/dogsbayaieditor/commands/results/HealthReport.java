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

import java.util.List;

/**
 * Project reuse-health report.
 *
 * @param brokenReferences path references whose target file doesn't exist
 * @param undefinedKeys    key references naming keys the root map doesn't define
 *                         (empty when no root map was given)
 * @param unusedKeys       keys defined in the root map that nothing references
 * @param orphanTopics     .dita topics with no inbound references at all
 */
public record HealthReport(
    List<BrokenRef> brokenReferences,
    List<BrokenRef> undefinedKeys,
    List<KeyInfo> unusedKeys,
    List<String> orphanTopics
) {
    public boolean isClean() {
        return brokenReferences.isEmpty() && undefinedKeys.isEmpty()
                && unusedKeys.isEmpty() && orphanTopics.isEmpty();
    }
}
