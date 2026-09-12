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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Myers' O((N+M)D) diff over two lists, as an edit script. Time and memory
 * grow with the size of the difference, not the documents, so a one-word
 * change in a long topic is cheap. When the difference exceeds
 * {@link #MAX_D} the caller is told so, rather than handed a script whose
 * rendering would be worse than a plain overwrite.
 */
final class Myers {

    enum Op { EQUAL, DELETE, INSERT }

    /** One step: {@code a} indexes the left list, {@code b} the right; -1 when absent. */
    record Step(Op op, int a, int b) {}

    /** Too different to review as marked changes. */
    static final class TooDifferent extends RuntimeException {
        TooDifferent(int d) {
            super("the two versions differ in more than " + d + " places; review them as a whole instead");
        }
    }

    static final int MAX_D = 4000;

    private Myers() {
    }

    static <T> List<Step> diff(List<T> a, List<T> b, BiPredicate<T, T> same) {
        int n = a.size();
        int m = b.size();
        int max = n + m;
        int limit = Math.min(max, MAX_D);
        int offset = limit + 1;
        int[] v = new int[2 * limit + 3];
        List<int[]> trace = new ArrayList<>();
        for (int d = 0; d <= limit; d++) {
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];
                } else {
                    x = v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && same.test(a.get(x), b.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    return backtrack(trace, a, b, n, m, offset, same);
                }
            }
        }
        throw new TooDifferent(limit);
    }

    private static <T> List<Step> backtrack(List<int[]> trace, List<T> a, List<T> b, int n, int m, int offset,
            BiPredicate<T, T> same) {
        List<Step> out = new ArrayList<>();
        int x = n;
        int y = m;
        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] v = trace.get(d);
            int k = x - y;
            int prevK;
            if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = v[offset + prevK];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                out.add(new Step(Op.EQUAL, x - 1, y - 1));
                x--;
                y--;
            }
            if (d > 0) {
                if (x == prevX) {
                    out.add(new Step(Op.INSERT, -1, y - 1));
                } else {
                    out.add(new Step(Op.DELETE, x - 1, -1));
                }
            }
            x = prevX;
            y = prevY;
        }
        Collections.reverse(out);
        return out;
    }
}
