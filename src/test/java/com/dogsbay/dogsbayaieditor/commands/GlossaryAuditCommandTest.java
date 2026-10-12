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
    void anEntryInsideAGlossgroupIsAGlossaryTerm() throws Exception {
        // Reported by CI against the tutorial's own glossary: gl-decibel and gl-hertz are entries
        // inside topics/glossary/audio-units.dita, a <glossgroup>, and every reference to them was
        // called undefined because the inventory was keyed by file and read only a root entry.
        write("glossary/audio-units.dita", "<glossgroup id=\"units\"><title>Units</title>"
                + "<glossentry id=\"gl-decibel\"><glossterm>decibel</glossterm></glossentry>"
                + "<glossentry id=\"gl-hertz\"><glossterm>hertz</glossterm></glossentry>"
                + "</glossgroup>");
        write("map.ditamap", "<map><title>M</title>"
                + "<keydef keys=\"gl-decibel\" href=\"glossary/audio-units.dita#gl-decibel\"/>"
                + "<keydef keys=\"gl-hertz\" href=\"glossary/audio-units.dita#gl-hertz\"/>"
                + "<keydef keys=\"units\" href=\"glossary/audio-units.dita\"/></map>");
        write("topics/use.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><abbreviated-form keyref=\"gl-decibel\"/></p>"   // into the group → a term
                + "<p><abbreviated-form keyref=\"units\"/></p>"        // the group itself → not
                + "</conbody></concept>");

        GlossaryAuditResult r = exec.execute(new GlossaryAuditCommand(
                dir.toString(), "root", dir.resolve("map.ditamap").toString()));

        // both entries are in the inventory, each with its own id and term
        assertThat(r.entries()).extracting(GlossaryAuditResult.Entry::id,
                GlossaryAuditResult.Entry::term)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("gl-decibel", "decibel"),
                        org.assertj.core.groups.Tuple.tuple("gl-hertz", "hertz"));
        // the reference into the group resolves; the one at the group does not, because a container
        // is not a term — which is the one thing the old behaviour got right, for the wrong reason
        assertThat(r.undefined()).extracting(GlossaryAuditResult.Ref::keyref)
                .containsExactly("units");
        // unused is per entry now, not per file: the group's other term is reported on its own
        assertThat(r.unused()).extracting(GlossaryAuditResult.Entry::term)
                .containsExactly("hertz");
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
