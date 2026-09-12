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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One block in the author document tree: an element with attributes, child
 * blocks, and (for text-bearing types) a list of styled inline runs.
 *
 * <p>Reading is unrestricted. Structural mutation (attach/detach/move) must go
 * through {@link AuthorDocument} so the id index and listeners stay correct,
 * and all user-driven edits must go through {@link Transaction} so they are
 * undoable. The public setters here exist for document construction by
 * adapters; the UI must never call them directly.
 */
public final class AuthorBlock {

    private final String id;
    private final BlockType type;
    private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    private final List<AuthorBlock> children = new ArrayList<>();
    private AuthorBlock parent;
    private List<InlineRun> text = List.of();
    private String rawXml = "";
    private List<XmlAuxNode> leadingAux = List.of();
    private List<XmlAuxNode> trailingAux = List.of();

    AuthorBlock(String id, BlockType type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public BlockType getType() {
        return type;
    }

    public AuthorBlock getParent() {
        return parent;
    }

    void setParent(AuthorBlock parent) {
        this.parent = parent;
    }

    public List<AuthorBlock> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** Mutable child list — package-private, used only by AuthorDocument. */
    List<AuthorBlock> children() {
        return children;
    }

    public int indexInParent() {
        return parent == null ? -1 : parent.children.indexOf(this);
    }

    /** Ancestor chain from root to this block, inclusive. */
    public List<AuthorBlock> path() {
        List<AuthorBlock> path = new ArrayList<>();
        for (AuthorBlock b = this; b != null; b = b.parent) {
            path.add(b);
        }
        Collections.reverse(path);
        return path;
    }

    public boolean isAncestorOf(AuthorBlock other) {
        for (AuthorBlock b = other; b != null; b = b.parent) {
            if (b == this) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Attributes (preserved verbatim for lossless round-trip)
    // ------------------------------------------------------------------

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String getAttribute(String name) {
        return attributes.get(name);
    }

    /** The {@code xml:space} attribute, as imported from XML. */
    public static final String XML_SPACE = "xml:space";

    /**
     * Whether whitespace in this block's text is significant: the block type
     * says so (codeblock, pre), or the nearest {@code xml:space} on the block
     * or an ancestor says {@code preserve}.
     */
    public boolean preservesSpace() {
        if (type.isPreserveSpace()) {
            return true;
        }
        for (AuthorBlock b = this; b != null; b = b.parent) {
            String space = b.attributes.get(XML_SPACE);
            if (space != null) {
                return "preserve".equals(space);
            }
        }
        return false;
    }

    public void setAttribute(String name, String value) {
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** Styled inline runs; empty for non-text blocks. */
    public List<InlineRun> getText() {
        return text;
    }

    public void setText(List<InlineRun> runs) {
        if (!type.hasText() && !runs.isEmpty()) {
            throw new AuthorStructureException(
                    "block type '" + type.getName() + "' does not carry text");
        }
        this.text = List.copyOf(runs);
    }

    /** Concatenated plain text of all runs. */
    public String getPlainText() {
        StringBuilder sb = new StringBuilder();
        for (InlineRun run : text) {
            sb.append(run.text());
        }
        return sb.toString();
    }

    /** Comments/PIs that appeared immediately before this block. */
    public List<XmlAuxNode> getLeadingAux() {
        return leadingAux;
    }

    public void setLeadingAux(List<XmlAuxNode> aux) {
        this.leadingAux = List.copyOf(aux);
    }

    /** Comments/PIs that appeared after the last child inside this block. */
    public List<XmlAuxNode> getTrailingAux() {
        return trailingAux;
    }

    public void setTrailingAux(List<XmlAuxNode> aux) {
        this.trailingAux = List.copyOf(aux);
    }

    /** Verbatim source of a {@link BlockType.Category#RAW} block. */
    public String getRawXml() {
        return rawXml;
    }

    public void setRawXml(String rawXml) {
        this.rawXml = rawXml == null ? "" : rawXml;
    }

    // ------------------------------------------------------------------
    // Debug / test support
    // ------------------------------------------------------------------

    /**
     * Deterministic structural dump (type, attributes, runs, children), used by
     * tests for deep-equality and by debugging tooling. Block ids are excluded
     * so structurally identical trees compare equal.
     */
    public String toStructureString() {
        StringBuilder sb = new StringBuilder();
        dump(sb, 0);
        return sb.toString();
    }

    private void dump(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth)).append(type.getName());
        if (!attributes.isEmpty()) {
            sb.append(' ').append(attributes);
        }
        if (!leadingAux.isEmpty()) {
            sb.append(" lead=").append(leadingAux);
        }
        if (!trailingAux.isEmpty()) {
            sb.append(" trail=").append(trailingAux);
        }
        if (type.getCategory() == BlockType.Category.RAW) {
            sb.append(" raw=").append(rawXml);
        } else if (!text.isEmpty()) {
            sb.append(" |");
            for (InlineRun run : text) {
                sb.append('[').append(run.text());
                if (!run.attrs().isEmpty()) {
                    sb.append('{').append(new java.util.TreeMap<>(run.attrs())).append('}');
                }
                sb.append(']');
            }
        }
        sb.append('\n');
        for (AuthorBlock child : children) {
            child.dump(sb, depth + 1);
        }
    }

    @Override
    public String toString() {
        return type.getName() + "#" + id;
    }
}
