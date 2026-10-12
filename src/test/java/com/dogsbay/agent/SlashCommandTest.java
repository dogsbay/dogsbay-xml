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

package com.dogsbay.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlashCommandTest {

    @Test
    void detectsCommands() {
        assertThat(SlashCommand.isCommand("/help")).isTrue();
        assertThat(SlashCommand.isCommand("  /provider ollama")).isTrue();
        assertThat(SlashCommand.isCommand("hello there")).isFalse();
        assertThat(SlashCommand.isCommand(null)).isFalse();
    }

    @Test
    void parsesNameAndArg() {
        SlashCommand bare = SlashCommand.parse("/skills");
        assertThat(bare.name()).isEqualTo("skills");
        assertThat(bare.arg()).isEmpty();

        SlashCommand withArg = SlashCommand.parse("/provider  ollama");
        assertThat(withArg.name()).isEqualTo("provider");
        assertThat(withArg.arg()).isEqualTo("ollama");

        SlashCommand multiArg = SlashCommand.parse("/model gemini-3.1-flash-lite-preview");
        assertThat(multiArg.name()).isEqualTo("model");
        assertThat(multiArg.arg()).isEqualTo("gemini-3.1-flash-lite-preview");
    }

    @Test
    void lowercasesCommandName() {
        assertThat(SlashCommand.parse("/HELP").name()).isEqualTo("help");
    }
}
