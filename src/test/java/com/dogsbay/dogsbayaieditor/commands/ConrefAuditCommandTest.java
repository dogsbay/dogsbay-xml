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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

class ConrefAuditCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();

    private void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void reportsMissingElementIdButNotValidOnes(@TempDir Path dir) throws Exception {
        write(dir, "common.dita",
            "<topic id=\"c\"><title>C</title><body><p id=\"good\">reuse me</p></body></topic>");
        write(dir, "a.dita", """
            <topic id="a"><title>A</title><body>
              <p conref="common.dita#c/good"/>
              <p conref="common.dita#c/badid"/>
            </body></topic>
            """);

        List<BrokenRef> broken = executor.execute(new ConrefAuditCommand(dir.toString(), null));

        assertThat(broken).singleElement().satisfies(b -> {
            assertThat(b.value()).contains("badid");
            assertThat(b.reason()).contains("badid");
            assertThat(b.source()).endsWith("a.dita");
        });
    }

    @Test
    void ignoresMissingTargetFile(@TempDir Path dir) throws Exception {
        // missing target FILE is check-links's job, not conref-audit's
        write(dir, "a.dita",
            "<topic id=\"a\"><title>A</title><body><p conref=\"nope.dita#x/y\"/></body></topic>");

        assertThat(executor.execute(new ConrefAuditCommand(dir.toString(), null))).isEmpty();
    }

    @Test
    void ignoresNavigationHrefFragments(@TempDir Path dir) throws Exception {
        // an xref/href to a missing element id is a link-check concern, not a
        // conref-reuse one — the audit must not flag it (only conref/keyref).
        write(dir, "common.dita",
            "<topic id=\"c\"><title>C</title><body><p id=\"good\">x</p></body></topic>");
        write(dir, "a.dita",
            "<topic id=\"a\"><title>A</title><body><p><xref href=\"common.dita#c/nope\"/></p></body></topic>");

        assertThat(executor.execute(new ConrefAuditCommand(dir.toString(), null))).isEmpty();
    }

    @Test
    void cleanWhenAllIdsResolve(@TempDir Path dir) throws Exception {
        write(dir, "common.dita",
            "<topic id=\"c\"><title>C</title><body><p id=\"good\">reuse</p></body></topic>");
        write(dir, "a.dita",
            "<topic id=\"a\"><title>A</title><body><p conref=\"common.dita#c/good\"/></body></topic>");

        assertThat(executor.execute(new ConrefAuditCommand(dir.toString(), null))).isEmpty();
    }
}
