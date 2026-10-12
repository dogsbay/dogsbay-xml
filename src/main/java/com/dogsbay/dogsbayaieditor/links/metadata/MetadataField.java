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

/**
 * The prolog / topicmeta fields DogsBay authors, audits, and governs — aligned with
 * the metadata Fluid Topics maps from DITA prolog. Each field has a stable
 * {@code key} (its public identifier, used in policy rules and findings), the DITA
 * element it reads, and how its value is read (element text, a specific attribute,
 * or a {@code name}/{@code content} pair).
 *
 * <p>Several DITA elements carry more than one governable value on distinct
 * attributes ({@code audience} type/job/experiencelevel, {@code created}/{@code
 * revised} date/golive/expiry, {@code vrm} version/release/modification) — these are
 * separate fields sharing an {@link #element()} but with distinct {@link #key()}s,
 * so lookups key by {@code key} and extraction collects every field for an element.
 */
public enum MetadataField {

    AUTHOR("author", "author", ValueKind.TEXT),
    SOURCE("source", "source", ValueKind.TEXT),
    PUBLISHER("publisher", "publisher", ValueKind.TEXT),
    COPYRYEAR("copyryear", "copyryear", ValueKind.ATTR, "year"),
    COPYRHOLDER("copyrholder", "copyrholder", ValueKind.TEXT),
    CREATED("created", "created", ValueKind.ATTR, "date"),
    CREATED_GOLIVE("created-golive", "created", ValueKind.ATTR, "golive"),
    CREATED_EXPIRY("created-expiry", "created", ValueKind.ATTR, "expiry"),
    REVISED("revised", "revised", ValueKind.ATTR, "modified"),
    REVISED_GOLIVE("revised-golive", "revised", ValueKind.ATTR, "golive"),
    REVISED_EXPIRY("revised-expiry", "revised", ValueKind.ATTR, "expiry"),
    PERMISSIONS("permissions", "permissions", ValueKind.ATTR, "view"),
    AUDIENCE("audience", "audience", ValueKind.ATTR, "type"),
    AUDIENCE_JOB("audience-job", "audience", ValueKind.ATTR, "job"),
    AUDIENCE_EXPERIENCELEVEL("audience-experiencelevel", "audience", ValueKind.ATTR,
            "experiencelevel"),
    CATEGORY("category", "category", ValueKind.TEXT),
    KEYWORD("keyword", "keyword", ValueKind.TEXT),
    INDEXTERM("indexterm", "indexterm", ValueKind.TEXT),
    PRODNAME("prodname", "prodname", ValueKind.TEXT),
    VRM_VERSION("vrm-version", "vrm", ValueKind.ATTR, "version"),
    VRM_RELEASE("vrm-release", "vrm", ValueKind.ATTR, "release"),
    VRM_MODIFICATION("vrm-modification", "vrm", ValueKind.ATTR, "modification"),
    BRAND("brand", "brand", ValueKind.TEXT),
    COMPONENT("component", "component", ValueKind.TEXT),
    FEATNUM("featnum", "featnum", ValueKind.TEXT),
    PLATFORM("platform", "platform", ValueKind.TEXT),
    PROGNUM("prognum", "prognum", ValueKind.TEXT),
    SERIES("series", "series", ValueKind.TEXT),
    RESOURCEID("resourceid", "resourceid", ValueKind.ATTR, "appid"),
    OTHERMETA("othermeta", "othermeta", ValueKind.NAME_CONTENT),
    DATA("data", "data", ValueKind.NAME_VALUE);

    /** How a field's value is read off its element. */
    public enum ValueKind { TEXT, ATTR, NAME_CONTENT, NAME_VALUE }

    private final String key;
    private final String element;
    private final ValueKind kind;
    private final String attr;

    MetadataField(String key, String element, ValueKind kind) {
        this(key, element, kind, null);
    }

    MetadataField(String key, String element, ValueKind kind, String attr) {
        this.key = key;
        this.element = element;
        this.kind = kind;
        this.attr = attr;
    }

    /** The stable public id (policy {@code field=}, findings). Equals the element
     *  name for single-value elements; suffixed (e.g. {@code audience-job}) otherwise. */
    public String key() {
        return key;
    }

    /** The DITA element's local name (e.g. {@code author}, {@code audience}). */
    public String element() {
        return element;
    }

    public ValueKind kind() {
        return kind;
    }

    /** The value-bearing attribute for {@link ValueKind#ATTR} fields, else null. */
    public String attr() {
        return attr;
    }

    /** A human label, e.g. {@code <audience @job>} or {@code <author>}. */
    public String label() {
        return attr == null ? "<" + element + ">" : "<" + element + " @" + attr + ">";
    }

    /** The field with this {@link #key()}, or null. */
    public static MetadataField byKey(String key) {
        for (MetadataField f : values()) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    /** Every field read off the element with this local name (may be several). */
    public static List<MetadataField> fieldsForElement(String localName) {
        List<MetadataField> out = new ArrayList<>();
        for (MetadataField f : values()) {
            if (f.element.equals(localName)) {
                out.add(f);
            }
        }
        return out;
    }
}
