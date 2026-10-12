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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Engine-free coverage of {@link BuildDeliverablesCommand}: the precondition paths
 * (no DITA-OT configured, invalid home, unknown deliverable) need no installed
 * DITA-OT. The actual build is covered by the opt-in, DITA-OT-tagged integration.
 */
class BuildDeliverablesCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private void writeProject() throws Exception {
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n<map/>");
        Files.writeString(dir.resolve("project.json"),
            "{ \"deliverables\": [ { \"name\": \"full\", "
            + "\"context\": { \"input\": \"guide.ditamap\" }, "
            + "\"publication\": { \"transtype\": \"html5\" } } ] }");
    }

    @Test
    void failsClearlyWhenDitaOtIsNotConfigured() throws Exception {
        writeProject();
        // The lookup finds an engine installed on this machine; point it at an empty frameworks folder.
        String previous = System.getProperty(DitaOtHome.HOME_PROPERTY);
        System.setProperty(DitaOtHome.HOME_PROPERTY, Files.createDirectories(dir.resolve("no-frameworks")).toString());
        try {
            assertThatThrownBy(() -> executor.execute(
                    new BuildDeliverablesCommand(dir.toString(), null, null, null)))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("DITA-OT")
                .satisfies(e -> assertThat(((CommandException) e).getCode())
                    .isEqualTo(CommandException.ErrorCode.INVALID_ARGUMENT));
        } finally {
            if (previous == null) {
                System.clearProperty(DitaOtHome.HOME_PROPERTY);
            } else {
                System.setProperty(DitaOtHome.HOME_PROPERTY, previous);
            }
        }
    }

    @Test
    void failsWhenDitaOtHomeOverrideIsInvalid() throws Exception {
        writeProject();
        Path bogus = dir.resolve("no-such-dita-ot");
        assertThatThrownBy(() -> executor.execute(
                new BuildDeliverablesCommand(dir.toString(), null, null, bogus.toString())))
            .isInstanceOf(CommandException.class)
            .hasMessageContaining("DITA-OT home is invalid");
    }

    @Test
    void rejectsUnknownDeliverableName() throws Exception {
        writeProject();
        assertThatThrownBy(() -> executor.execute(
                new BuildDeliverablesCommand(dir.toString(), null, "nope", "/tmp")))
            .isInstanceOf(CommandException.class)
            .hasMessageContaining("nope");
    }

    @Test
    void rejectsWhenNoSelectedNameExists() throws Exception {
        writeProject();
        // deliverableNames takes precedence; none match the project's "full" deliverable.
        assertThatThrownBy(() -> executor.execute(new BuildDeliverablesCommand(
                dir.toString(), null, null, "/tmp", java.util.List.of("nope", "missing"))))
            .isInstanceOf(CommandException.class)
            .hasMessageContaining("selected deliverables");
    }
}
