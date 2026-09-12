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

package com.dogsbay.xml.author.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.dom4j.Element;
import org.dom4j.Text;

import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.BlockTypeRegistry;

/**
 * Builds {@link BlockTypeRegistry} instances for general XML mode.
 *
 * <p>{@link #fromSchema} walks the grammar's element closure: content models
 * drive the allowed-children sets, {@code isEmpty} marks void elements, mixed
 * or simple types become text blocks. {@link #fromDocument} is the no-schema
 * fallback: classification and child sets are inferred from the document
 * itself (the insert menu then offers what the document already uses).
 */
public final class GenericRegistryBuilder {

    private GenericRegistryBuilder() {
    }

    // ------------------------------------------------------------------
    // From grammar
    // ------------------------------------------------------------------



    // ------------------------------------------------------------------
    // From the document itself
    // ------------------------------------------------------------------

    public static BlockTypeRegistry fromDocument(Element root) {
        Map<String, Shape> shapes = new LinkedHashMap<>();
        observe(root, shapes);
        List<BlockType> types = new ArrayList<>();
        for (Map.Entry<String, Shape> entry : shapes.entrySet()) {
            Shape shape = entry.getValue();
            BlockType.Category category = shape.sawChildren && !shape.sawText
                    ? BlockType.Category.CONTAINER
                    : BlockType.Category.TEXT;
            types.add(BlockType.builder(entry.getKey(), category, entry.getKey())
                    .children(shape.childNames.toArray(new String[0]))
                    .build());
        }
        return new BlockTypeRegistry(types);
    }

    private static void observe(Element element, Map<String, Shape> shapes) {
        Shape shape = shapes.computeIfAbsent(element.getQualifiedName(), n -> new Shape());
        for (Object node : element.content()) {
            if (node instanceof Text text && !text.getText().isBlank()) {
                shape.sawText = true;
            } else if (node instanceof Element child) {
                shape.sawChildren = true;
                shape.childNames.add(child.getQualifiedName());
                observe(child, shapes);
            }
        }
    }

    private static final class Shape {
        boolean sawText;
        boolean sawChildren;
        final Set<String> childNames = new LinkedHashSet<>();
    }
}
