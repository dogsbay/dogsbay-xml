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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.xml.edit.StructuralEdit.Step;
import com.fasterxml.jackson.databind.ObjectMapper;

/** edit_structure on a real DITA file, as an agent: the Audacity demo's step that edit_document mis-marked. */
class EditStructureCommandTest {

    /** Like the demo topic: valid apart from an error seeded on purpose. */
    private static final String TOPIC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
            <task id="installing-audacity">
              <title>Installing</title>
              <taskbody>
                <context>
                  <p>Available for Windows, macOS and Linux.</p>
                  <x>TODO: confirm minimum OS versions</x>
                </context>
                <steps>
                  <step platform="macos">
                    <cmd>Open the <filepath>.dmg</filepath> file and drag it to <filepath>Applications</filepath>.</cmd>
                  </step>
                  <step platform="linux">
                    <cmd>Install from your distribution's package manager.</cmd>
                  </step>
                </steps>
              </taskbody>
            </task>
            """;

    @TempDir Path dir;
    private Path file;
    private final HeadlessExecutor executor = new HeadlessExecutor();
    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private AgentSession agent;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(dir.resolve(".dogsbay"));
        file = Files.writeString(dir.resolve("installing-audacity.dita"), TOPIC);
        agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
    }

    private <R> R asAgent(Command<R> command) throws CommandException {
        Object[] out = new Object[1];
        CommandException[] failed = new CommandException[1];
        SessionContext.run(agent, () -> {
            try {
                out[0] = executor.execute(command);
            } catch (CommandException e) {
                failed[0] = e;
            }
        });
        if (failed[0] != null) {
            throw failed[0];
        }
        @SuppressWarnings("unchecked")
        R result = (R) out[0];
        return result;
    }

    private List<String> errors(String xml) {
        return ((com.dogsbay.xml.review.ErrorReporting) AgentProposalPolicy.validatorFor(file)).errors(xml);
    }

    @Test
    void theStepEditDocumentMisMarkedLandsAsOneNewStep() throws Exception {
        CommandResult result = asAgent(new EditStructureCommand(file, List.of(new Step("insert",
                "//step[@platform='linux']", "before", "<step platform=\"macos\"><cmd>Keep it in "
                + "<filepath>/Applications</filepath>.</cmd></step>", null, null, null, null)), false));

        String written = Files.readString(file);
        assertThat(result.message()).contains("1 step applied as review proposals");
        assertThat(written).contains("<step platform=\"macos\" status=\"new\" rev=\"ai:claude-acp\"><cmd>Keep it in")
                .contains("      <step platform=\"linux\">\n        <cmd>Install from").doesNotContain("review-wrapper");
        assertThat(errors(written)).as("only the seeded error").isEqualTo(errors(TOPIC));

        asAgent(new ReviewRevertCommand(file.toString()));
        assertThat(Files.readString(file)).as("taken back exactly").isEqualTo(TOPIC);
    }

    @Test
    void stepsThatWouldAddAnErrorAreRefusedAndNothingChanges() throws Exception {
        assertThatThrownBy(() -> asAgent(new EditStructureCommand(file, List.of(new Step("insert",
                "//step[@platform='linux']/cmd", "last-child", "<step><cmd>nested</cmd></step>", null, null, null, null)),
                false))).isInstanceOf(CommandException.class).hasMessageContaining("would add an error")
                .hasMessageContaining("nothing was changed");
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }

    @Test
    void aDryRunSaysWhatWouldHappenAndWritesNothing() throws Exception {
        CommandResult result = asAgent(new EditStructureCommand(file, List.of(
                new Step("delete", "//step[@platform='linux']", null, null, null, null, null, null)), true));
        assertThat(result.message()).startsWith("Dry run, nothing written: 1 step would apply")
                .contains("deleted <step platform=\"linux\"> at line");
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }

    @Test
    void aPersonsStepsApplyWithoutMarks() throws Exception {
        executor.execute(new EditStructureCommand(file, List.of(
                new Step("set-attribute", "//step[@platform='linux']", null, null, "importance", "optional", null, null)),
                false));
        assertThat(Files.readString(file)).contains("<step platform=\"linux\" importance=\"optional\">")
                .doesNotContain("status=");
    }

    @Test
    void aMisspelledStepFieldIsRefusedRatherThanIgnored() throws Exception {
        var params = new ObjectMapper().readTree("""
                {"file": "%s", "steps": [{"op": "delete", "selector": "//step[@platform='linux']"}]}
                """.formatted(file.toString().replace("\\", "\\\\")));
        assertThatThrownBy(() -> new JsonRpcHandler(executor).dispatch("edit-structure", params))
                .hasMessageContaining("step 1 has no field \"selector\"");
        var container = new ObjectMapper().readTree("""
                {"file": "%s", "steps": [{"op": "set-text", "select": "//cmd[1]", "text": {"b": "x"}}]}
                """.formatted(file.toString().replace("\\", "\\\\")));
        assertThatThrownBy(() -> new JsonRpcHandler(executor).dispatch("edit-structure", container))
                .hasMessageContaining("field \"text\" must be a string");
        var dryRun = new ObjectMapper().readTree("""
                {"file": "%s", "dryRun": "yes", "steps": [{"op": "delete", "select": "//step[@platform='linux']"}]}
                """.formatted(file.toString().replace("\\", "\\\\")));
        assertThatThrownBy(() -> new JsonRpcHandler(executor).dispatch("edit-structure", dryRun))
                .hasMessageContaining("dryRun must be true or false");
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }
}
