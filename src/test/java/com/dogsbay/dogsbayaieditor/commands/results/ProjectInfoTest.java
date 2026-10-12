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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext;

class ProjectInfoTest {

    @Test
    void enrichesFromProjectContext() {
        Path root = Path.of("/ws");
        Path projectFile = root.resolve("project.json");
        Path catalog = Path.of("/cat/catalog.xml");
        Path map = root.resolve("guide.ditamap");
        Path ditaval = root.resolve("mac.ditaval");
        ProjectContext ctx = new ProjectContext(root, projectFile, "json",
                List.of(catalog),
                List.of(new Deliverable("mac", map, ditaval, "html5")),
                null);

        ProjectInfo info = ProjectInfo.of("ws", "/ws", "dita", "guide.ditamap", true, ctx);

        assertThat(info.projectFile()).isEqualTo(projectFile.toString());
        assertThat(info.catalogs()).containsExactly(catalog.toString());
        assertThat(info.deliverables()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("mac");
            assertThat(d.map()).isEqualTo(map.toString());
            assertThat(d.ditaval()).isEqualTo(ditaval.toString());
            assertThat(d.transtype()).isEqualTo("html5");
        });
    }

    @Test
    void nullContextYieldsLightweightInfo() {
        ProjectInfo info = ProjectInfo.of("ws", "/ws", "dita", "guide.ditamap", true, null);
        assertThat(info.projectFile()).isNull();
        assertThat(info.catalogs()).isEmpty();
        assertThat(info.deliverables()).isEmpty();
        assertThat(info.defaultRootMap()).isEqualTo("guide.ditamap");
    }

    @Test
    void fiveArgConstructorStaysLightweight() {
        ProjectInfo info = new ProjectInfo("ws", "/ws", "dita", null, false);
        assertThat(info.catalogs()).isEmpty();
        assertThat(info.deliverables()).isEmpty();
        assertThat(info.projectFile()).isNull();
    }
}
