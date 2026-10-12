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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.review.Myers.Op;
import com.dogsbay.xml.review.Myers.Step;

class MyersTest {

    private static String render(List<Step> steps, List<String> a, List<String> b) {
        StringBuilder sb = new StringBuilder();
        for (Step s : steps) {
            sb.append(switch (s.op()) {
                case EQUAL -> "=" + a.get(s.a());
                case DELETE -> "-" + a.get(s.a());
                case INSERT -> "+" + b.get(s.b());
            }).append(' ');
        }
        return sb.toString().trim();
    }

    @Test
    void producesAMinimalScriptThatReplaysBothSides() {
        List<String> a = List.of("<p>", "alpha", "</p>", "<p>", "beta", "</p>", "<p>", "gamma", "</p>");
        List<String> b = List.of("<p>", "alpha", "</p>", "<note>", "gamma", "</note>", "<p>", "gamma", "</p>");
        List<Step> steps = Myers.diff(a, b, String::equals);
        // several minimal scripts exist; the engine's consistency pass picks the structural one
        assertThat(render(steps, a, b)).startsWith("=<p> =alpha =</p>").endsWith("=gamma =</p>");
        assertThat(steps.stream().filter(s -> s.op() != Op.EQUAL).count()).as("D").isEqualTo(6);
    }

    @Test
    void replaysExactlyOnRandomInputs() {
        java.util.Random r = new java.util.Random(7);
        for (int round = 0; round < 200; round++) {
            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            int n = r.nextInt(12);
            for (int i = 0; i < n; i++) {
                a.add(String.valueOf((char) ('a' + r.nextInt(4))));
            }
            b.addAll(a);
            for (int k = 0; k < r.nextInt(5); k++) {
                if (!b.isEmpty() && r.nextBoolean()) {
                    b.remove(r.nextInt(b.size()));
                } else {
                    b.add(r.nextInt(b.size() + 1), String.valueOf((char) ('a' + r.nextInt(4))));
                }
            }
            List<Step> steps = Myers.diff(a, b, String::equals);
            List<String> left = new ArrayList<>();
            List<String> right = new ArrayList<>();
            for (Step s : steps) {
                switch (s.op()) {
                    case EQUAL -> { left.add(a.get(s.a())); right.add(b.get(s.b())); }
                    case DELETE -> left.add(a.get(s.a()));
                    case INSERT -> right.add(b.get(s.b()));
                }
            }
            assertThat(left).as("left replay " + a + " -> " + b).isEqualTo(a);
            assertThat(right).as("right replay " + a + " -> " + b).isEqualTo(b);
        }
    }

    @Test
    void emptyAndIdenticalInputs() {
        assertThat(Myers.diff(List.of(), List.of(), String::equals)).isEmpty();
        assertThat(Myers.diff(List.of("x"), List.of("x"), String::equals)).extracting(Step::op).containsExactly(Op.EQUAL);
        assertThat(Myers.diff(List.of("x"), List.of(), String::equals)).extracting(Step::op).containsExactly(Op.DELETE);
        assertThat(Myers.diff(List.of(), List.of("x"), String::equals)).extracting(Step::op).containsExactly(Op.INSERT);
    }
}
