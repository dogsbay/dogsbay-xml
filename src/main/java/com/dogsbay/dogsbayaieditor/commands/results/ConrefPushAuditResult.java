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
 * The outcome of a {@code conref_push_audit}: the inventory of conref-push operations
 * and the problems found (broken sibling pairing or an unresolvable push target).
 *
 * @param pushes every {@code @conaction} usage (file, line, element, action, target)
 * @param issues pairing/target problems — each a push that won't apply cleanly
 */
public record ConrefPushAuditResult(List<Push> pushes, List<Issue> issues) {

    public ConrefPushAuditResult {
        pushes = pushes == null ? List.of() : List.copyOf(pushes);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** One conref-push operation. */
    public record Push(String file, int line, String element, String action, String target) {}

    /** A problem with a conref-push operation. */
    public record Issue(String file, int line, String element, String problem) {}
}
