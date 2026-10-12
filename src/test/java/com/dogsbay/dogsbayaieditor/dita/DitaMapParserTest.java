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

/** Map Explorer tree: DITA 1.3 ditavalref branches surface as badged leaves; key references and titles resolve. */
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

    /** The tree as indented "Kind: label" lines, for readable assertions. */
    private static String outline(TreeNode node, int depth) {
        StringBuilder sb = new StringBuilder("  ".repeat(depth))
                .append(node.getClass().getSimpleName().replace("Dita", "").replace("Node", ""))
                .append(": ").append(node).append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(outline(node.getChildAt(i), depth + 1));
        }
        return sb.toString();
    }

    private static DitaNode find(TreeNode node, String label) {
        if (String.valueOf(node).equals(label)) {
            return (DitaNode) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DitaNode found = find(node.getChildAt(i), label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void topicrefsByKeyResolveToTheirTopicsAndTitles() throws Exception {
        // install.dita's title is written with a key of its own — "Installing <keyword keyref="product"/> &
        // friends" — which is ordinary DITA, and the label has to resolve it. Dropping the element left
        // "Installing & friends" here and "What is ?" for a topic whose key sits mid-sentence (reported
        // 2026-09-30).

        Files.createDirectories(dir.resolve("topics"));
        Files.writeString(dir.resolve("topics/install.dita"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
            <task id="install"><title>Installing <keyword keyref="product"/> &amp; friends</title><taskbody/></task>
            """);
        Files.writeString(dir.resolve("topics/build.dita"), "<topic id=\"b\"><title>First build</title></topic>");
        Files.writeString(dir.resolve("topics/usage.ditamap"), """
            <map><title>Usage</title>
              <topicref keyref="build"/>
            </map>
            """);
        Files.writeString(dir.resolve("keys.ditamap"), """
            <map><title>Keys</title>
              <keydef keys="product"><topicmeta><keywords><keyword>Audacity</keyword></keywords></topicmeta></keydef>
              <keydef keys="install" href="topics/install.dita"/>
              <keydef keys="build" href="topics/build.dita#b"/>
              <keydef keys="usage" href="topics/usage.ditamap" format="ditamap"/>
              <keydef keys="labeled" href="topics/build.dita"><topicmeta><linktext>Build again</linktext></topicmeta></keydef>
            </map>
            """);
        Path map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <map><title>Guide</title>
              <topicref keyref="install"/>
              <topicref keyref="install" navtitle="Setup"/>
              <topicref keyref="labeled"/>
              <topicref keyref="usage" format="ditamap"/>
              <topicref keyref="missing"/>
              <topicgroup>
                <topicref href="topics/build.dita#b"/>
              </topicgroup>
              <topicgroup processing-role="resource-only">
                <mapref href="keys.ditamap"/>
              </topicgroup>
            </map>
            """);

        DitaMapNode root = new DitaMapParser().parse(map.toFile());

        assertThat(outline(root, 0)).isEqualTo("""
            Map: Guide
              Topic: Installing Audacity & friends
              Topic: Setup
              Topic: Build again
              Map: Usage
                Topic: First build
              Topic: missing (unresolved key: missing)
              Topic: First build
              Group: (resource-only)
                Map: Keys
            """);
        DitaNode install = find(root, "Installing Audacity & friends");
        // Canonical on both sides: on macOS @TempDir sits under /var/folders, which is a
        // symlink to /private/var, and the parser reports the resolved real path. Comparing
        // the two as written made this the one test in the suite that failed on a Mac.
        assertThat(install.getResolvedFile().getCanonicalFile())
                .isEqualTo(dir.resolve("topics/install.dita").toFile().getCanonicalFile());
        assertThat(install).isInstanceOf(DitaTopicNode.class);
    }


    @Test
    void aTopicTitleWhoseKeyDoesNotResolveFallsBackToSomethingHonest() throws Exception {
        // A title with a hole in it names nothing, so the tree shows the key the topicref used instead
        Files.writeString(dir.resolve("what.dita"),
                "<concept id=\"w\"><title>What is <keyword keyref=\"absent\"/>?</title></concept>");
        Path map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <map><title>Guide</title>
              <keydef keys="what" href="what.dita"/>
              <topicref keyref="what"/>
            </map>
            """);

        DitaMapNode root = new DitaMapParser().parse(map.toFile());

        assertThat(outline(root, 0)).contains("Topic: what").doesNotContain("What is ?");
    }

    @Test
    void keyReferencesResolveInTheirKeyScope() throws Exception {
        Files.writeString(dir.resolve("a.dita"), "<topic id=\"a\"><title>Root A</title></topic>");
        Files.writeString(dir.resolve("b.dita"), "<topic id=\"b\"><title>Scoped B</title></topic>");
        Path map = dir.resolve("scoped.ditamap");
        Files.writeString(map, """
            <map><title>Scoped</title>
              <keydef keys="t" href="a.dita"/>
              <topicgroup keyscope="inner">
                <keydef keys="t" href="b.dita"/>
                <topicref keyref="t"/>
              </topicgroup>
              <topicref keyref="t"/>
            </map>
            """);

        String outline = outline(new DitaMapParser().parse(map.toFile()), 0);

        assertThat(outline).isEqualTo("""
            Map: Scoped
              Topic: Scoped B
              Topic: Root A
            """);
    }
}
