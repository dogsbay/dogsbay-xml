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

package com.dogsbay.dogsbayaieditor.ditaproject;

import java.util.List;

/**
 * The compact, bounded result of a project-wide batch operation.
 *
 * <p>The point is O(1) caller/agent context regardless of project size: a clean
 * 500-file run returns three counts, not 500 file dumps; findings are capped and
 * the overflow recorded in {@link #truncated()} so a flood of errors can't blow
 * up the payload while still signalling "there are more".
 *
 * @param <T>       the finding type (e.g. a per-file validation result)
 * @param total     items processed
 * @param passed    items with no findings
 * @param failed    items with at least one finding
 * @param findings  findings, capped (see {@link #capped})
 * @param truncated number of findings omitted beyond the cap (0 when none)
 */
public record BatchResult<T>(
    int total,
    int passed,
    int failed,
    List<T> findings,
    int truncated
) {
    public BatchResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** True when nothing failed. */
    public boolean isClean() {
        return failed == 0;
    }

    /**
     * Build a result, capping {@code allFindings} to {@code cap} and recording
     * the overflow in {@link #truncated()}.
     */
    public static <T> BatchResult<T> capped(int total, int passed, int failed,
            List<T> allFindings, int cap) {
        List<T> all = allFindings == null ? List.of() : allFindings;
        if (cap < 0 || all.size() <= cap) {
            return new BatchResult<>(total, passed, failed, all, 0);
        }
        return new BatchResult<>(total, passed, failed,
                List.copyOf(all.subList(0, cap)), all.size() - cap);
    }
}
