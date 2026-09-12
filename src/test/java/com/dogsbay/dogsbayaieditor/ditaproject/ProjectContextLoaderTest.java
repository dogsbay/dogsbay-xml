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

class ProjectContextLoaderTest {

    private Path write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    // ── XML ─────────────────────────────────────────────────────────────

    @Test
    void parsesInlineXmlDeliverable(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <deliverable name="mac-beginner">
                <context>
                  <input href="audacity-guide.ditamap"/>
                  <profile><ditaval href="mac-beginner.ditaval"/></profile>
                </context>
                <publication transtype="html5"/>
              </deliverable>
            </project>
            """);

        List<Deliverable> ds = ProjectContextLoader.parseDeliverables(pf);

        assertThat(ds).hasSize(1);
        Deliverable d = ds.get(0);
        assertThat(d.name()).isEqualTo("mac-beginner");
        assertThat(d.map()).isEqualTo(dir.resolve("audacity-guide.ditamap").normalize());
        assertThat(d.ditaval()).isEqualTo(dir.resolve("mac-beginner.ditaval").normalize());
        assertThat(d.transtype()).isEqualTo("html5");
        assertThat(d.hasDitaval()).isTrue();
    }

    @Test
    void resolvesXmlContextAndPublicationByIdref(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <context id="ctx-mac">
                <input href="guide.ditamap"/>
                <profile><ditaval href="mac.ditaval"/></profile>
              </context>
              <publication id="pub-html" transtype="html5"/>
              <deliverable name="mac">
                <context idref="ctx-mac"/>
                <publication idref="pub-html"/>
              </deliverable>
            </project>
            """);

        List<Deliverable> ds = ProjectContextLoader.parseDeliverables(pf);

        assertThat(ds).hasSize(1);
        assertThat(ds.get(0).map()).isEqualTo(dir.resolve("guide.ditamap").normalize());
        assertThat(ds.get(0).ditaval()).isEqualTo(dir.resolve("mac.ditaval").normalize());
        assertThat(ds.get(0).transtype()).isEqualTo("html5");
    }

    @Test
    void skipsXmlDeliverableWithNoInputMap(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.xml", """
            <project>
              <deliverable name="broken"><publication transtype="pdf"/></deliverable>
            </project>
            """);
        assertThat(ProjectContextLoader.parseDeliverables(pf)).isEmpty();
    }

    // ── JSON ────────────────────────────────────────────────────────────

    @Test
    void parsesInlineJsonDeliverable(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.json", """
            { "deliverables": [
                { "name": "mac-beginner",
                  "context": { "input": "audacity-guide.ditamap", "ditaval": "mac-beginner.ditaval" },
                  "publication": { "transtype": "html5" } } ] }
            """);

        List<Deliverable> ds = ProjectContextLoader.parseDeliverables(pf);

        assertThat(ds).hasSize(1);
        assertThat(ds.get(0).name()).isEqualTo("mac-beginner");
        assertThat(ds.get(0).map()).isEqualTo(dir.resolve("audacity-guide.ditamap").normalize());
        assertThat(ds.get(0).ditaval()).isEqualTo(dir.resolve("mac-beginner.ditaval").normalize());
        assertThat(ds.get(0).transtype()).isEqualTo("html5");
    }

    @Test
    void parsesJsonProfilesDitavalsShapeAndIdref(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.json", """
            { "contexts": [ { "id": "c1", "input": "m.ditamap",
                              "profiles": { "ditavals": ["c1.ditaval"] } } ],
              "publications": [ { "id": "p1", "transtype": "pdf" } ],
              "deliverables": [ { "name": "d1", "context": "c1", "publication": { "idref": "p1" } } ] }
            """);

        List<Deliverable> ds = ProjectContextLoader.parseDeliverables(pf);

        assertThat(ds).hasSize(1);
        assertThat(ds.get(0).map()).isEqualTo(dir.resolve("m.ditamap").normalize());
        assertThat(ds.get(0).ditaval()).isEqualTo(dir.resolve("c1.ditaval").normalize());
        assertThat(ds.get(0).transtype()).isEqualTo("pdf");
    }

    // ── YAML ────────────────────────────────────────────────────────────

    @Test
    void parsesInlineYamlDeliverable(@TempDir Path dir) throws Exception {
        Path pf = write(dir, "project.yaml", """
            deliverables:
              - name: mac-beginner
                context:
                  input: audacity-guide.ditamap
                  ditaval: mac-beginner.ditaval
                publication:
                  transtype: html5
            """);

        List<Deliverable> ds = ProjectContextLoader.parseDeliverables(pf);

        assertThat(ds).hasSize(1);
        assertThat(ds.get(0).name()).isEqualTo("mac-beginner");
        assertThat(ds.get(0).map()).isEqualTo(dir.resolve("audacity-guide.ditamap").normalize());
        assertThat(ds.get(0).ditaval()).isEqualTo(dir.resolve("mac-beginner.ditaval").normalize());
        assertThat(ds.get(0).transtype()).isEqualTo("html5");
    }

    // ── discovery / synthesis / load ────────────────────────────────────

    @Test
    void discoversJsonOverXmlOverYaml(@TempDir Path dir) throws Exception {
        write(dir, "project.yaml", "deliverables: []");
        write(dir, "project.xml", "<project/>");
        write(dir, "project.json", "{}");
        assertThat(ProjectContextLoader.discover(dir)).get()
                .isEqualTo(dir.resolve("project.json"));

        Files.delete(dir.resolve("project.json"));
        assertThat(ProjectContextLoader.discover(dir)).get()
                .isEqualTo(dir.resolve("project.xml"));
    }

    @Test
    void synthesizesSingleDeliverableFromRootMap(@TempDir Path dir) {
        Path map = dir.resolve("guide.ditamap");
        ProjectContext ctx = ProjectContextLoader.synthesize(dir, map, List.of(), null);

        assertThat(ctx.isSynthesized()).isTrue();
        assertThat(ctx.deliverables()).hasSize(1);
        assertThat(ctx.defaultDeliverable().name()).isEqualTo("default");
        assertThat(ctx.rootMap()).isEqualTo(map.toAbsolutePath().normalize());
    }

    @Test
    void synthesizesEmptyWhenNoRootMap(@TempDir Path dir) {
        ProjectContext ctx = ProjectContextLoader.synthesize(dir, null, List.of(), null);
        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.defaultDeliverable()).isNull();
        assertThat(ctx.rootMap()).isNull();
    }

    @Test
    void loadReadsProjectFileWithCallerSuppliedCatalogs(@TempDir Path dir) throws Exception {
        write(dir, "project.json", """
            { "deliverables": [ { "name": "d",
                "context": { "input": "m.ditamap" } } ] }
            """);
        Path catalog = dir.resolve("catalog.xml");

        ProjectContext ctx = ProjectContextLoader.load(dir, null, List.of(catalog), null);

        assertThat(ctx.sourceFormat()).isEqualTo("json");
        assertThat(ctx.projectFile()).isEqualTo(dir.resolve("project.json"));
        assertThat(ctx.catalogs()).containsExactly(catalog);
        assertThat(ctx.deliverable("d")).isNotNull();
        assertThat(ctx.deliverable("missing")).isNull();
    }

    @Test
    void loadFallsBackToSynthesisWhenNoProjectFile(@TempDir Path dir) throws Exception {
        Path map = dir.resolve("guide.ditamap");
        ProjectContext ctx = ProjectContextLoader.load(dir, map, List.of(), null);

        assertThat(ctx.isSynthesized()).isTrue();
        assertThat(ctx.rootMap()).isEqualTo(map.toAbsolutePath().normalize());
    }
}
