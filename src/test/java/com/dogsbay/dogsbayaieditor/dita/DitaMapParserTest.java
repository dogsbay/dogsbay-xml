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

package com.dogsbay.dogsbayaieditor.dita;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Map Explorer tree: DITA 1.3 ditavalref branches surface as badged leaves. */
class DitaMapParserTest {

    @TempDir Path dir;

    private static List<DitaBranchNode> branchNodes(TreeNode node) {
        List<DitaBranchNode> found = new ArrayList<>();
        if (node instanceof DitaBranchNode b) {
            found.add(b);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            found.addAll(branchNodes(node.getChildAt(i)));
        }
        return found;
    }

    @Test
    void ditavalrefBranchesBecomeBadgedNodesUnderTheirTopicref() throws Exception {
        Path map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win</dvrResourcePrefix></ditavalmeta></ditavalref>
                <ditavalref href="mac.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>mac</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);

        DitaMapNode root = new DitaMapParser().parse(map.toFile());
        List<DitaBranchNode> branches = branchNodes(root);

        assertThat(branches).hasSize(2);
        assertThat(branches).extracting(DitaBranchNode::getTitle)
                .containsExactlyInAnyOrder("branch: win", "branch: mac");
        assertThat(branches).extracting(DitaBranchNode::getDitavalHref)
                .containsExactlyInAnyOrder("win.ditaval", "mac.ditaval");
    }

    @Test
    void branchLabelFallsBackToDitavalFileNameAndIgnoresNestedMeta() throws Exception {
        // The outer ditavalref has NO dvrResourcePrefix; a nested ditavalref carries one.
        // The outer label must come from its own DITAVAL file name, not the nested meta.
        Path map = dir.resolve("nested.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>N</title>
              <topicref href="install.dita">
                <ditavalref href="filters/win.ditaval">
                  <ditavalref href="filters/x.ditaval"><ditavalmeta>
                    <dvrResourcePrefix>INNER</dvrResourcePrefix></ditavalmeta></ditavalref>
                </ditavalref>
              </topicref>
            </map>
            """);
        List<DitaBranchNode> branches = branchNodes(new DitaMapParser().parse(map.toFile()));
        assertThat(branches).extracting(DitaBranchNode::getTitle)
                .containsExactly("branch: win.ditaval");   // file name, not "INNER"
    }

    @Test
    void aMapWithoutBranchesHasNoBranchNodes() throws Exception {
        Path map = dir.resolve("flat.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>F</title><topicref href="a.dita"/></map>
            """);
        assertThat(branchNodes(new DitaMapParser().parse(map.toFile()))).isEmpty();
    }
}
