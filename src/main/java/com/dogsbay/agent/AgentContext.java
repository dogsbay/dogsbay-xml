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

import java.nio.file.Path;
import java.util.List;

/**
 * Ambient editor context the host surfaces to the agent each turn (data seam 2).
 * All fields are optional — a standalone host returns {@link #empty()}.
 *
 * <p>The enriched fields ({@code docType}, {@code grammar}, {@code deliverable},
 * {@code deliverableMap}) let the agent answer correctly on the first turn —
 * knowing the document is a DITA concept governed by concept.dtd, and which
 * deliverable it publishes under — without spending a tool round-trip to
 * rediscover what the editor already knows. They are bounded and cheap: the host
 * only reads state it already has, never triggering validation or a project scan.
 *
 * @param activeFile     the file currently being edited, or {@code null}
 * @param selection      the current selection text, or {@code null}/empty
 * @param openDocuments  paths of all open documents
 * @param mode           chat mode label (e.g. "Ask", "Agent", "DITA Agent")
 * @param docType        the active document's type (e.g. "concept (DITA)"), or {@code null}
 * @param grammar        the governing grammar (e.g. "concept.dtd (DTD)"), or {@code null}
 * @param deliverable    the active deliverable's name, or {@code null}
 * @param deliverableMap the active deliverable's root map, or {@code null}
 */
public record AgentContext(
        Path activeFile,
        String selection,
        List<Path> openDocuments,
        String mode,
        String docType,
        String grammar,
        String deliverable,
        Path deliverableMap) {

    /** Cap on how many open-document paths are listed before summarizing the rest. */
    private static final int MAX_OPEN_LISTED = 12;

    /** Cap on the selection length embedded in the per-turn fragment (chars). */
    private static final int MAX_SELECTION_CHARS = 4000;

    public AgentContext {
        openDocuments = openDocuments == null ? List.of() : List.copyOf(openDocuments);
    }

    /** Minimal context without the enriched document fields (backwards-compatible). */
    public AgentContext(Path activeFile, String selection, List<Path> openDocuments, String mode) {
        this(activeFile, selection, openDocuments, mode, null, null, null, null);
    }

    /** No editor context (standalone / nothing open). */
    public static AgentContext empty() {
        return new AgentContext(null, null, List.of(), "Agent");
    }

    /** Renders the context as a short system-prompt fragment, or "" when empty. */
    public String toPromptFragment() {
        if (activeFile == null && (selection == null || selection.isBlank())
                && openDocuments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## Editor context\n");
        if (mode != null && !mode.isBlank()) {
            sb.append("- Mode: ").append(mode).append('\n');
        }
        if (activeFile != null) {
            sb.append("- Active file: ").append(activeFile);
            String annot = typeAnnotation();
            if (annot != null) {
                sb.append("  ").append(annot);
            }
            sb.append('\n');
        }
        if (deliverable != null && !deliverable.isBlank()) {
            sb.append("- Active deliverable: ").append(deliverable);
            if (deliverableMap != null && deliverableMap.getFileName() != null) {
                sb.append("  (map: ").append(deliverableMap.getFileName()).append(')');
            }
            sb.append('\n');
        }
        if (!openDocuments.isEmpty()) {
            sb.append("- Open documents: ").append(boundedList()).append('\n');
        }
        if (selection != null && !selection.isBlank()) {
            sb.append("- Current selection:\n```\n").append(boundedSelection()).append("\n```\n");
        }
        return sb.toString();
    }

    /** The selection, capped so a very large selection can't bloat every turn. */
    private String boundedSelection() {
        if (selection.length() <= MAX_SELECTION_CHARS) {
            return selection;
        }
        return selection.substring(0, MAX_SELECTION_CHARS)
                + "\n…[selection truncated, " + (selection.length() - MAX_SELECTION_CHARS)
                + " more chars]";
    }

    /** "(concept (DITA); concept.dtd (DTD))" — the document type and grammar, or null. */
    private String typeAnnotation() {
        StringBuilder a = new StringBuilder();
        if (docType != null && !docType.isBlank()) {
            a.append(docType);
        }
        if (grammar != null && !grammar.isBlank()) {
            if (a.length() > 0) {
                a.append("; ");
            }
            a.append(grammar);
        }
        return a.length() == 0 ? null : "(" + a + ")";
    }

    /** The open-document list, capped so a big workspace can't flood the prompt. */
    private String boundedList() {
        if (openDocuments.size() <= MAX_OPEN_LISTED) {
            return openDocuments.toString();
        }
        List<Path> head = openDocuments.subList(0, MAX_OPEN_LISTED);
        return head + " …+" + (openDocuments.size() - MAX_OPEN_LISTED) + " more";
    }
}
