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

package com.dogsbay.dogsbayaieditor.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.samples.SampleProject;
import com.fasterxml.jackson.databind.ObjectMapper;

/** {@link ProjectGraphBuilder}: edge kinds, ships sets, scopes and structural issues. */
class ProjectGraphBuilderTest {

    @TempDir Path tmp;

    private static final String MAP_DT =
            "<?xml version=\"1.0\"?>\n<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n";

    private void write(String rel, String content) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private void topic(String rel, String title, String body) throws IOException {
        write(rel, "<?xml version=\"1.0\"?>\n<concept id=\"c\">\n<title>" + title
                + "</title>\n<conbody>" + body + "</conbody>\n</concept>\n");
    }

    private static List<GraphEdge> edges(ProjectGraph g, String kind) {
        return g.edges().stream().filter(e -> e.kind().equals(kind)).toList();
    }

    private static GraphNode node(ProjectGraph g, String id) {
        return g.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    private static List<GraphIssue> issues(ProjectGraph g, String rule) {
        return g.issues().stream().filter(i -> i.rule().equals(rule)).toList();
    }

    private static GraphDeliverable deliverable(ProjectGraph g, String name) {
        return g.deliverables().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    // ---- small fixtures -----------------------------------------------------

    @Test
    void topicrefAndReltableAreDistinct_andIdsAreProjectRelative() throws Exception {
        topic("topics/a.dita", "Alpha", "");
        topic("topics/b.dita", "Beta", "");
        topic("topics/c.dita", "Gamma", "");
        write("main.ditamap", MAP_DT + "<map>\n<title>Main</title>\n"
                + "<keydef keys=\"kc\" href=\"topics/c.dita\"/>\n"
                + "<topicref href=\"topics/a.dita\"/>\n"
                + "<reltable>\n<relrow>\n"
                + "<relcell><topicref href=\"topics/a.dita\"/></relcell>\n"
                + "<relcell><topicref href=\"topics/b.dita\"/></relcell>\n"
                + "<relcell><topicref keyref=\"kc\"/></relcell>\n"
                + "</relrow>\n</reltable>\n</map>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(g.scope()).isEqualTo("all");
        assertThat(edges(g, "topicref")).extracting(GraphEdge::from, GraphEdge::to, GraphEdge::line)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("main.ditamap", "topics/a.dita", 6));
        assertThat(edges(g, "reltable")).extracting(GraphEdge::to)
                .containsExactlyInAnyOrder("topics/a.dita", "topics/b.dita", "topics/c.dita");
        assertThat(node(g, "topics/a.dita").title()).isEqualTo("Alpha");
        assertThat(node(g, "topics/a.dita").type()).isEqualTo("concept");
        assertThat(node(g, "main.ditamap").type()).isEqualTo("map");
        assertThat(node(g, "main.ditamap").title()).isEqualTo("Main");
        assertThat(g.nodes()).extracting(GraphNode::id).allSatisfy(id -> assertThat(id).doesNotContain("\\"));

        // No project file: the unreferenced map is a synthetic deliverable.
        GraphDeliverable main = deliverable(g, "main");
        assertThat(main.ships()).containsExactlyInAnyOrder("main.ditamap", "topics/a.dita");
        assertThat(node(g, "topics/b.dita").ships()).isNull();
        // A reltable-only keyref neither ships its target nor marks it orphan.
        assertThat(node(g, "topics/c.dita").ships()).isNull();
        assertThat(issues(g, "orphan-topic")).isEmpty();
    }

    @Test
    void keytargetCarriesVia_andTwoBindingsGiveTwoEdges() throws Exception {
        topic("t1.dita", "One", "");
        topic("t2.dita", "Two", "<p><xref keyref=\"start\"/></p>");
        write("one.ditamap", MAP_DT + "<map>\n<keydef keys=\"start\" href=\"t1.dita\"/>\n"
                + "<topicref href=\"t2.dita\"/>\n</map>\n");
        write("two.ditamap", MAP_DT + "<map>\n<keydef keys=\"start\" href=\"t2.dita\"/>\n"
                + "<topicref href=\"t2.dita\"/>\n</map>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(edges(g, "keytarget")).extracting(GraphEdge::from, GraphEdge::to, GraphEdge::via)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("key:start", "t1.dita", "one.ditamap"),
                        org.assertj.core.groups.Tuple.tuple("key:start", "t2.dita", "two.ditamap"));
        assertThat(edges(g, "keydef")).extracting(GraphEdge::from, GraphEdge::to, GraphEdge::line)
                .contains(org.assertj.core.groups.Tuple.tuple("one.ditamap", "key:start", 4));
        assertThat(edges(g, "keyref")).extracting(GraphEdge::from, GraphEdge::to)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("t2.dita", "key:start"));
        assertThat(node(g, "key:start").kind()).isEqualTo("key");
        // An xref keyref does not ship its target.
        assertThat(deliverable(g, "one").ships()).containsExactlyInAnyOrder("one.ditamap", "t2.dita");
    }

    @Test
    void shadowedKeyWithinOneKeySpace() throws Exception {
        topic("t.dita", "T", "<p><ph keyref=\"v\"/></p>");
        write("sub.ditamap", MAP_DT + "<map>\n<keydef keys=\"v\"><topicmeta><keywords><keyword>1"
                + "</keyword></keywords></topicmeta></keydef>\n</map>\n");
        write("root.ditamap", MAP_DT + "<map>\n<keydef keys=\"v\"><topicmeta><keywords><keyword>2"
                + "</keyword></keywords></topicmeta></keydef>\n"
                + "<mapref href=\"sub.ditamap\"/>\n<topicref href=\"t.dita\"/>\n</map>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(issues(g, "shadowed-key")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("warning");
            assertThat(i.file()).isEqualTo("sub.ditamap");
            assertThat(i.line()).isEqualTo(4);
            assertThat(i.message()).contains("root.ditamap");
        });
        assertThat(edges(g, "mapref")).extracting(GraphEdge::to).containsExactly("sub.ditamap");
        assertThat(issues(g, "undefined-key")).isEmpty();
        assertThat(issues(g, "unused-key")).isEmpty();
    }

