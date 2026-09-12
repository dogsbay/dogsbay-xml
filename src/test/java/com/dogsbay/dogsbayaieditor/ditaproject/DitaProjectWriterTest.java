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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P5: in-place, field-preserving writes. The key guarantee is that editing one
 * deliverable never drops the user's other fields (output, params, context.id,
 * top-level includes/contexts/publications, unknown keys). Verified by writing,
 * then re-reading through the loader and re-parsing the raw JSON.
 */
class DitaProjectWriterTest {

    @TempDir Path dir;

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    private String read(String name) throws Exception {
        return Files.readString(dir.resolve(name));
    }

    @Test
    void updatingOneDeliverablePreservesOutputParamsContextIdAndUnknownKeys() throws Exception {
        write("project.json", """
            { "includes": ["common.json"],
              "deliverables": [ {
                "name": "rosa",
                "x-custom": "keep-me",
                "context": { "id": "html", "input": "guide.ditamap",
                  "profiles": { "ditavals": ["rosa.ditaval"] } },
                "output": "out/rosa",
                "publication": { "transtype": "html5",
                  "params": [ { "name": "nav-toc", "value": "full" },
                              { "name": "args.cssroot", "path": "../res" } ] } } ] }
            """);

        // Change only the transtype.
        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("rosa", "guide.ditamap", List.of("rosa.ditaval"),
                "pdf2", "out/rosa",
                List.of(Param.value("nav-toc", "full"),
                        new Param("args.cssroot", "../res", Param.Kind.PATH))));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(dir.resolve("project.json").toFile());
        var d = root.path("deliverables").path(0);

