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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.List;

/**
 * Validation of every deliverable, each finding once. A file shared by four
 * deliverables used to be reported four times, and "these two files are broken
 * in every deliverable" had to be worked out by diffing; the finding now says
 * which deliverables it breaks.
 *
 * @param deliverables per-deliverable counts, in project order
 * @param findings     one entry per error, with the deliverables whose publication set holds the file
 * @param truncated    findings left out after the cap
 */
public record DeliverablesReport(List<Deliverable> deliverables, List<Finding> findings, int truncated) {

    public DeliverablesReport {
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** True when no deliverable has an invalid file. */
    public boolean isClean() {
        return deliverables.stream().allMatch(d -> d.failed() == 0);
    }

    /**
     * @param name   the deliverable name
     * @param map    its root map (absolute path)
     * @param total  files in its publication set
     * @param passed files that validate
     * @param failed files that do not
     */
    public record Deliverable(String name, String map, int total, int passed, int failed) {
    }

    /**
     * @param file         the invalid file (absolute path)
     * @param line         line, or -1
     * @param column       column, or -1
     * @param severity     error or warning
     * @param message      the validator's message, shortened where it enumerated a content model
     * @param deliverables the deliverables this file breaks
     */
    public record Finding(String file, int line, int column, String severity, String message,
            List<String> deliverables) {

        public Finding {
            deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        }
    }
}
