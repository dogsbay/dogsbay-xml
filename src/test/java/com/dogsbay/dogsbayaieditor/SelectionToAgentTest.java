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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SelectionToAgentTest {

    private Consumer<String> sink;

    @AfterEach
    void unregister() {
        if (sink != null) {
            SelectionToAgent.unregister(sink);
        }
    }

    @Test
    void sendsStrippedTextOnlyWhileASinkIsRegistered() {
        List<String> got = new ArrayList<>();
        assertThat(SelectionToAgent.available()).isFalse();
        assertThat(SelectionToAgent.send("validate a.dita")).isFalse();

        sink = got::add;
        SelectionToAgent.register(sink);
        assertThat(SelectionToAgent.available()).isTrue();
        assertThat(SelectionToAgent.send("  validate a.dita\n")).isTrue();
        assertThat(SelectionToAgent.send("   ")).isFalse();
        assertThat(SelectionToAgent.send(null)).isFalse();
        assertThat(got).containsExactly("validate a.dita");

        SelectionToAgent.unregister(got::add);   // a different consumer: not ours, still registered
        assertThat(SelectionToAgent.available()).isTrue();
        SelectionToAgent.unregister(sink);
        assertThat(SelectionToAgent.available()).isFalse();
    }
}
