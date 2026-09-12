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
 * The outcome of a {@code reltable_audit}: validation issues plus the related-links
 * the reltables would generate (the preview).
 *
 * @param issues         validation findings (broken/maptarget/type-mismatch/degenerate)
 * @param generatedLinks the projected per-topic related-links
 */
public record ReltableAuditResult(List<ReltableIssue> issues,
        List<ReltableLink> generatedLinks) {

    public ReltableAuditResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        generatedLinks = generatedLinks == null ? List.of() : List.copyOf(generatedLinks);
    }

    /** One generated related-link (source topic → target topic). */
    public record ReltableLink(String source, String target, String text, int row) {}

    /** True when no error-severity issues were found. */
    public boolean isClean() {
        return issues.stream().noneMatch(i -> "error".equals(i.severity()));
    }
}
