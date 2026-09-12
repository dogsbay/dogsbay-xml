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

package com.dogsbay.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.app.StandaloneAgentHost;
import com.xagent.core.Agent;
import com.xagent.provider.ProviderConfig;

/**
 * Verifies lazy/responsive agent construction: building an agent must NOT
 * require credentials (so the UI comes up and the user can pick a provider /
 * log in / use Ollama), and {@code authHint} guides when a key is missing.
 */
class ChatAgentFactoryTest {

    @Test
    void editorSteeringOnlyWhenEditorToolsPresent() {
        assertThat(ChatAgentFactory.editorSteering(false)).isEmpty();
        String s = ChatAgentFactory.editorSteering(true);
        assertThat(s)
            .contains("project_health")
            .contains("validate_project")
            .contains("Definition of done");
    }

    @Test
    void buildsAgentWithoutCredentials(@TempDir Path tmp) {
        // no provider/key needed — the model is resolved lazily on first prompt
        Agent agent = ChatAgentFactory.create(new StandaloneAgentHost(tmp));

        assertThat(agent).isNotNull();
        // default tool set (read/write/edit/bash/grep/find/ls + xml/dita) registered
        assertThat(agent.toolRegistry().size()).isGreaterThan(5);
    }

    @Test
    void loadsBundledDitaSkills(@TempDir Path tmp) {
        // the DITA skills shipped in xagent-core's /skills resources are loaded by default
        var skills = ChatAgentFactory.loadSkills(new StandaloneAgentHost(tmp));
        assertThat(skills).extracting(com.xagent.skill.Skill::name)
                .contains("dita-fix-validation", "dita-conref-audit", "dita-metadata-normalize");
    }

    @Test
    void classifiesKeyBasedProviders() {
        assertThat(ChatAgentFactory.isKeyBased("anthropic")).isTrue();
        assertThat(ChatAgentFactory.isKeyBased("openai")).isTrue();
        assertThat(ChatAgentFactory.isKeyBased("gemini")).isTrue();
        assertThat(ChatAgentFactory.isKeyBased("ollama")).isFalse();        // local, no key
        assertThat(ChatAgentFactory.isKeyBased("openai-codex")).isFalse();  // OAuth, no key
    }

    @Test
    void ollamaNeedsNoKeyHint() {
        // Ollama runs locally — never a "missing key" hint, regardless of env
        assertThat(ChatAgentFactory.authHint(ProviderConfig.resolve("ollama", null, null, null)))
                .isNull();
    }

    @Test
    void keyBasedProviderHintReflectsCredentialState() {
        ProviderConfig openai = ProviderConfig.resolve("openai", null, null, null);
        if (openai.apiKey() == null) {
            assertThat(ChatAgentFactory.authHint(openai)).contains("No API key");
        } else {
            // a key is present in this environment → ready, no hint
            assertThat(ChatAgentFactory.authHint(openai)).isNull();
        }
    }
}
