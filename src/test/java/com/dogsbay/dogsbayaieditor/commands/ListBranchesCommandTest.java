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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.BranchInfo;

/** Phase 2: branch-filter variant enumeration (ditavalref). */
class ListBranchesCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    @Test
    void enumeratesSiblingBranchesWithMetaAndKeyscope() throws Exception {
        write("win.ditaval", "<val/>");
        write("mac.ditaval", "<val/>");
        File m = write("guide.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <topicref href="install.dita" navtitle="Install">
                <ditavalref href="win.ditaval">
                  <ditavalmeta>
                    <dvrResourcePrefix>win-</dvrResourcePrefix>
                    <dvrKeyscopePrefix>win.</dvrKeyscopePrefix>
                  </ditavalmeta>
                </ditavalref>
                <ditavalref href="mac.ditaval">
                  <ditavalmeta><dvrResourcePrefix>mac-</dvrResourcePrefix></ditavalmeta>
                </ditavalref>
              </topicref>
            </map>
            """);
        var branches = exec.execute(new ListBranchesCommand(m.toString()));
        assertThat(branches).hasSize(2);
        assertThat(branches).extracting(BranchInfo::label)
                .containsExactlyInAnyOrder("win-", "mac-");
        BranchInfo win = branches.stream().filter(b -> b.label().equals("win-"))
                .findFirst().orElseThrow();
        assertThat(win.appliesTo()).isEqualTo("install.dita");
        assertThat(win.ditaval()).isEqualTo("win.ditaval");
        assertThat(win.resolvedDitaval()).isEqualTo(dir.resolve("win.ditaval").toFile()
                .getAbsolutePath());
        assertThat(win.generatedKeyscope()).isEqualTo("win.");
    }

    @Test
    void missingDitavalIsReportedAsUnresolved() throws Exception {
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="a.dita">
                <ditavalref href="gone.ditaval"/>
              </topicref>
            </map>
            """);
        var branches = exec.execute(new ListBranchesCommand(m.toString()));
        assertThat(branches).singleElement().satisfies(b -> {
            assertThat(b.ditaval()).isEqualTo("gone.ditaval");   // the href is reported
            assertThat(b.resolvedDitaval()).isNull();             // but it doesn't resolve
        });
    }

    @Test
    void noDitavalrefYieldsNoBranches() throws Exception {
        File m = write("plain.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>P</title><topicref href="a.dita"/></map>
            """);
        assertThat(exec.execute(new ListBranchesCommand(m.toString()))).isEmpty();
    }
}
