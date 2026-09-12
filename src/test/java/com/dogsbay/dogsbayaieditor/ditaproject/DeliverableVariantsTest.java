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

/** Phase 3 (headless): branches projected as derived deliverable variants. */
class DeliverableVariantsTest {

    @TempDir Path dir;

    @Test
    void projectsBranchesAsDerivedDeliverables() throws Exception {
        Files.writeString(dir.resolve("base.ditaval"), "<val/>");
        Files.writeString(dir.resolve("win.ditaval"), "<val/>");
        Files.writeString(dir.resolve("mac.ditaval"), "<val/>");
        Path map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win-</dvrResourcePrefix></ditavalmeta></ditavalref>
                <ditavalref href="mac.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>mac-</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);
        Deliverable full = new Deliverable("full", map, List.of(dir.resolve("base.ditaval")),
                "html5", null, List.of(), dir.resolve("project.json"), null);

        List<Deliverable> variants = DeliverableVariants.of(full);

        assertThat(variants).extracting(Deliverable::name)
                .containsExactlyInAnyOrder("full · win-", "full · mac-");
        Deliverable win = variants.stream().filter(v -> v.name().endsWith("win-"))
                .findFirst().orElseThrow();
        // effective DITAVAL set = parent's + the branch's
        assertThat(win.ditavals()).containsExactly(
                dir.resolve("base.ditaval"), dir.resolve("win.ditaval"));
        assertThat(win.transtype()).isEqualTo("html5");
        assertThat(win.map()).isEqualTo(map);
    }

    @Test
    void collidingBranchLabelsGetUniqueNames() throws Exception {
        Files.createDirectories(dir.resolve("a"));
        Files.createDirectories(dir.resolve("b"));
        Files.writeString(dir.resolve("a/f.ditaval"), "<val/>");
        Files.writeString(dir.resolve("b/f.ditaval"), "<val/>");
        Path map = dir.resolve("guide.ditamap");
        // two branches with NO dvrResourcePrefix → both label() == "f.ditaval"
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="t.dita">
                <ditavalref href="a/f.ditaval"/>
                <ditavalref href="b/f.ditaval"/>
              </topicref>
            </map>
            """);
        Deliverable full = new Deliverable("full", map, List.of(), "html5", null,
                List.of(), null, null);
        List<Deliverable> variants = DeliverableVariants.of(full);
        assertThat(variants).extracting(Deliverable::name)
                .containsExactly("full · f.ditaval", "full · f.ditaval (2)");
    }

    @Test
    void noBranchesYieldsNoVariants() throws Exception {
        Path map = dir.resolve("plain.ditamap");
        Files.writeString(map, "<map><title>P</title><topicref href=\"a.dita\"/></map>");
        Deliverable d = new Deliverable("full", map, List.of(), "html5", null,
                List.of(), null, null);
        assertThat(DeliverableVariants.of(d)).isEmpty();
    }
}
