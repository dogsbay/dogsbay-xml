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

import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

class ValidateProjectCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();

    private void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void validatesScopeAndReportsFailingFiles(@TempDir Path dir) throws Exception {
        write(dir, "ok1.xml", "<root><a/></root>");
        write(dir, "ok2.xml", "<root><b>text</b></root>");
        write(dir, "bad.xml", "<root><a></root>"); // not well-formed

        BatchResult<FileValidation> r =
            executor.execute(new ValidateProjectCommand(dir.toString(), "glob:*.xml"));

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.passed()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.isClean()).isFalse();
        assertThat(r.findings()).hasSize(1);
        assertThat(r.findings().get(0).file()).endsWith("bad.xml");
        assertThat(r.findings().get(0).valid()).isFalse();
        assertThat(r.findings().get(0).errors()).isNotEmpty();
        assertThat(r.truncated()).isZero();
    }

    @Test
    void cleanScopeHasNoFindings(@TempDir Path dir) throws Exception {
        write(dir, "a.xml", "<root/>");
        write(dir, "b.xml", "<root><x/></root>");

        BatchResult<FileValidation> r =
            executor.execute(new ValidateProjectCommand(dir.toString(), "glob:*.xml"));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.passed()).isEqualTo(2);
        assertThat(r.isClean()).isTrue();
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void rootScopeWalksAllXmlish(@TempDir Path dir) throws Exception {
        write(dir, "a.xml", "<root/>");
        Files.createDirectories(dir.resolve("sub"));
        write(dir.resolve("sub"), "b.xml", "<root/>");
        write(dir, "notxml.txt", "ignored");

        BatchResult<FileValidation> r =
            executor.execute(new ValidateProjectCommand(dir.toString())); // null scope = root

        assertThat(r.total()).isEqualTo(2); // a.xml + sub/b.xml; .txt ignored
        assertThat(r.isClean()).isTrue();
    }
}
