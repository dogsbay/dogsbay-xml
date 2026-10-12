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

package com.dogsbay.dogsbayaieditor.links.metadata;

import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;

/**
 * Evaluates a file's {@link MetadataSnapshot} against a {@link MetadataPolicy} — the
 * direct in-tool audit. Produces typed per-field {@link MetadataFinding}s (which the
 * authoring panel and {@code --from-policy} consume): missing {@code required} →
 * error, missing {@code recommended} → warning, {@code forbidden} present → error,
 * value not in {@code allowedValues} or not matching {@code pattern} → error.
 *
 * <p>Findings are file-level ({@code line = -1}); the message names the field. (The
 * portable Schematron export gives the same checks with SVRL locations.)
 */
public final class MetadataEvaluator {

    private MetadataEvaluator() {}

    public static List<MetadataFinding> evaluate(String file, MetadataSnapshot snap,
            MetadataPolicy policy) {
        List<MetadataFinding> out = new ArrayList<>();
        boolean mapLike = isMapRoot(snap.rootType());
        for (MetadataRule rule : policy.rulesFor(snap.rootType())) {
            // A rule with no topic type means "every topic" (see MetadataRule). A map /
            // bookmap / subjectScheme is not a topic and carries no topic content
            // metadata, so an any-topic rule must not fire on it (e.g. a required
            // <keyword> policy should flag topics, not keydef maps). A rule that
            // explicitly targets a map type still applies via rulesFor() above.
            if (mapLike && (rule.topicType() == null || rule.topicType().isBlank())) {
                continue;
            }
            String fld = rule.field().key();
            String label = rule.field().label();
            int line = snap.lineFor(rule.field());
            List<String> values = snap.get(rule.field());
            switch (rule.presence()) {
                case REQUIRED -> {
                    if (values.isEmpty()) {
                        out.add(new MetadataFinding(file, line, fld,
                                "missing required " + label, "error"));
                    } else {
                        checkValues(file, rule, values, line, out);
                    }
                }
                case RECOMMENDED -> {
                    if (values.isEmpty()) {
                        out.add(new MetadataFinding(file, line, fld,
                                "recommended " + label + " is missing", "warning"));
                    } else {
                        checkValues(file, rule, values, line, out);
                    }
                }
                case FORBIDDEN -> {
                    if (!values.isEmpty()) {
                        out.add(new MetadataFinding(file, line, fld,
                                label + " is not allowed on <" + snap.rootType() + ">",
                                "error"));
                    }
                }
            }
        }
        return out;
    }

    /** A map-like root carries publication metadata, not topic content metadata. */
    private static boolean isMapRoot(String rootType) {
        if (rootType == null) {
            return false;
        }
        return switch (rootType.toLowerCase(java.util.Locale.ROOT)) {
            case "map", "bookmap", "subjectscheme" -> true;
            default -> false;
        };
    }

    private static void checkValues(String file, MetadataRule rule, List<String> values,
            int line, List<MetadataFinding> out) {
        // Value/pattern constraints apply to simple text/attr values, not the
        // composite name=content/value of othermeta/data (matches the Schematron export).
        if (rule.field().kind() != MetadataField.ValueKind.TEXT
                && rule.field().kind() != MetadataField.ValueKind.ATTR) {
            return;
        }
        String fld = rule.field().key();
        String label = rule.field().label();
        for (String v : values) {
            if (!rule.allowedValues().isEmpty() && !rule.allowedValues().contains(v)) {
                out.add(new MetadataFinding(file, line, fld,
                        label + " value “" + v + "” is not allowed (expected: "
                                + String.join(", ", rule.allowedValues()) + ")", "error"));
            }
            if (rule.pattern() != null && !v.matches(rule.pattern())) {
                out.add(new MetadataFinding(file, line, fld,
                        label + " value “" + v + "” does not match "
                                + rule.pattern(), "error"));
            }
        }
    }
}
