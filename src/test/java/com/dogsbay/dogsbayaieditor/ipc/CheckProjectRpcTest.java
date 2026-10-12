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

package com.dogsbay.dogsbayaieditor.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.CheckProjectCommand;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;

/**
 * What an agent asks for over JSON-RPC is what gets run.
 *
 * <p>The existing guards check that {@code check} maps to {@link CheckProjectCommand} and that the tool schema agrees
 * with the dispatch; neither looks at the components. Swapping {@code schematron} and {@code deliverable}, or flipping
 * the default for {@code build}, would pass all of them — and flipping that default is the difference between a check
 * that builds and one that quietly does not.
 */
class CheckProjectRpcTest {

    private static final ObjectMapper M = new ObjectMapper();

    private CheckProjectCommand dispatch(ObjectNode params) throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        new JsonRpcHandler(recorder).dispatch("check", params);
        return (CheckProjectCommand) recorder.calls.get(0).command();
    }

    @Test
    void everyArgumentLandsOnTheComponentItNames() throws Exception {
        ObjectNode params = M.createObjectNode();
        params.put("root", "/p");
        params.put("map", "root.ditamap");
        params.put("schematron", "house.sch");
        params.put("deliverable", "full");
        params.put("output", "out/");
        params.put("build", false);

        CheckProjectCommand cmd = dispatch(params);

        assertThat(cmd.root()).isEqualTo("/p");
        assertThat(cmd.rootMap()).isEqualTo("root.ditamap");
        assertThat(cmd.schematron()).isEqualTo("house.sch");
        assertThat(cmd.deliverable()).isEqualTo("full");
        assertThat(cmd.outputBaseDir()).isEqualTo("out/");
        assertThat(cmd.build()).isFalse();
        assertThat(cmd.ditaOtHome()).as("an agent's command arrives without one; the editor injects it").isNull();
    }

    @Test
    void aCheckThatSaysNothingAboutBuildingStillBuilds() throws Exception {
        ObjectNode params = M.createObjectNode();
        params.put("root", "/p");

        CheckProjectCommand cmd = dispatch(params);

        assertThat(cmd.build()).isTrue();
        assertThat(cmd.rootMap()).isNull();
        assertThat(cmd.deliverable()).isNull();
    }

    @Test
    void theMethodMapsToTheCommandItRuns() {
        assertThat(JsonRpcHandler.commandClassOf("check")).isEqualTo(CheckProjectCommand.class);
    }
}
