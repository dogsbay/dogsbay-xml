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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult;

/** glossary_audit: inventory, undefined references, unused entries. */
class GlossaryAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void inventoryUndefinedAndUnused() throws Exception {
        write("glossary/g-waveform.dita",
                "<glossentry id=\"waveform\"><glossterm>waveform</glossterm></glossentry>");
        write("glossary/g-clipping.dita",
                "<glossentry id=\"clipping\"><glossterm>clipping</glossterm></glossentry>");
        write("map.ditamap", "<map><title>M</title>"
                + "<keydef keys=\"waveform\" href=\"glossary/g-waveform.dita\"/>"
                + "<keydef keys=\"clipping\" href=\"glossary/g-clipping.dita\"/></map>");
        write("topics/use.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><abbreviated-form keyref=\"waveform\"/></p>"      // resolves → used
                + "<p><abbreviated-form keyref=\"ghost\"/></p>"          // undefined key
                + "</conbody></concept>");

        GlossaryAuditResult r = exec.execute(new GlossaryAuditCommand(
                dir.toString(), "root", dir.resolve("map.ditamap").toString()));

        // inventory: both glossentries
        assertThat(r.entries()).extracting(GlossaryAuditResult.Entry::term)
                .containsExactlyInAnyOrder("waveform", "clipping");
        // ghost doesn't resolve to a glossary entry → undefined
        assertThat(r.undefined()).hasSize(1);
        assertThat(r.undefined().get(0).keyref()).isEqualTo("ghost");
        // clipping is defined but never referenced → unused
        assertThat(r.unused()).extracting(GlossaryAuditResult.Entry::term)
                .containsExactly("clipping");
    }

    @Test
    void withoutRootMapReturnsInventoryOnly() throws Exception {
        write("glossary/g-waveform.dita",
                "<glossentry id=\"waveform\"><glossterm>waveform</glossterm></glossentry>");
        write("topics/use.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><abbreviated-form keyref=\"ghost\"/></p></conbody></concept>");

        GlossaryAuditResult r = exec.execute(
                new GlossaryAuditCommand(dir.toString(), "root", null));

        assertThat(r.entries()).hasSize(1);
        assertThat(r.undefined()).isEmpty();   // no key space → no resolution checks
        assertThat(r.unused()).isEmpty();
    }
}
