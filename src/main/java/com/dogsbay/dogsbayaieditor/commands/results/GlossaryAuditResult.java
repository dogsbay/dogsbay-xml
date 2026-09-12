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
 * The outcome of a {@code glossary_audit}: the glossary-entry inventory, references that
 * don't resolve to a glossary term (undefined), and glossentries nothing references
 * (unused).
 *
 * @param entries   every {@code <glossentry>} found (file + id + term)
 * @param undefined {@code <abbreviated-form>}/{@code <term>} references whose key doesn't
 *                  resolve to a glossary entry (needs a rootMap; empty without one)
 * @param unused    glossentries that no reference points at (needs a rootMap)
 */
public record GlossaryAuditResult(List<Entry> entries, List<Ref> undefined,
        List<Entry> unused) {

    public GlossaryAuditResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        undefined = undefined == null ? List.of() : List.copyOf(undefined);
        unused = unused == null ? List.of() : List.copyOf(unused);
    }

    /** One glossary entry. */
    public record Entry(String file, String id, String term) {}

    /** A reference whose key doesn't resolve to a glossary entry. */
    public record Ref(String file, int line, String element, String keyref) {}
}
