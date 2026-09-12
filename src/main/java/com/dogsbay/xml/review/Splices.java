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
import java.util.Comparator;
import java.util.List;

/**
 * Textual splices into a source string, applied together. One
 * implementation for the whole review model, with one containment rule:
 * a splice that lies inside the span another splice deletes is dropped,
 * because the outer removal already covers it (a comment inside a deleted
 * block, an inline mark inside an element a pair replaces). At the same
 * offset a replacement runs before a pure insertion, so the insertion lands
 * in front of the replaced text.
 */
final class Splices {

    record Splice(int at, int delLen, String insert) {
        int end() {
            return at + delLen;
        }
    }

    private Splices() {
    }

    static String apply(String text, List<Splice> splices) {
        List<Splice> kept = new ArrayList<>();
        for (Splice s : splices) {
            boolean inside = false;
            for (Splice o : splices) {
                if (o == s || o.delLen() == 0) {
                    continue;
                }
                // A pure insertion at the edge of a deleted span is beside it, not in it.
                boolean within = s.delLen() > 0
                        ? o.at() <= s.at() && s.end() <= o.end() && !(o.at() == s.at() && o.end() == s.end())
                        : o.at() < s.at() && s.at() < o.end();
                if (within) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                kept.add(s);
            }
        }
        kept.sort(Comparator.comparingInt(Splice::at).reversed()
                .thenComparing(Comparator.comparingInt(Splice::delLen).reversed()));
        StringBuilder sb = new StringBuilder(text);
        for (Splice s : kept) {
            sb.replace(s.at(), s.end(), s.insert());
        }
        return sb.toString();
    }
}
