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

package com.dogsbay.xml.editor;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.text.Segment;

/**
 * A small recycler for {@link Segment} objects used while painting.
 *
 * <p>The view classes fetch a Segment for nearly every line they draw. Allocating one per
 * call is measurable during a fast scroll, so borrowed instances are handed back and
 * reused. This replaces a fork of a Swing-internal cache.
 *
 * <p>Not thread-safe, and deliberately so: every caller is on the Event Dispatch Thread
 * during paint. A Segment borrowed on one thread must not be released on another. The pool
 * is bounded, so a caller that forgets to release simply loses the reuse, never leaks.
 */
final class SegmentPool {

    /** Enough for the nesting depth the view classes actually reach while painting. */
    private static final int MAX_POOLED = 8;

    private static final Deque<Segment> POOL = new ArrayDeque<>(MAX_POOLED);

    private SegmentPool() {
    }

    /**
     * Borrows a Segment. Its contents are undefined; the caller fills it.
     *
     * @return a Segment to use and then hand back to {@link #release}
     */
    static Segment get() {
        Segment segment = POOL.pollLast();
        return segment != null ? segment : new Segment();
    }

    /**
     * Returns a borrowed Segment to the pool. Passing null is allowed and ignored.
     *
     * @param segment the Segment previously obtained from {@link #get}
     */
    static void release(Segment segment) {
        if (segment == null) {
            return;
        }

        // Drop the reference to the document's character array so a pooled Segment can
        // never pin a closed document's buffer in memory.
        segment.array = null;
        segment.count = 0;
        segment.offset = 0;

        if (POOL.size() < MAX_POOLED) {
            POOL.addLast(segment);
        }
    }
}