        assertThat(root.path("includes").path(0).asText()).isEqualTo("common.json"); // top-level kept
        assertThat(d.path("x-custom").asText()).isEqualTo("keep-me");                 // unknown key kept
        assertThat(d.path("context").path("id").asText()).isEqualTo("html");          // context id kept
        assertThat(d.path("output").asText()).isEqualTo("out/rosa");                  // output kept
        assertThat(d.path("publication").path("transtype").asText()).isEqualTo("pdf2"); // edited
        assertThat(d.path("publication").path("params")).hasSize(2);                  // params kept
        assertThat(d.path("publication").path("params").path(1).path("path").asText())
            .isEqualTo("../res"); // path-kind param round-trips as "path"
    }

    @Test
    void addsANewDeliverableAlongsideExistingOnes() throws Exception {
        write("project.json", """
            { "deliverables": [ {
                "name": "full",
                "context": { "input": "guide.ditamap" },
                "publication": { "transtype": "html5" } } ] }
            """);

        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("mac", "guide.ditamap", List.of("mac.ditaval"),
                "html5", "out/mac", List.of()));

        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        assertThat(ctx.deliverables()).hasSize(2);
        Deliverable mac = ctx.deliverable("mac");
        assertThat(mac).isNotNull();
        assertThat(mac.ditaval()).isEqualTo(dir.resolve("mac.ditaval").normalize());
        assertThat(mac.transtype()).isEqualTo("html5");
        // new context got an id defaulted to the name
        assertThat(mac.contextId()).isEqualTo("mac");
    }

    @Test
    void writesAndReloadsPublicationParams() throws Exception {
        write("project.json", "{ \"deliverables\": [] }");
        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("full", "guide.ditamap", List.of(), "html5", null,
                List.of(Param.value("nav-toc", "partial"),
                        new Param("args.cssroot", "css", Param.Kind.PATH))));

        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        Deliverable full = ctx.deliverable("full");
        assertThat(full.params()).hasSize(2);
        var byName = full.params().stream()
            .collect(java.util.stream.Collectors.toMap(Param::name, p -> p));
        assertThat(byName.get("nav-toc").value()).isEqualTo("partial");
        assertThat(byName.get("nav-toc").kind()).isEqualTo(Param.Kind.VALUE);
        assertThat(byName.get("args.cssroot").kind()).isEqualTo(Param.Kind.PATH);

        // editing again with fewer params replaces the block (add/update/delete)
        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("full", "guide.ditamap", List.of(), "html5", null,
                List.of(Param.value("nav-toc", "full"))));
        Deliverable reedited = ProjectContextLoader.load(dir, null, List.of(), null)
            .deliverable("full");
        assertThat(reedited.params()).hasSize(1);
        assertThat(reedited.params().get(0).value()).isEqualTo("full");
    }

    @Test
    void deletesADeliverablePreservingOthers() throws Exception {
        write("project.json", """
            { "title": "Docs", "deliverables": [
                { "name": "full", "context": { "input": "guide.ditamap" },
                  "publication": { "transtype": "html5" } },
                { "name": "mac", "context": { "input": "guide.ditamap" },
                  "publication": { "transtype": "html5" } } ] }
            """);

        boolean removed = DitaProjectWriter.deleteDeliverable(dir.resolve("project.json"), "mac");
        assertThat(removed).isTrue();

        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        assertThat(ctx.deliverables()).hasSize(1);
        assertThat(ctx.deliverable("full")).isNotNull();
        assertThat(ctx.deliverable("mac")).isNull();
        // unrelated content (the title) survives
        assertThat(Files.readString(dir.resolve("project.json"))).contains("Docs");

        // deleting an absent name is a no-op
        assertThat(DitaProjectWriter.deleteDeliverable(dir.resolve("project.json"), "nope"))
            .isFalse();
    }

    @Test
    void clearingDitavalsRemovesTheProfilesBlock() throws Exception {
        write("project.json", """
            { "deliverables": [ {
                "name": "full",
                "context": { "input": "guide.ditamap",
                  "profiles": { "ditavals": ["x.ditaval"] } },
                "publication": { "transtype": "html5" } } ] }
            """);

        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("full", "guide.ditamap", List.of(), "html5", null, List.of()));

        assertThat(read("project.json")).doesNotContain("ditaval");
        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        assertThat(ctx.deliverable("full").hasDitaval()).isFalse();
    }

    @Test
    void preservesIdrefContextAndPublicationInsteadOfInlining() throws Exception {
        write("project.json", """
            { "contexts": [ { "id": "html", "input": "guide.ditamap" } ],
              "publications": [ { "id": "common", "transtype": "html5" } ],
              "deliverables": [ {
                "name": "rosa",
                "context": { "idref": "html" },
                "publication": { "idref": "common" } } ] }
            """);

        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("rosa", "guide.ditamap", List.of("rosa.ditaval"),
                "pdf2", "out/rosa", List.of()));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var d = mapper.readTree(dir.resolve("project.json").toFile())
                .path("deliverables").path(0);
        // The idref links survive — not replaced by inline objects.
        assertThat(d.path("context").path("idref").asText()).isEqualTo("html");
        assertThat(d.path("context").has("input")).isFalse();
        assertThat(d.path("publication").path("idref").asText()).isEqualTo("common");
        assertThat(d.path("publication").has("transtype")).isFalse();
        // deliverable-level fields still applied
        assertThat(d.path("output").asText()).isEqualTo("out/rosa");
    }

    @Test
    void migratesLegacyFlatDitavalToProfilesWithoutDuplicating() throws Exception {
        write("project.json", """
            { "deliverables": [ {
                "name": "full",
                "context": { "input": "guide.ditamap", "ditaval": "old.ditaval" },
                "publication": { "transtype": "html5" } } ] }
            """);

        DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("full", "guide.ditamap", List.of("new.ditaval"),
                "html5", null, List.of()));

        // flat key gone; only the profiles ditaval remains -> loader sees ONE ditaval
        assertThat(read("project.json")).doesNotContain("old.ditaval");
        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        assertThat(ctx.deliverable("full").ditavals()).containsExactly(
            dir.resolve("new.ditaval").normalize());
    }

    @Test
    void rejectsANonObjectRootInsteadOfDiscardingIt() throws Exception {
        write("project.json", "[ \"not\", \"a\", \"project\" ]");
        assertThatThrownBy(() -> DitaProjectWriter.upsertDeliverable(dir.resolve("project.json"),
            new DeliverableEdit("x", "g.ditamap", List.of(), "html5", null, List.of())))
            .isInstanceOf(java.io.IOException.class);
        // original content untouched
        assertThat(read("project.json")).contains("not");
    }

    @Test
    void createsParentDirectoriesForANewProjectFile() throws Exception {
        Path pf = dir.resolve("nested/sub/project.json");
        DitaProjectWriter.createProject(pf,
            new DeliverableEdit("full", "guide.ditamap", List.of(), "html5", null, List.of()));
        assertThat(Files.exists(pf)).isTrue();
    }

    @Test
    void createsANewProjectFile() throws Exception {
        Path pf = dir.resolve("project.json");
        DitaProjectWriter.createProject(pf,
            new DeliverableEdit("full", "guide.ditamap", List.of(), "html5", "out", List.of()));

        assertThat(Files.exists(pf)).isTrue();
        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        assertThat(ctx.deliverables()).singleElement().satisfies(d ->
            assertThat(d.name()).isEqualTo("full"));

        assertThatThrownBy(() -> DitaProjectWriter.createProject(pf,
            new DeliverableEdit("x", "g.ditamap", List.of(), "html5", null, List.of())))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void roundTripsThroughYaml() throws Exception {
        DitaProjectWriter.upsertDeliverable(dir.resolve("project.yaml"),
            new DeliverableEdit("full", "guide.ditamap", List.of("x.ditaval"),
                "html5", null, List.of(Param.value("nav-toc", "full"))));

        var ctx = ProjectContextLoader.load(dir, null, List.of(), null);
        Deliverable d = ctx.deliverable("full");
        assertThat(d.transtype()).isEqualTo("html5");
        assertThat(d.ditaval()).isEqualTo(dir.resolve("x.ditaval").normalize());
        assertThat(d.params()).singleElement().satisfies(p ->
            assertThat(p.name()).isEqualTo("nav-toc"));
    }

    @Test
    void rejectsXmlInPlaceEdit() throws Exception {
        write("project.xml", "<project xmlns=\"https://www.dita-ot.org/project\"/>");
        assertThat(DitaProjectWriter.isEditable(dir.resolve("project.xml"))).isFalse();
        assertThatThrownBy(() -> DitaProjectWriter.upsertDeliverable(dir.resolve("project.xml"),
            new DeliverableEdit("x", "g.ditamap", List.of(), "html5", null, List.of())))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("JSON/YAML");
    }
}
