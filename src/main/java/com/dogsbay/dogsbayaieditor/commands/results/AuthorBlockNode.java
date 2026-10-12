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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.BlockType;

/** One block of the Author view's tree, for CLI/MCP consumers. */
public record AuthorBlockNode(
        String id,
        String type,
        String category,
        String text,
        Map<String, String> attributes,
        List<AuthorBlockNode> children) {

    public static AuthorBlockNode fromBlock(AuthorBlock block) {
        List<AuthorBlockNode> children = new ArrayList<>();
        for (AuthorBlock child : block.getChildren()) {
            children.add(fromBlock(child));
        }
        String text = block.getType().getCategory() == BlockType.Category.RAW
                ? block.getRawXml()
                : block.getPlainText();
        return new AuthorBlockNode(
                block.getId(),
                block.getType().getName(),
                block.getType().getCategory().name(),
                text.isEmpty() ? null : text,
                block.getAttributes().isEmpty() ? null : block.getAttributes(),
                children.isEmpty() ? null : children);
    }
}
