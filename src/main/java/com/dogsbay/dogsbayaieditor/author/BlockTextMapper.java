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
package com.dogsbay.dogsbayaieditor.author;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.BlockType;

/**
 * Translates a place in the block model to the same place in the XML text and
 * back, so switching between the Author and XML views keeps the writer where
 * they were.
 *
 * <p>The two trees cannot be compared by identity: the Author view holds its
 * own block tree, and the XML view holds a parsed element tree that is rebuilt
 * on every save. What they share is shape, so a place is addressed as a path
 * of {@code name[n]} steps from the root, {@code n} counting only siblings of
 * the same name.
 *
 * <p>The shapes differ in one way that matters. Inline elements (a
 * {@code <b>} inside a paragraph) are runs of text in the block model, not
 * blocks, so they appear in the element tree and not in the block tree.
 * Counting per name rather than per position absorbs that, as long as an
 * inline element and its containing block do not share a name. Two DITA
 * elements are used both ways, {@code draft-comment} and
 * {@code required-cleanup}: a path stops above one of those rather than
 * counting occurrences the two trees number differently.
 *
 * <p>A path that cannot be followed to the end resolves to the deepest step
 * that could be, which lands the caret in the right neighbourhood rather than
 * nowhere. Reaching no step at all is a failure, not a neighbourhood, and
 * {@link #targetFor} and {@link #resolveOrNull} report it as one.
 */
public final class BlockTextMapper {

    /** One step of a path: an element name and its 1-based index among same-named siblings. */
    public record Step(String name, int index) {
    }

    /**
     * Elements DITA allows both as a block and inside text. The block tree
     * holds only the block occurrences and the element tree holds both, so the
     * two number them differently and a path cannot name one reliably.
     */
    private static final java.util.Set<String> INLINE_AND_BLOCK =
            java.util.Set.of("draft-comment", "required-cleanup");

    private BlockTextMapper() {
    }

    /** The path truncated above the first step that names a both-ways element. */
    private static List<Step> unambiguous(List<Step> path) {
        for (int i = 0; i < path.size(); i++) {
            if (INLINE_AND_BLOCK.contains(path.get(i).name())) {
                return new ArrayList<>(path.subList(0, i));
            }
        }
        return path;
    }

    /**
     * The path from the document root to {@code block}, empty when the block
     * is the root itself and null when there is no block.
     */
    public static List<Step> pathOf(AuthorBlock block) {
        if (block == null) {
            return null;
        }
        List<Step> path = new ArrayList<>();
        for (AuthorBlock b = block; b.getParent() != null; b = b.getParent()) {
            String name = nameOf(b);
            int index = 1;
            for (AuthorBlock sibling : b.getParent().getChildren()) {
                if (sibling == b) {
                    break;
                }
                if (name.equals(nameOf(sibling))) {
                    index++;
                }
            }
            path.add(0, new Step(name, index));
        }
        return unambiguous(path);
    }

    /** The path from the document root to {@code element}, empty for the root itself. */
    public static List<Step> pathOf(Element element) {
        if (element == null) {
            return null;
        }
        List<Step> path = new ArrayList<>();
        for (Element e = element; e.getParent() != null; e = e.getParent()) {
            String name = e.getQualifiedName();
            int index = 1;
            for (Object siblingObject : e.getParent().elements()) {
                Element sibling = (Element) siblingObject;
                if (sibling == e) {
                    break;
                }
                if (name.equals(sibling.getQualifiedName())) {
                    index++;
                }
            }
            path.add(0, new Step(name, index));
        }
        return unambiguous(path);
    }

    /**
     * Follows {@code path} down the element tree, returning the deepest
     * element it reaches (the root when the first step already fails).
     */
    public static Element resolve(Element root, List<Step> path) {
        Element at = root;
        if (root == null || path == null) {
            return at;
        }
        for (Step step : path) {
            Element next = null;
            int seen = 0;
            for (Object childObject : at.elements()) {
                Element child = (Element) childObject;
                if (step.name().equals(child.getQualifiedName()) && ++seen == step.index()) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return at;
            }
            at = next;
        }
        return at;
    }

    /**
     * Follows {@code path} down the block tree, returning the deepest block it
     * reaches (the root when the first step already fails).
     */
    public static AuthorBlock resolve(AuthorBlock root, List<Step> path) {
        AuthorBlock at = root;
        if (root == null || path == null) {
            return at;
        }
        for (Step step : path) {
            AuthorBlock next = null;
            int seen = 0;
            for (AuthorBlock child : at.getChildren()) {
                if (step.name().equals(nameOf(child)) && ++seen == step.index()) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return at;
            }
            at = next;
        }
        return at;
    }

    /**
     * {@link #resolve(Element, List)}, but null when the path reached no step
     * at all. Landing on the root is a failure dressed as an answer: it would
     * send a caret to the top of the file rather than leave it alone.
     */
    public static Element resolveOrNull(Element root, List<Step> path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        Element found = resolve(root, path);
        return found == root ? null : found;
    }

    /**
     * The block a caret at {@code path} should move the Author view to, or
     * null to leave the view where it is.
     *
     * <p>Two cases are better left alone than followed. A caret outside every
     * element, or in the whitespace between blocks, resolves to a container
     * (often the topic body); if the view is already somewhere inside that
     * container it holds a more useful place than the container itself, and
     * moving would scroll the writer away from where they were. The root is
     * never a useful target.
     */
    public static AuthorBlock targetFor(AuthorBlock root, List<Step> path, AuthorBlock currentSelection) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        AuthorBlock block = resolve(root, path);
        if (block == null || block == root) {
            return null;
        }
        if (currentSelection != null && currentSelection != block && block.isAncestorOf(currentSelection)) {
            return null;
        }
        return block;
    }

    /**
     * The XML element name a block stands for. A raw chip's type is the
     * placeholder name shared by every chip, so its name comes from the markup
     * it holds.
     */
    static String nameOf(AuthorBlock block) {
        if (block.getType().getCategory() != BlockType.Category.RAW) {
            return block.getType().getName();
        }
        String xml = block.getRawXml();
        if (xml == null) {
            return block.getType().getName();
        }
        int start = xml.indexOf('<');
        if (start < 0) {
            return block.getType().getName();
        }
        int end = start + 1;
        while (end < xml.length() && !Character.isWhitespace(xml.charAt(end))
                && xml.charAt(end) != '>' && xml.charAt(end) != '/') {
            end++;
        }
        return end > start + 1 ? xml.substring(start + 1, end) : block.getType().getName();
    }
}
