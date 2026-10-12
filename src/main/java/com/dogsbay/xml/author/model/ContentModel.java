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
package com.dogsbay.xml.author.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The order and cardinality of a block's children: an ordered list of slots,
 * each naming the element types it takes and how many. Insertion checks use
 * order and the upper bound only; a document being written may still lack a
 * required child, which the validator reports rather than the insert refusing.
 *
 * <p>Matching is greedy left to right: each slot consumes as many consecutive
 * children it names as its maximum allows, and every child must be consumed.
 * That is enough for DITA's content models, which are sequences of choice
 * groups with repetition.
 */
public final class ContentModel {

    /** One position in the sequence: which types, at least and at most how many. */
    public record Slot(Set<String> names, int min, int max) {
        public boolean takes(String name) {
            return names.contains(name);
        }
    }

    public static final int UNBOUNDED = Integer.MAX_VALUE;

    private final List<Slot> slots;

    private ContentModel(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    public static ContentModel of(Slot... slots) {
        return new ContentModel(List.of(slots));
    }

    /** Exactly one of {@code names}. */
    public static Slot one(String... names) {
        return new Slot(ordered(names), 1, 1);
    }

    /** Zero or one of {@code names}. */
    public static Slot optional(String... names) {
        return new Slot(ordered(names), 0, 1);
    }

    /** Zero or more of {@code names}, in any mix. */
    public static Slot many(String... names) {
        return new Slot(ordered(names), 0, UNBOUNDED);
    }

    /** One or more of {@code names}, in any mix. */
    public static Slot some(String... names) {
        return new Slot(ordered(names), 1, UNBOUNDED);
    }

    /** Declaration order is menu order. */
    private static Set<String> ordered(String... names) {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(List.of(names)));
    }

    public List<Slot> slots() {
        return slots;
    }

    /** Every type any slot takes. */
    public Set<String> allNames() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Slot s : slots) {
            out.addAll(s.names());
        }
        return out;
    }

    /**
     * Whether {@code children} is a valid sequence as far as order and upper
     * bounds go (lower bounds are the validator's business).
     */
    public boolean accepts(List<String> children) {
        int i = 0;
        for (Slot slot : slots) {
            int taken = 0;
            while (i < children.size() && taken < slot.max() && slot.takes(children.get(i))) {
                i++;
                taken++;
            }
        }
        return i == children.size();
    }

    /**
     * Where {@code type} can go into {@code children}, as close to
     * {@code preferred} as possible: {@code preferred} itself first, then
     * positions after it, then before. Returns -1 when no position is valid.
     */
    public int insertIndex(List<String> children, String type, int preferred) {
        int p = Math.max(0, Math.min(preferred, children.size()));
        if (acceptsWith(children, type, p)) {
            return p;
        }
        for (int i = p + 1; i <= children.size(); i++) {
            if (acceptsWith(children, type, i)) {
                return i;
            }
        }
        for (int i = p - 1; i >= 0; i--) {
            if (acceptsWith(children, type, i)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether {@code type} fits anywhere in {@code children}. */
    public boolean canInsert(List<String> children, String type) {
        return insertIndex(children, type, children.size()) >= 0;
    }

    private boolean acceptsWith(List<String> children, String type, int at) {
        List<String> with = new ArrayList<>(children.size() + 1);
        with.addAll(children.subList(0, at));
        with.add(type);
        with.addAll(children.subList(at, children.size()));
        return accepts(with);
    }
}
