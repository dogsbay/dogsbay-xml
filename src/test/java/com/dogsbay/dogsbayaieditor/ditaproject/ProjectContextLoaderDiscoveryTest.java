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

package com.dogsbay.dogsbayaieditor.ditaproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1: auto-discovery of real DITA-OT project layouts (N arbitrarily-named files
 * under {@code projects/}), model fidelity (params/output/multiple ditavals), and
 * {@code includes}+{@code idref} resolution. Mirrors the shape of the real
 * {@code dita-starter/projects/*.json}.
 */
class ProjectContextLoaderDiscoveryTest {

    private Path write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }

    private ProjectContext load(Path dir) throws Exception {
        return ProjectContextLoader.load(dir, null, List.of(), null);
    }

    @Test
    void discoversNArbitrarilyNamedFilesUnderProjectsDir(@TempDir Path dir) throws Exception {
        write(dir, "projects/rosa-html5.json", """
            { "deliverables": [ {
                "name": "HTML5",
                "context": { "id": "html",
                  "input": "../input/guide.ditamap",
                  "profiles": { "ditavals": ["../input/rosa.ditaval"] } },
                "output": "../output/rosa",
                "publication": { "transtype": "html5",
                  "params": [ { "name": "nav-toc", "value": "full" },
                              { "name": "args.cssroot", "path": "../resources" } ] } } ] }
            """);
        write(dir, "projects/enterprise-html5.json", """
            { "deliverables": [ {
                "name": "HTML5",
                "context": { "input": "../input/guide.ditamap",
                  "profiles": { "ditavals": ["../input/enterprise.ditaval"] } },
                "output": "../output/enterprise",
                "publication": { "transtype": "html5" } } ] }
            """);

        ProjectContext ctx = load(dir);

        assertThat(ctx.projectFiles()).hasSize(2);
        assertThat(ctx.deliverables()).hasSize(2);

        // Both deliverables are named "HTML5" — disambiguated by source file.
        assertThat(ctx.deliverables()).allSatisfy(d -> assertThat(d.name()).isEqualTo("HTML5"));
        assertThat(ctx.deliverables()).extracting(d -> d.sourceFile().getFileName().toString())
            .containsExactlyInAnyOrder("rosa-html5.json", "enterprise-html5.json");

        Deliverable rosa = ctx.deliverables().stream()
            .filter(d -> d.sourceFile().getFileName().toString().equals("rosa-html5.json"))
            .findFirst().orElseThrow();
        // paths resolve relative to the project file (projects/ -> ../input)
        assertThat(rosa.map()).isEqualTo(dir.resolve("input/guide.ditamap").normalize());
        assertThat(rosa.ditaval()).isEqualTo(dir.resolve("input/rosa.ditaval").normalize());
        assertThat(rosa.transtype()).isEqualTo("html5");
        assertThat(rosa.output()).isEqualTo(Path.of("../output/rosa"));
        // params kept faithfully, including the path-kind param
        assertThat(rosa.params()).hasSize(2);
        assertThat(rosa.params()).anySatisfy(p -> {
            assertThat(p.name()).isEqualTo("nav-toc");
            assertThat(p.value()).isEqualTo("full");
            assertThat(p.kind()).isEqualTo(Param.Kind.VALUE);
        });
        assertThat(rosa.params()).anySatisfy(p -> {
            assertThat(p.name()).isEqualTo("args.cssroot");
            assertThat(p.kind()).isEqualTo(Param.Kind.PATH);
        });
    }

    @Test
    void followsIncludesToResolveIdrefAcrossFiles(@TempDir Path dir) throws Exception {
        write(dir, "projects/common.json", """
            { "contexts": [ { "id": "html",
                "input": "../src/guide.ditamap",
                "profiles": { "ditavals": ["../src/html.ditaval"] } } ] }
            """);
        write(dir, "projects/html.json", """
            { "includes": ["common.json"],
              "deliverables": [ {
                "name": "HTML5",
                "context": { "idref": "html" },
                "publication": { "transtype": "html5",
                  "params": [ { "name": "nav-toc", "value": "full" } ] } } ] }
            """);

        ProjectContext ctx = load(dir);

        // common.json contributes 0 deliverables; html.json contributes 1 (idref resolved).
        assertThat(ctx.deliverables()).hasSize(1);
        Deliverable d = ctx.deliverables().get(0);
        assertThat(d.map()).isEqualTo(dir.resolve("src/guide.ditamap").normalize());
        assertThat(d.ditaval()).isEqualTo(dir.resolve("src/html.ditaval").normalize());
        assertThat(d.transtype()).isEqualTo("html5");
        assertThat(d.contextId()).isEqualTo("html");
        assertThat(d.params()).singleElement().satisfies(p ->
            assertThat(p.name()).isEqualTo("nav-toc"));
    }

    @Test
    void contentSniffIgnoresNonProjectJson(@TempDir Path dir) throws Exception {
        write(dir, "package.json", """
            { "name": "not-a-dita-project", "version": "1.0.0" }
            """);
        write(dir, "projects/guide.json", """
            { "deliverables": [ {
                "name": "Guide",
                "context": { "input": "../g.ditamap" },
                "publication": { "transtype": "html5" } } ] }
            """);

        ProjectContext ctx = load(dir);

        assertThat(ctx.projectFiles()).hasSize(1);
        assertThat(ctx.deliverables()).singleElement().satisfies(d ->
            assertThat(d.name()).isEqualTo("Guide"));
    }

    @Test
    void mergesContextAndPublicationProfileDitavals(@TempDir Path dir) throws Exception {
        write(dir, "projects/p.json", """
            { "deliverables": [ {
                "name": "PDF",
                "context": { "input": "../g.ditamap",
                  "profiles": { "ditavals": ["../ctx.ditaval"] } },
                "publication": { "transtype": "pdf2",
                  "profiles": { "ditavals": ["../pub.ditaval"] } } } ] }
            """);

        ProjectContext ctx = load(dir);

        Deliverable d = ctx.deliverables().get(0);
        assertThat(d.ditavals()).containsExactly(
            dir.resolve("ctx.ditaval").normalize(),
            dir.resolve("pub.ditaval").normalize());
    }

    @Test
    void publicationIdrefInheritsParamsWithLocalOverride(@TempDir Path dir) throws Exception {
        write(dir, "projects/p.json", """
            { "publications": [ { "id": "common", "transtype": "html5",
                "params": [ { "name": "nav-toc", "value": "partial" },
                            { "name": "args.copycss", "value": "yes" } ] } ],
              "deliverables": [ {
                "name": "HTML5",
                "context": { "input": "../g.ditamap" },
                "publication": { "idref": "common",
                  "params": [ { "name": "nav-toc", "value": "full" } ] } } ] }
            """);

        ProjectContext ctx = load(dir);

        Deliverable d = ctx.deliverables().get(0);
        assertThat(d.transtype()).isEqualTo("html5");
        // inherited args.copycss + locally-overridden nav-toc (full, not partial)
        assertThat(d.params()).hasSize(2);
        assertThat(d.params()).anySatisfy(p -> {
            assertThat(p.name()).isEqualTo("nav-toc");
            assertThat(p.value()).isEqualTo("full");
        });
        assertThat(d.params()).anySatisfy(p -> assertThat(p.name()).isEqualTo("args.copycss"));
    }

    @Test
    void emptyWorkspaceSynthesizesFromFallbackRootMap(@TempDir Path dir) throws Exception {
        Path map = dir.resolve("root.ditamap");
        Files.writeString(map, "<map/>");

        ProjectContext ctx = ProjectContextLoader.load(dir, map, List.of(), null);

        assertThat(ctx.isSynthesized()).isTrue();
        assertThat(ctx.deliverables()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("default");
            assertThat(d.sourceFile()).isNull();
        });
    }
}
