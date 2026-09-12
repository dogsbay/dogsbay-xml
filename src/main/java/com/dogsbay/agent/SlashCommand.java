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

/**
 * A parsed slash command from the chat input (e.g. {@code /provider ollama} →
 * name "provider", arg "ollama"). Input not starting with {@code /} is a normal
 * prompt and is sent to the agent instead.
 */
public record SlashCommand(String name, String arg) {

    /** True if {@code input} is a slash command (starts with {@code /}). */
    public static boolean isCommand(String input) {
        return input != null && input.strip().startsWith("/");
    }

    /** Parse a slash command; {@code arg} is empty when none was given. */
    public static SlashCommand parse(String input) {
        String s = input.strip();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        s = s.strip();
        int sp = s.indexOf(' ');
        if (sp < 0) {
            return new SlashCommand(s.toLowerCase(), "");
        }
        return new SlashCommand(s.substring(0, sp).toLowerCase(), s.substring(sp + 1).strip());
    }
}
