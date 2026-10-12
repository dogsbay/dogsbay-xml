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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * A required-metadata policy: an ordered list of {@link MetadataRule}s. Declarative
 * and introspectable (the authoring panel reads it to show required fields and
 * govern values; {@code --from-policy} reads it to fill missing fields) — and it
 * {@linkplain #toSchematron() compiles to Schematron} for portable validation.
 *
 * <p>Serialized as a {@code <metadata-policy>} element, stored either in the shared
 * {@code .dogsbay/config.xml} (a team decision) or as a standalone {@code --policy}
 * file. Missing/malformed degrades to {@link #empty()} (no policy).
 */
public record MetadataPolicy(List<MetadataRule> rules) {

    public MetadataPolicy {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static MetadataPolicy empty() {
        return new MetadataPolicy(List.of());
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** The rules that apply to a topic whose root element is {@code topicType}. */
    public List<MetadataRule> rulesFor(String topicType) {
        return rules.stream().filter(r -> r.appliesTo(topicType)).toList();
    }

    // ── XML serialization (the <metadata-policy> element) ────────────────

    /** Parse a {@code <metadata-policy>} element; unknown fields/rows are skipped. */
    public static MetadataPolicy parse(Element policyEl) {
        if (policyEl == null) {
            return empty();
        }
        List<MetadataRule> rules = new ArrayList<>();
        for (Element r : policyEl.elements("rule")) {
            MetadataField field = MetadataField.byKey(r.attributeValue("field"));
            if (field == null) {
                continue; // unknown field name — skip rather than fail
            }
            String topicType = blankToNull(r.attributeValue("topic-type"));
            MetadataRule.Presence presence =
                    MetadataRule.Presence.parse(r.attributeValue("presence"));
            // Allowed values: the concise space-separated attribute (single-token
            // values) and/or <value> children (which preserve multi-word values).
            List<String> allowedValues = new ArrayList<>();
            String allowed = r.attributeValue("allowed-values");
            if (allowed != null && !allowed.isBlank()) {
                for (String t : allowed.trim().split("\\s+")) {
                    allowedValues.add(t);
                }
            }
            for (Element v : r.elements("value")) {
                String t = v.getTextTrim();
                if (!t.isEmpty()) {
                    allowedValues.add(t);
                }
            }
            String pattern = blankToNull(r.attributeValue("pattern"));
            rules.add(new MetadataRule(topicType, field, presence, allowedValues, pattern));
        }
        return new MetadataPolicy(rules);
    }

    /** Build a fresh {@code <metadata-policy>} element from this policy. */
    public Element toElement() {
        Element policyEl = DocumentHelper.createElement("metadata-policy");
        for (MetadataRule rule : rules) {
            Element r = policyEl.addElement("rule");
            if (rule.topicType() != null && !rule.topicType().isBlank()) {
                r.addAttribute("topic-type", rule.topicType());
            }
            r.addAttribute("field", rule.field().key());
            r.addAttribute("presence",
                    rule.presence().name().toLowerCase(java.util.Locale.ROOT));
            if (!rule.allowedValues().isEmpty()) {
                boolean anyWhitespace = rule.allowedValues().stream()
                        .anyMatch(v -> v.chars().anyMatch(Character::isWhitespace));
                if (anyWhitespace) {
                    // multi-word values can't round-trip through a space-separated
                    // attribute — emit <value> children
                    for (String v : rule.allowedValues()) {
                        r.addElement("value").setText(v);
                    }
                } else {
                    r.addAttribute("allowed-values", String.join(" ", rule.allowedValues()));
                }
            }
            if (rule.pattern() != null && !rule.pattern().isBlank()) {
                r.addAttribute("pattern", rule.pattern());
            }
        }
        return policyEl;
    }

    /** Read a standalone policy file whose root is {@code <metadata-policy>}. */
    public static MetadataPolicy fromFile(File file) {
        if (file == null || !file.isFile()) {
            return empty();
        }
        try {
            SAXReader reader = new SAXReader();
            reader.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Element root = reader.read(file).getRootElement();
            return "metadata-policy".equals(root.getName()) ? parse(root) : empty();
        } catch (Exception e) {
            return empty();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ── Schematron export (interop) ──────────────────────────────────────

    /**
     * Compile this policy to ISO Schematron (XSLT2 query binding) for portable
     * validation in any Schematron pipeline (CI, DITA-OT, oXygen). Rules are grouped
     * into a pattern per topic context (typed by root element name; untyped rules
     * use {@code /*}, the document root) so every check fires. Each rule becomes an
     * {@code <assert>} (required / allowed-values / pattern) or {@code <report>}
     * (forbidden); {@code recommended} carries {@code role="warning"}.
     */
    public String toSchematron() {
        java.util.LinkedHashMap<String, List<MetadataRule>> byContext =
                new java.util.LinkedHashMap<>();
        for (MetadataRule r : rules) {
            String ctx = (r.topicType() == null || r.topicType().isBlank())
                    ? "/*" : r.topicType();
            byContext.computeIfAbsent(ctx, k -> new ArrayList<>()).add(r);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" "
                + "queryBinding=\"xslt2\">\n");
        sb.append("  <title>DogsBay required-metadata policy</title>\n");
        for (var entry : byContext.entrySet()) {
            sb.append("  <pattern>\n");
            sb.append("    <rule context=\"").append(attr(entry.getKey())).append("\">\n");
            for (MetadataRule r : entry.getValue()) {
                appendRule(sb, r);
            }
            sb.append("    </rule>\n");
            sb.append("  </pattern>\n");
        }
        sb.append("</schema>\n");
        return sb.toString();
    }

    private static void appendRule(StringBuilder sb, MetadataRule r) {
        String el = r.field().element();
        String label = r.field().label(); // disambiguates e.g. <audience @job>
        String presence = presenceTest(r.field());
        switch (r.presence()) {
            case REQUIRED -> assertion(sb, presence, null,
                    "Required metadata " + label + " is missing");
            case RECOMMENDED -> assertion(sb, presence, "warning",
                    "Recommended metadata " + label + " is missing");
            case FORBIDDEN -> sb.append("      <report test=\"").append(attr(presence))
                    .append("\">Metadata ").append(esc(label))
                    .append(" is not allowed here</report>\n");
        }
        // Value/pattern constraints apply to simple text/attr values, not the
        // composite name=content/value of othermeta/data (whose evaluator value is
        // a "name=value" string) — keep the two engines in agreement.
        boolean simpleValue = r.field().kind() == MetadataField.ValueKind.TEXT
                || r.field().kind() == MetadataField.ValueKind.ATTR;
        String vexpr = valueExpr(r.field());
        if (simpleValue && !r.allowedValues().isEmpty()) {
            String list = r.allowedValues().stream()
                    .map(v -> "'" + v.replace("'", "''") + "'")
                    .reduce((a, b) -> a + "," + b).orElse("''");
            String test = "not(.//" + el + "[" + vexpr + " and not(" + vexpr + " = ("
                    + list + "))])";
            assertion(sb, test, null, label + " must be one of: "
                    + String.join(", ", r.allowedValues()));
        }
        if (simpleValue && r.pattern() != null && !r.pattern().isBlank()) {
            String pat = r.pattern().replace("'", "''");
            // Anchor to match Java's whole-string matches() semantics (XPath
            // matches() is otherwise a substring search).
            String test = "not(.//" + el + "[" + vexpr + " and not(matches(" + vexpr
                    + ",'^(" + pat + ")$'))])";
            assertion(sb, test, null, label + " must match " + r.pattern());
        }
    }

    private static void assertion(StringBuilder sb, String test, String role, String message) {
        sb.append("      <assert test=\"").append(attr(test)).append('"');
        if (role != null) {
            sb.append(" role=\"").append(role).append('"');
        }
        sb.append('>').append(esc(message)).append("</assert>\n");
    }

    /** XPath that is true when the field is present with a non-empty value. */
    private static String presenceTest(MetadataField f) {
        return switch (f.kind()) {
            case ATTR -> ".//" + f.element() + "/@" + f.attr();
            case TEXT -> ".//" + f.element() + "[normalize-space(.)!='']";
            case NAME_CONTENT, NAME_VALUE -> ".//" + f.element() + "/@name";
        };
    }

    /** XPath (relative to the field element) yielding its value, for value checks. */
    private static String valueExpr(MetadataField f) {
        return switch (f.kind()) {
            case ATTR -> "@" + f.attr();
            case TEXT -> "normalize-space(.)";
            case NAME_CONTENT, NAME_VALUE -> "@name";
        };
    }

    private static String attr(String s) {
        return esc(s).replace("\"", "&quot;");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
