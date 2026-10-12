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
package com.dogsbay.dogsbayaieditor;

import java.util.function.Consumer;

/**
 * Where "Send selection to AI Agent" goes. The agent plugin registers the
 * sink when it activates; the editor's popup and the terminal offer the
 * action only while one is registered. Text is sent as a prompt to the agent
 * tab currently in front, so a tutorial's commands run without copying.
 */
public final class SelectionToAgent {

    public static final String LABEL = "Send selection to AI Agent";

    private static volatile Consumer<String> sink;

    private SelectionToAgent() {
    }

    public static void register(Consumer<String> target) {
        sink = target;
    }

    public static void unregister(Consumer<String> target) {
        if (sink == target) {
            sink = null;
        }
    }

    public static boolean available() {
        return sink != null;
    }

    /** Send {@code text} to the current agent tab; blank text and no sink are no-ops. */
    public static boolean send(String text) {
        Consumer<String> s = sink;
        if (s == null || text == null || text.isBlank()) {
            return false;
        }
        s.accept(text.strip());
        return true;
    }
}
