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
 * Which editor tool to reach for first, in one place. The MCP server's
 * {@code instructions}, the brief a hosted agent gets and the built-in agent's
 * steering all quote these, so "validate" cannot mean one thing to one agent and
 * something else to another.
 *
 * <p>Written after an agent asked to map a project called check_links, never found
 * project_health, and wrote its own extractor: with a hundred tools listed flat,
 * the entry points have to be named.
 */
public final class AgentGuidance {

    /** Validation and auditing: one done-gate, granular tools for re-checks. */
    public static final String CHECKS =
            "To validate or audit, start with project_health: one call covers check_links (references and keys), "
            + "conref_audit (element ids), validate_project (DTD validation of the publication set) and "
            + "metadata_audit, plus schematron_project when you pass schematron. Narrow it with include and "
            + "severity. Re-check a single dimension after a fix with the granular tool, or validate_document "
            + "for one file.";

    /** Structure: one graph call instead of a lookup per file. */
    public static final String STRUCTURE =
            "For how the project connects (maps, topics, keys, reuse, reltables, what each deliverable ships), "
            + "call project_graph once rather than where_used per file.";

    /** Pages for people: a template over command data, not a hand-written extractor. */
    public static final String REPORTS =
            "For a standalone HTML page (a relationship map, a health report), call render_report with a "
            + "built-in template or one in .dogsbay/reports/; do not write your own extractor or page.";

    private AgentGuidance() {
    }

    /** The MCP {@code initialize} instructions for the dogsbay-editor server. */
    public static String mcpInstructions() {
        return "DogsBay XML editor tools for DITA projects. " + CHECKS + " " + STRUCTURE + " " + REPORTS
                + " Prefer these tools to a shell: they see unsaved buffers and are audited to your session.";
    }
}
