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

/**
 * Plugin metadata smoke test. Activation is display-driven (docks a sidebar
 * panel, builds an agent) and is exercised manually / via {@code AgentApp};
 * here we just verify the plugin's identity and that it is discoverable.
 */
class AgentPluginTest {

    @Test
    void exposesStableMetadata() {
        Plugin plugin = new AgentPlugin();
        assertThat(plugin.getId()).isEqualTo("agent");
        assertThat(plugin.getName()).isEqualTo("AI Agent");
        assertThat(plugin.getDescription()).isNotBlank();
        assertThat(plugin.isBuiltIn()).isTrue();
    }
}
