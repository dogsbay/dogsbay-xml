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

package com.dogsbay.dogsbayaieditor.validate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * Converts a project-wide {@link BatchResult} of {@link FileValidation}s into
 * {@link XMLError}s for the editor's error pane. Each error carries its file's
 * URL as the systemId so the pane opens the right file on click (the pane's
 * click handler opens by systemId when it doesn't match the active document).
 */
public final class ProjectValidationErrors {

    private ProjectValidationErrors() {}

    /** Flatten failing files into per-error XMLErrors (file URL + line + message). */
    public static List<XMLError> toErrors(BatchResult<FileValidation> result) {
        List<XMLError> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (FileValidation fv : result.findings()) {
            String systemId = new File(fv.file()).toURI().toString();
            for (ValidationError e : fv.errors()) {
                out.add(new XMLError(systemId, e.line(), e.column(),
                        typeOf(e.severity()), e.message()));
            }
        }
        return out;
    }

    /**
     * Flatten Schematron findings into XMLErrors. SVRL gives an XPath location,
     * not a line number, so line/column are -1 and the location is appended to
     * the message; the file URL still lets the pane open the file on click.
     */
    public static List<XMLError> fromSchematron(BatchResult<SchematronFinding> result) {
        List<XMLError> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (SchematronFinding f : result.findings()) {
            String systemId = new File(f.file()).toURI().toString();
            int line = f.line() > 0 ? f.line() : -1;
            // With a resolved line, click-to-open jumps to the node; otherwise
            // keep the XPath location in the message as a navigation hint.
            String msg = (line < 0 && f.location() != null && !f.location().isEmpty())
                    ? f.message() + "  [" + f.location() + "]"
                    : f.message();
            out.add(new XMLError(systemId, line, -1, typeOf(f.role()), msg));
        }
        return out;
    }

    /**
     * Flatten DITA-OT deep-validation diagnostics into XMLErrors. DITA-OT messages
     * carry a best-effort source path; it is resolved against {@code projectRoot}
     * (absolute paths and {@code file:} URIs are used as-is) and falls back to the
     * deliverable's map when no usable path is present, so the pane always opens
     * <em>something</em> on click. The message code is prefixed for context.
     *
     * @param results     per-deliverable deep-validation results
     * <p>Deliverables share topics, so the same diagnostic recurs across every
     * deliverable that ships a faulty topic. Identical diagnostics
     * (same file + line + column + message) are <strong>de-duplicated</strong> so
     * the pane shows each problem once — but the <em>union</em> across deliverables
     * is kept, so an issue that only surfaces in one DITAVAL-filtered deliverable
     * still appears. (The per-deliverable view lives in the CLI/MCP result.)
     *
     * @param projectRoot root for resolving relative message paths, or null
     */
    public static List<XMLError> fromDitaOt(List<DitaOtValidation> results, File projectRoot) {
        List<XMLError> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DitaOtValidation d : results) {
            String fallbackSystemId = new File(d.map()).toURI().toString();
            java.util.List<com.dogsbay.dogsbayaieditor.links.Branch> branches =
                    branchesOf(d.map());
            for (DitaOtMessage m : d.messages()) {
                String systemId = resolveSystemId(m.file(), projectRoot, fallbackSystemId);
                // Best-effort branch attribution: DITA-OT names a branch's generated
                // resources with its dvrResourcePrefix/Suffix, so a message whose file
                // carries that affix belongs to that variant.
                String branch = branchAttribution(m.file(), branches);
                String msg = (m.code() != null ? "[" + m.code() + "] " : "")
                        + (branch != null ? "[branch: " + branch + "] " : "")
                        + m.message();
                if (seen.add(systemId + "\n" + m.line() + "\n" + m.column()
                        + "\n" + msg)) {
                    out.add(new XMLError(systemId, m.line(), m.column(),
                            typeOf(m.severity()), msg));
                }
            }
        }
        return out;
    }

    /**
     * Flatten DITA-OT <em>build</em> diagnostics (from "Build Deliverables") into
     * XMLErrors for the Project Validation pane, tagging each with its deliverable name
     * so the user can see and click through to what failed. Messages whose file can't be
     * resolved fall back to the project root.
     *
     * @param projectRoot root for resolving relative message paths, or null
     */
    public static List<XMLError> fromDitaOtBuilds(
            List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> results, File projectRoot) {
        List<XMLError> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        // No file → no clickable location (don't fall back to the project *directory*,
        // which the error pane would try to open as a document).
        String fallbackSystemId = null;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var b : results) {
            for (DitaOtMessage m : b.messages()) {
                String systemId = resolveSystemId(m.file(), projectRoot, fallbackSystemId);
                String msg = (m.code() != null ? "[" + m.code() + "] " : "")
                        + "[" + b.name() + "] " + m.message();
                if (seen.add(systemId + "\n" + m.line() + "\n" + m.column() + "\n" + msg)) {
                    out.add(new XMLError(systemId, m.line(), m.column(), typeOf(m.severity()), msg));
                }
            }
        }
        return out;
    }

    /**
     * Flatten controlled-value violations into XMLErrors. Each finding already
     * carries a 1-based line (from the element/ditaval source) and a message that
     * includes the near-miss suggestion; surfaced as warnings (an out-of-vocabulary
     * value is an authoring/governance issue, not a well-formedness error). The
     * file URL lets the pane open the file on click.
     */
    public static List<XMLError> fromConditions(BatchResult<ConditionViolation> result) {
        List<XMLError> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (ConditionViolation v : result.findings()) {
            String systemId = new File(v.file()).toURI().toString();
            int line = v.line() > 0 ? v.line() : -1;
            out.add(new XMLError(systemId, line, -1, XMLError.WARNING, v.message()));
        }
        return out;
    }

    /**
     * Flatten required-metadata findings into XMLErrors. Severity maps from the
     * finding ({@code error}/{@code warning}); the field name is prefixed for
     * context; line is the finding's (often -1 → file-level open).
     */
    public static List<XMLError> fromMetadata(BatchResult<MetadataFinding> result) {
        List<XMLError> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (MetadataFinding f : result.findings()) {
            String systemId = new File(f.file()).toURI().toString();
            int line = f.line() > 0 ? f.line() : -1;
            out.add(new XMLError(systemId, line, -1, typeOf(f.severity()), f.message()));
        }
        return out;
    }

    /** Branches of a deliverable's map, or empty (unreadable map / no branches). */
    private static java.util.List<com.dogsbay.dogsbayaieditor.links.Branch> branchesOf(
            String map) {
        if (map == null) {
            return java.util.List.of();
        }
        try {
            return com.dogsbay.dogsbayaieditor.links.BranchModel.enumerate(new File(map));
        } catch (java.io.IOException e) {
            return java.util.List.of();
        }
    }

    /**
     * The label of the branch a diagnostic file belongs to, or null. Best-effort:
     * DITA-OT prefixes/suffixes a branch's generated resource names with the
     * {@code <dvrResourcePrefix>}/{@code <dvrResourceSuffix>}, so we match the file's
     * basename against each branch's affixes. Branches without a resource affix can't
     * be attributed this way (the engine derives a name we don't model) → no match.
     */
    static String branchAttribution(String filePath,
            java.util.List<com.dogsbay.dogsbayaieditor.links.Branch> branches) {
        if (filePath == null || branches == null || branches.isEmpty()) {
            return null;
        }
        String base = new File(filePath).getName();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        for (com.dogsbay.dogsbayaieditor.links.Branch b : branches) {
            if (!b.resourcePrefix().isEmpty() && base.startsWith(b.resourcePrefix())) {
                return b.label();
            }
            if (!b.resourceSuffix().isEmpty() && stem.endsWith(b.resourceSuffix())) {
                return b.label();
            }
        }
        return null;
    }

    private static String resolveSystemId(String file, File projectRoot, String fallback) {
        if (file == null || file.isBlank()) {
            return fallback;
        }
        if (file.startsWith("file:")) {
            return file;
        }
        File f = new File(file);
        if (!f.isAbsolute() && projectRoot != null) {
            f = new File(projectRoot, file);
        }
        return f.exists() ? f.toURI().toString() : fallback;
    }

    private static int typeOf(String severity) {
        if (severity == null) {
            return XMLError.ERROR;
        }
        return switch (severity.toLowerCase()) {
            case "warning", "warn", "info" -> XMLError.WARNING;
            case "fatal" -> XMLError.FATAL;
            default -> XMLError.ERROR;
        };
    }
}