    @Test
    void keyResolvingInOneDeliverableOnly_isInconsistent() throws Exception {
        topic("shared.dita", "Shared", "<p><ph keyref=\"gl\"/> <ph keyref=\"typo\"/></p>");
        write("keys.ditamap", MAP_DT + "<map>\n<keydef keys=\"gl\"><topicmeta><keywords><keyword>G"
                + "</keyword></keywords></topicmeta></keydef>\n</map>\n");
        write("full.ditamap", MAP_DT + "<map>\n<mapref href=\"keys.ditamap\"/>\n"
                + "<topicref href=\"shared.dita\"/>\n</map>\n");
        write("lite.ditamap", MAP_DT + "<map>\n<topicref href=\"shared.dita\"/>\n</map>\n");
        write("project.json", """
                {"deliverables": [
                  {"name": "full", "context": {"input": "full.ditamap"}},
                  {"name": "lite", "context": {"input": "lite.ditamap",
                     "profiles": {"ditavals": ["f.ditaval"]}}}
                ]}
                """);
        write("f.ditaval", "<val/>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(issues(g, "key-resolves-inconsistently")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("warning");
            assertThat(i.file()).isEqualTo("shared.dita");
            assertThat(i.message()).isEqualTo("Key \"gl\" resolves in full, fails in lite");
        });
        assertThat(issues(g, "undefined-key")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("error");
            assertThat(i.message()).isEqualTo("Key \"typo\" is undefined in full, lite");
        });
        assertThat(edges(g, "profile")).singleElement().satisfies(e -> {
            assertThat(e.from()).isEqualTo("lite.ditamap");
            assertThat(e.to()).isEqualTo("f.ditaval");
            assertThat(e.deliverable()).isEqualTo("lite");
        });
        assertThat(node(g, "f.ditaval").kind()).isEqualTo("ditaval");
        assertThat(deliverable(g, "lite").ditavals()).containsExactly("f.ditaval");
        assertThat(node(g, "shared.dita").ships()).containsExactly("full", "lite");
    }

    @Test
    void brokenReferenceGivesMissingNodeAndBrokenEdge_xrefDoesNotShip() throws Exception {
        topic("a.dita", "A", "<p><xref href=\"b.dita\"/> <xref href=\"#c/x\"/>"
                + " <xref href=\"https://example.com\" scope=\"external\"/></p>");
        topic("b.dita", "B", "");
        write("m.ditamap", MAP_DT + "<map>\n<topicref href=\"a.dita\"/>\n"
                + "<topicref href=\"gone/missing.dita\"/>\n</map>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(node(g, "gone/missing.dita").missing()).isTrue();
        assertThat(node(g, "a.dita").missing()).isNull();
        assertThat(edges(g, "topicref")).filteredOn(e -> e.to().equals("gone/missing.dita"))
                .singleElement().satisfies(e -> assertThat(e.broken()).isTrue());
        assertThat(issues(g, "broken-reference")).singleElement().satisfies(i -> {
            assertThat(i.file()).isEqualTo("m.ditamap");
            assertThat(i.line()).isEqualTo(5);
        });
        assertThat(edges(g, "link")).extracting(GraphEdge::from, GraphEdge::to)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("a.dita", "b.dita"));
        assertThat(deliverable(g, "m").ships()).containsExactlyInAnyOrder("m.ditamap", "a.dita");
        assertThat(issues(g, "orphan-topic")).isEmpty();
    }

    @Test
    void orphanTopicAndConrefShips() throws Exception {
        topic("lonely.dita", "Lonely", "");
        write("lib.dita", "<?xml version=\"1.0\"?>\n<topic id=\"lib\"><title>Lib</title>"
                + "<body><p id=\"p\">x</p></body></topic>\n");
        topic("a.dita", "A", "<p conref=\"lib.dita#lib/p\"/>");
        write("m.ditamap", MAP_DT + "<map>\n<topicref href=\"a.dita\"/>\n</map>\n");

        ProjectGraph g = ProjectGraphBuilder.build(tmp, null, null);

        assertThat(issues(g, "orphan-topic")).extracting(GraphIssue::file).containsExactly("lonely.dita");
        assertThat(edges(g, "conref")).singleElement().satisfies(e -> {
            assertThat(e.from()).isEqualTo("a.dita");
            assertThat(e.to()).isEqualTo("lib.dita");
            assertThat(e.fragment()).isEqualTo("lib/p");
        });
        assertThat(node(g, "lib.dita").ships()).containsExactly("m");
    }

    @Test
    void scopeByMapAndByDeliverable() throws Exception {
        topic("a.dita", "A", "");
        topic("b.dita", "B", "");
        topic("orphan.dita", "O", "");
        write("a.ditamap", MAP_DT + "<map>\n<topicref href=\"a.dita\"/>\n</map>\n");
        write("maps/b.ditamap", MAP_DT + "<map>\n<topicref href=\"../b.dita\"/>\n</map>\n");
        write("project.json", """
                {"deliverables": [
                  {"name": "da", "context": {"input": "a.ditamap"}},
                  {"name": "db", "context": {"input": "maps/b.ditamap"}}
                ]}
                """);

        ProjectGraph byMap = ProjectGraphBuilder.build(tmp, "maps/b.ditamap", null);
        assertThat(byMap.scope()).isEqualTo("map:maps/b.ditamap");
        assertThat(byMap.deliverables()).extracting(GraphDeliverable::name).containsExactly("b");
        assertThat(byMap.nodes()).extracting(GraphNode::id)
                .containsExactlyInAnyOrder("maps/b.ditamap", "b.dita");

        ProjectGraph byAbsoluteMap = ProjectGraphBuilder.build(tmp,
                tmp.resolve("a.ditamap").toString(), null);
        assertThat(byAbsoluteMap.scope()).isEqualTo("map:a.ditamap");

        ProjectGraph byDeliverable = ProjectGraphBuilder.build(tmp, null, "db");
        assertThat(byDeliverable.scope()).isEqualTo("deliverable:db");
        assertThat(byDeliverable.deliverables()).singleElement().satisfies(d -> {
            assertThat(d.map()).isEqualTo("maps/b.ditamap");
            assertThat(d.ships()).containsExactlyInAnyOrder("maps/b.ditamap", "b.dita");
        });
        assertThat(byDeliverable.nodes()).extracting(GraphNode::id).doesNotContain("a.dita", "orphan.dita");

        ProjectGraph all = ProjectGraphBuilder.build(tmp, null, null);
        assertThat(all.deliverables()).extracting(GraphDeliverable::name).containsExactly("da", "db");
        assertThat(all.nodes()).extracting(GraphNode::id).contains("orphan.dita");

        assertThatThrownBy(() -> ProjectGraphBuilder.build(tmp, null, "nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serialisationOmitsNulls() throws Exception {
        topic("a.dita", "A", "");
        write("m.ditamap", MAP_DT + "<map>\n<topicref href=\"a.dita\"/>\n</map>\n");

        String json = new ObjectMapper().writeValueAsString(ProjectGraphBuilder.build(tmp, null, null));

        assertThat(json).contains("\"kind\":\"topicref\"", "\"generated\":", "\"scope\":\"all\"");
        assertThat(json).doesNotContain("null", "\"missing\"", "\"broken\"", "\"via\"");
    }

    // ---- bundled sample -----------------------------------------------------

    /**
     * The graph over the project the editor actually ships, which is the finished guide of
     * the DITA tutorial: eight deliverables out of five maps, key scopes, a collection and
     * a bookmap. It is the only test here that runs over a project nobody wrote for a test,
     * so it is where an edge kind that works on a four-file fixture and not on a real map
     * shows up. It is pinned to the sample on purpose: regenerating the bundle from a
     * different branch changes these numbers, and that is a thing to notice.
     */
    @Test
    void bundledSampleGraph() throws Exception {
        Path root = tmp.resolve("sample");
        SampleProject.install(root.toFile());

        ProjectGraph g = ProjectGraphBuilder.build(root, null, null);

        // project.json deliverables, in declared order, with their profiles.
        assertThat(g.deliverables()).extracting(GraphDeliverable::name)
                .containsExactly("full", "beginner-mac", "beginner-windows", "podcaster-linux",
                        "review", "install-variants", "collection", "book-pdf");
        assertThat(deliverable(g, "beginner-mac").map()).isEqualTo("beginner-guide.ditamap");
        assertThat(deliverable(g, "beginner-mac").ditavals()).containsExactly("filters/mac-beginner.ditaval");
        // The four deliverables that filter. "full", the collection, the variants map and the
        // book take no DITAVAL, so no profile edge belongs to them.
        assertThat(edges(g, "profile")).extracting(GraphEdge::deliverable)
                .containsExactlyInAnyOrder("beginner-mac", "beginner-windows", "podcaster-linux", "review");

        // The beginner guide is a subset: it does not carry the concept topics the full
        // guide opens with, though an xref from a topic it does carry reaches one.
        assertThat(deliverable(g, "full").ships()).contains("keydefs-glossary.ditamap",
                "shared/common-steps.dita", "topics/what-is-digital-audio.dita");
        assertThat(deliverable(g, "beginner-mac").ships())
                .doesNotContain("topics/what-is-digital-audio.dita", "topics/podcast-production-workflow.dita");

        // Every deliverable's key space reaches the glossary: the two keydef maps are
        // pulled into each root map, not into the full guide alone.
        String glossTopic = "topics/glossary/g-bit-depth.dita";
        assertThat(node(g, glossTopic).type()).isEqualTo("glossentry");
        assertThat(node(g, glossTopic).title()).isEqualTo("Bit depth");
        assertThat(node(g, glossTopic).ships())
                .containsExactlyElementsOf(g.deliverables().stream().map(GraphDeliverable::name).toList());

        // The bookmap is its own deliverable, and the only one that ships it.
        assertThat(node(g, "audacity-book.ditamap").type()).isEqualTo("bookmap");
        assertThat(node(g, "audacity-book.ditamap").ships()).containsExactly("book-pdf");

        // Reltable cells of audacity-guide are reltable edges, never topicref edges.
        List<GraphEdge> reltable = edges(g, "reltable");
        assertThat(reltable).extracting(GraphEdge::line)
                .containsExactlyInAnyOrder(101, 104, 105, 108, 114, 115, 118);
        assertThat(reltable).allSatisfy(e -> assertThat(e.from()).isEqualTo("audacity-guide.ditamap"));
        assertThat(edges(g, "topicref"))
                .filteredOn(e -> e.from().equals("audacity-guide.ditamap"))
                .extracting(GraphEdge::line).doesNotContain(101, 104, 105, 108, 114, 115, 118);

        // One key, bound to a different topic by each map that defines it — the whole point
        // of "start here", and the case a graph keyed on the key name alone gets wrong.
        assertThat(edges(g, "keytarget")).filteredOn(e -> e.from().equals("key:start-here"))
                .extracting(GraphEdge::via, GraphEdge::to)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("audacity-guide.ditamap", "topics/what-is-audacity.dita"),
                        org.assertj.core.groups.Tuple.tuple("audacity-book.ditamap", "topics/what-is-audacity.dita"),
                        org.assertj.core.groups.Tuple.tuple("beginner-guide.ditamap", "topics/recording-your-first-track.dita"),
                        org.assertj.core.groups.Tuple.tuple("podcaster-guide.ditamap", "topics/podcast-production-workflow.dita"));

        // The collection pulls in the three guides and the shared keydef and scheme maps.
        assertThat(edges(g, "mapref")).filteredOn(e -> e.from().equals("audacity-collection.ditamap"))
                .extracting(GraphEdge::to).containsExactlyInAnyOrder(
                        "audacity-guide.ditamap", "beginner-guide.ditamap", "podcaster-guide.ditamap",
                        "keydefs-glossary.ditamap", "keydefs-product.ditamap", "subject-scheme.ditamap");
        // Branch filtering: one map, one branch per platform, and "mac" is the controlled
        // value — a file named platform-macos.ditaval here would mean the branch was renamed
        // away from the subject scheme.
        assertThat(edges(g, "ditavalref")).filteredOn(e -> e.from().equals("installation-variants.ditamap"))
                .extracting(GraphEdge::to).containsExactlyInAnyOrder("filters/platform-windows.ditaval",
                        "filters/platform-mac.ditaval", "filters/platform-linux.ditaval");

        // Nothing is wrong with this project, and that is the sample's job: a first-time
        // user's first action is Check Project.
        assertThat(g.nodes()).noneMatch(n -> Boolean.TRUE.equals(n.missing()));
        assertThat(issues(g, "broken-reference")).isEmpty();
        assertThat(issues(g, "undefined-key")).isEmpty();

        // What a healthy project with key scopes still reports. The collection nests the
        // three guides, two of them under a keyscope, so the guides' own root-level
        // definitions are shadowed inside it and a reference written against them resolves
        // in every standalone deliverable and not in the collection. Both are warnings, and
        // neither is a defect to fix — which is why project health stays clean.
        assertThat(issues(g, "shadowed-key")).extracting(GraphIssue::file)
                .containsOnly("audacity-guide.ditamap");
        assertThat(issues(g, "shadowed-key")).extracting(GraphIssue::message)
                .contains("Key \"start-here\" is shadowed by the definition in podcaster-guide.ditamap line 11",
                        "Key \"podcaster.start-here\" is shadowed by the definition in podcaster-guide.ditamap line 11");
        assertThat(issues(g, "key-resolves-inconsistently")).extracting(GraphIssue::message)
                .allMatch(m -> m.endsWith("fails in collection"));
        assertThat(issues(g, "unused-key")).extracting(GraphIssue::message)
                .containsExactly("Key \"first-track\" is defined but never referenced");

        String json = new ObjectMapper().writeValueAsString(g);
        assertThat(json).doesNotContain("null");
    }

    @Test
    void bundledSampleScopedToDeliverable() throws Exception {
        Path root = tmp.resolve("sample");
        SampleProject.install(root.toFile());

        ProjectGraph g = ProjectGraphBuilder.build(root, null, "beginner-windows");

        assertThat(g.deliverables()).extracting(GraphDeliverable::name).containsExactly("beginner-windows");
        assertThat(g.nodes()).extracting(GraphNode::id)
                .contains("beginner-guide.ditamap", "filters/windows-beginner.ditaval")
                .doesNotContain("audacity-book.ditamap", "filters/mac-beginner.ditaval",
                        "topics/podcast-production-workflow.dita");
        assertThat(node(g, "topics/trimming-audio.dita").ships()).containsExactly("beginner-windows");
        // Reached only through an xref from a shipped topic: in the graph, but not shipped.
        assertThat(node(g, "topics/what-is-digital-audio.dita").ships()).isNull();
    }
}
