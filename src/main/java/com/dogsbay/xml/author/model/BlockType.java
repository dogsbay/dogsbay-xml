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

package com.dogsbay.xml.author.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Describes one kind of block ("flavour"): which XML element it maps to, how
 * it behaves in the editor, and which child block types it may contain.
 *
 * <p>Instances are immutable and live in a {@link BlockTypeRegistry}. The DITA
 * profile is built in {@link BlockTypeRegistry#ditaProfile()}; general-XML mode
 * derives registries from the document's grammar instead.
 */
public final class BlockType {

    /** Reserved type name for the raw-XML fallback block. */
    public static final String RAW_NAME = "#raw";

    public enum Category {
        /** Carries editable inline text (may additionally have child blocks, e.g. li). */
        TEXT,
        /** Structural only — children, no direct text (e.g. steps, ul, conbody). */
        CONTAINER,
        /** No text and no children (e.g. image). */
        VOID,
        /** Opaque unparsed XML — the lossless fallback for unsupported markup. */
        RAW
    }

    private final String name;
    private final Category category;
    private final String label;
    private final Set<String> allowedChildren;
    private final ContentModel contentModel;
    private final boolean preserveSpace;
    private final boolean splitOnEnter;
    private final boolean deletable;

    private BlockType(Builder b) {
        this.name = b.name;
        this.category = b.category;
        this.label = b.label;
        // keep declaration order — it drives insert-menu ordering (Set.copyOf would shuffle)
        LinkedHashSet<String> children = new LinkedHashSet<>(b.allowedChildren);
        if (b.contentModel != null) {
            children.addAll(b.contentModel.allNames());   // the model is the authority; membership follows
        }
        this.allowedChildren = java.util.Collections.unmodifiableSet(children);
        this.contentModel = b.contentModel;
        this.preserveSpace = b.preserveSpace;
        this.splitOnEnter = b.splitOnEnter;
        this.deletable = b.deletable;
    }

    /** XML element name this block maps to (or {@link #RAW_NAME}). */
    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    /** Human-readable label shown in gutters and insert menus. */
    public String getLabel() {
        return label;
    }

    /** Names of block types this block accepts as children. */
    /** The ordered content model, or null when any order and count of the allowed children is fine. */
    public ContentModel getContentModel() {
        return contentModel;
    }

    /**
     * Whether {@code childNames} is an acceptable child sequence: membership
     * always, and order and upper bounds when the type has a content model.
     */
    public boolean acceptsChildren(java.util.List<String> childNames) {
        for (String n : childNames) {
            if (!BlockType.RAW_NAME.equals(n) && !allowedChildren.contains(n)) {
                return false;
            }
        }
        if (contentModel == null) {
            return true;
        }
        java.util.List<String> known = new java.util.ArrayList<>(childNames.size());
        for (String n : childNames) {
            if (!BlockType.RAW_NAME.equals(n)) {
                known.add(n);   // verbatim chips sit anywhere
            }
        }
        return contentModel.accepts(known);
    }

    public Set<String> getAllowedChildren() {
        return allowedChildren;
    }

    /** Whitespace is significant ({@code codeblock}, {@code pre}). */
    public boolean isPreserveSpace() {
        return preserveSpace;
    }

    /** Enter splits the block into a sibling; otherwise Enter moves focus. */
    public boolean isSplitOnEnter() {
        return splitOnEnter;
    }

    /** False for blocks that must never be deleted by keyboard ops (table cells). */
    public boolean isDeletable() {
        return deletable;
    }

    public boolean hasText() {
        return category == Category.TEXT || category == Category.RAW;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Builder builder(String name, Category category, String label) {
        return new Builder(name, category, label);
    }

    public static final class Builder {
        private final String name;
        private final Category category;
        private final String label;
        private final Set<String> allowedChildren = new LinkedHashSet<>();
        private ContentModel contentModel;
        private boolean preserveSpace;
        private boolean splitOnEnter;
        private boolean deletable = true;

        private Builder(String name, Category category, String label) {
            this.name = name;
            this.category = category;
            this.label = label;
        }

        public Builder children(String... names) {
            for (String n : names) {
                allowedChildren.add(n);
            }
            return this;
        }

        /** The ordered content model; its types are also allowed children. */
        public Builder model(ContentModel.Slot... slots) {
            this.contentModel = ContentModel.of(slots);
            return this;
        }

        public Builder preserveSpace() {
            this.preserveSpace = true;
            return this;
        }

        public Builder splitOnEnter() {
            this.splitOnEnter = true;
            return this;
        }

        public Builder notDeletable() {
            this.deletable = false;
            return this;
        }

        public BlockType build() {
            return new BlockType(this);
        }
    }
}
