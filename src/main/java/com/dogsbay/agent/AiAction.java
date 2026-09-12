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

package com.dogsbay.agent;

/**
 * A single-turn AI action applied to selected text (rewrite, simplify, …). The
 * prompt instructs the model to return only the transformed content so the
 * result is directly substitutable into the document.
 */
public enum AiAction {

    REWRITE("Rewrite",
            "Rewrite the content below to be clearer and more concise while preserving its "
                    + "meaning and any XML/DITA markup. Return ONLY the rewritten content — no "
                    + "explanation, no code fences."),
    SIMPLIFY("Simplify",
            "Rewrite the content below in plainer language for a general audience, preserving "
                    + "any XML/DITA markup. Return ONLY the simplified content — no explanation, "
                    + "no code fences."),
    FIX("Fix issues",
            "Correct errors in the content below (well-formedness, obvious DITA/XML validation "
                    + "problems, typos) with minimal changes. Return ONLY the corrected content — "
                    + "no explanation, no code fences."),
    SUMMARIZE("Summarize",
            "Summarize the content below concisely. Return ONLY the summary — no preamble, no "
                    + "code fences."),
    EXPAND("Expand",
            "Expand the content below with additional relevant detail, preserving any XML/DITA "
                    + "markup. Return ONLY the expanded content — no explanation, no code fences."),

    // DITA-specific transforms (grouped separately in the menu).
    SHORTDESC("Generate shortdesc", true,
            "From the content below, write a concise DITA <shortdesc> element (one or two "
                    + "sentences summarizing it). Return ONLY the <shortdesc>…</shortdesc> element "
                    + "— no explanation, no code fences."),
    WRAP_NOTE("Wrap as note", true,
            "Wrap the content below in a DITA <note> element, preserving any existing markup. "
                    + "Return ONLY the resulting <note>…</note> — no explanation, no code fences.");

    private final String label;
    private final boolean dita;
    private final String systemPrompt;

    AiAction(String label, String systemPrompt) {
        this(label, false, systemPrompt);
    }

    AiAction(String label, boolean dita, String systemPrompt) {
        this.label = label;
        this.dita = dita;
        this.systemPrompt = systemPrompt;
    }

    public String label() {
        return label;
    }

    /** True for DITA-specific transforms, grouped under their own menu section. */
    public boolean isDita() {
        return dita;
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
