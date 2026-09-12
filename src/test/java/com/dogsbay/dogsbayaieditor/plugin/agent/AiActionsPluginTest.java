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

package com.dogsbay.dogsbayaieditor.plugin.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.plugin.Plugin;

class AiActionsPluginTest {

    @Test
    void exposesStableMetadata() {
        Plugin plugin = new AiActionsPlugin();
        assertThat(plugin.getId()).isEqualTo("ai-actions");
        assertThat(plugin.getName()).isEqualTo("AI Actions");
        assertThat(plugin.getDescription()).isNotBlank();
        assertThat(plugin.isBuiltIn()).isTrue();
    }
}
