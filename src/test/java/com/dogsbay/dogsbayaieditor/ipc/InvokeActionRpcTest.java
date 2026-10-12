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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.ClickButtonCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.LocateCommand;
import com.dogsbay.dogsbayaieditor.commands.LocateNodeCommand;
import com.dogsbay.dogsbayaieditor.commands.LocateRangeCommand;
import com.dogsbay.dogsbayaieditor.commands.SetCursorCommand;
import com.dogsbay.dogsbayaieditor.commands.RevealCommand;
import com.dogsbay.dogsbayaieditor.commands.RevealNodeCommand;
import com.dogsbay.dogsbayaieditor.commands.InvokeActionCommand;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;
import com.dogsbay.dogsbayaieditor.commands.SelectPanelCommand;
import com.dogsbay.dogsbayaieditor.commands.TerminalRunCommand;
import com.dogsbay.dogsbayaieditor.commands.TypeTextCommand;
import com.dogsbay.dogsbayaieditor.commands.WaitIdleCommand;

/** Running a menu command by its id, and pressing a dialog's button by its name, over the integration server. */
class InvokeActionRpcTest {

    private static final ObjectMapper M = new ObjectMapper();

    private ObjectNode params(String id) {
        ObjectNode params = M.createObjectNode();
        params.put("id", id);
        return params;
    }

    @Test
    void buildsTheCommandFromTheId() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        new JsonRpcHandler(recorder).dispatch("invokeAction", params("XML:  Validate"));

        assertThat(recorder.calls).hasSize(1);
        assertThat(recorder.calls.get(0).command()).isInstanceOf(InvokeActionCommand.class);
        assertThat(((InvokeActionCommand) recorder.calls.get(0).command()).id()).isEqualTo("XML:  Validate");
    }

    @Test
    void theMethodMapAgreesWithWhatDispatchBuilds() {
        // the map is hand-written beside the dispatch, so the two drift unless something checks
        assertThat(JsonRpcHandler.commandClassOf("invokeAction")).isEqualTo(InvokeActionCommand.class);
    }

    @Test
    void buildsThePanelAndTerminalCommandsToo() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        JsonRpcHandler handler = new JsonRpcHandler(recorder);

        ObjectNode panel = M.createObjectNode();
        panel.put("panel", "errors");
        handler.dispatch("selectPanel", panel);

        ObjectNode terminal = M.createObjectNode();
        terminal.put("command", "PS1='$ '; clear");
        handler.dispatch("terminalRun", terminal);

        assertThat(recorder.calls).hasSize(2);
        assertThat(((SelectPanelCommand) recorder.calls.get(0).command()).panel()).isEqualTo("errors");
        assertThat(((TerminalRunCommand) recorder.calls.get(1).command()).command()).isEqualTo("PS1='$ '; clear");
        assertThat(JsonRpcHandler.commandClassOf("selectPanel")).isEqualTo(SelectPanelCommand.class);
        assertThat(JsonRpcHandler.commandClassOf("terminalRun")).isEqualTo(TerminalRunCommand.class);
    }

    @Test
    void buildsTheWaitCommandWithAndWithoutPeriods() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        JsonRpcHandler handler = new JsonRpcHandler(recorder);

        handler.dispatch("waitIdle", M.createObjectNode());
        ObjectNode timed = M.createObjectNode();
        timed.put("quietMs", 40);
        timed.put("timeoutMs", 3000);
        handler.dispatch("waitIdle", timed);

        WaitIdleCommand plain = (WaitIdleCommand) recorder.calls.get(0).command();
        assertThat(plain.quiet()).isEqualTo(WaitIdleCommand.DEFAULT_QUIET_MS);
        WaitIdleCommand asked = (WaitIdleCommand) recorder.calls.get(1).command();
        assertThat(asked.quiet()).isEqualTo(40);
        assertThat(asked.timeout()).isEqualTo(3000);
        assertThat(plain.timeout()).isEqualTo(WaitIdleCommand.DEFAULT_TIMEOUT_MS);
        assertThat(JsonRpcHandler.commandClassOf("waitIdle")).isEqualTo(WaitIdleCommand.class);
    }

    @Test
    void refusesAPeriodThatIsNotANumberRatherThanReadingItAsNoWaitAtAll() {
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());
        for (String bad : new String[] { "\"soon\"", "null", "true", "3000000000" }) {
            ObjectNode params = M.createObjectNode();
            params.set("quietMs", M.getNodeFactory().pojoNode(null));
            try {
                params = (ObjectNode) M.readTree("{\"quietMs\": " + bad + "}");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            ObjectNode sent = params;
            if (bad.equals("null")) {
                // null means "not given", which is the default rather than an error
                continue;
            }
            assertThatThrownBy(() -> handler.dispatch("waitIdle", sent)).as(bad)
                    .hasMessageContaining("must be a number of milliseconds");
        }
    }

    @Test
    void buildsTheTypingCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("text", "<p>Hello</p>");
        params.put("cps", 8);

        new JsonRpcHandler(recorder).dispatch("typeText", params);

        TypeTextCommand cmd = (TypeTextCommand) recorder.calls.get(0).command();
        assertThat(cmd.text()).isEqualTo("<p>Hello</p>");
        assertThat(cmd.rate()).isEqualTo(8);
        assertThat(JsonRpcHandler.commandClassOf("typeText")).isEqualTo(TypeTextCommand.class);
    }

    @Test
    void typingWithoutTextIsAnArgumentError() {
        assertThatThrownBy(() -> new JsonRpcHandler(new RecordingExecutor())
                .dispatch("typeText", M.createObjectNode()))
                .hasMessageContaining("text");
    }

    @Test
    void withoutAnEditorItSaysSoRatherThanFailingObscurely() {
        assertThatThrownBy(() -> new JsonRpcHandler(new HeadlessExecutor())
                .dispatch("invokeAction", params("XML:  Validate")))
                .hasMessageContaining("editor");
    }

    @Test
    void buildsTheClickButtonCommandFromTheLabel() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("label", "Set active");
        new JsonRpcHandler(recorder).dispatch("clickButton", params);

        assertThat(recorder.calls).hasSize(1);
        ClickButtonCommand cmd = (ClickButtonCommand) recorder.calls.get(0).command();
        assertThat(cmd.label()).isEqualTo("Set active");
        assertThat(cmd.window()).as("no window named — the active one is meant").isNull();
        assertThat(cmd.press()).as("pressing is what a caller means unless it says otherwise").isTrue();
    }

    @Test
    void clickButtonCarriesTheWindowWhenOneIsNamed() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("label", "Close");
        params.put("window", "Manage DITA Deliverables");
        new JsonRpcHandler(recorder).dispatch("clickButton", params);

        ClickButtonCommand cmd = (ClickButtonCommand) recorder.calls.get(0).command();
        assertThat(cmd.window()).isEqualTo("Manage DITA Deliverables");
    }

    @Test
    void clickButtonNeedsALabel() {
        RecordingExecutor recorder = new RecordingExecutor();
        JsonRpcHandler handler = new JsonRpcHandler(recorder);

        assertThatThrownBy(() -> handler.dispatch("clickButton", M.createObjectNode()))
                .hasMessageContaining("label");
    }

    @Test
    void clickButtonCanBeAskedToLocateWithoutPressing() throws Exception {
        // A recording moves its own pointer to the button and clicks it there, so a viewer sees which button
        // is being pressed rather than a dialog closing by itself.
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("label", "OK");
        params.put("press", false);
        new JsonRpcHandler(recorder).dispatch("clickButton", params);

        assertThat(((ClickButtonCommand) recorder.calls.get(0).command()).press()).isFalse();
    }

    @Test
    void theMethodMapKnowsClickButtonToo() {
        assertThat(JsonRpcHandler.commandClassOf("clickButton")).isEqualTo(ClickButtonCommand.class);
    }

    @Test
    void buildsTheLocateCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("text", "Concept");
        params.put("what", "row");
        new JsonRpcHandler(recorder).dispatch("locate", params);

        LocateCommand cmd = (LocateCommand) recorder.calls.get(0).command();
        assertThat(cmd.text()).isEqualTo("Concept");
        assertThat(cmd.what()).isEqualTo("row");
        assertThat(cmd.window()).isNull();
    }

    @Test
    void locateCarriesTheWindowWhenOneIsNamed() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("text", "Value");
        params.put("what", "field");
        params.put("window", "Set Attribute");
        new JsonRpcHandler(recorder).dispatch("locate", params);

        assertThat(((LocateCommand) recorder.calls.get(0).command()).window()).isEqualTo("Set Attribute");
    }

    @Test
    void locateCarriesTheWholeOfWhatWasAsked() throws Exception {
        // What a locate needs depends on which kind it is — a cell is named by a column and a row number, a
        // place in the document by an xpath — so the boundary carries the parameters and the executor is
        // what decides whether enough of them came.
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "cell");
        params.put("column", "Name");
        params.put("row", "last");
        new JsonRpcHandler(recorder).dispatch("locate", params);

        LocateCommand cmd = (LocateCommand) recorder.calls.get(0).command();
        assertThat(cmd.what()).isEqualTo("cell");
        assertThat(cmd.column()).isEqualTo("Name");
        assertThat(cmd.row()).as("a cell is reached by where it is, so the row is its own parameter")
                .isEqualTo("last");
        assertThat(cmd.text()).as("and an empty cell has no text to be named by").isNull();
    }

    @Test
    void aCellsRowMayBeWrittenAsANumber() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "cell");
        params.put("column", "Output");
        params.put("row", 3);
        new JsonRpcHandler(recorder).dispatch("locate", params);

        assertThat(((LocateCommand) recorder.calls.get(0).command()).row()).isEqualTo("3");
    }

    @Test
    void locateCarriesAnOffset() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "text");
        params.put("text", "Save the file");
        params.put("offset", 4);
        new JsonRpcHandler(recorder).dispatch("locate", params);

        assertThat(((LocateCommand) recorder.calls.get(0).command()).offset()).isEqualTo(4);
    }

    @Test
    void aWholeNumberParameterIsCheckedRatherThanCoerced() throws Exception {
        // asInt() reads a string or a decimal as 0, so a driver sending "nth": "2" would be told its name
        // was ambiguous and never learn its parameter had been dropped
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());
        for (String method : new String[] { "locate", "reveal" }) {
            for (String name : new String[] { "nth", "offset" }) {
                ObjectNode params = M.createObjectNode();
                params.put("what", "text");
                params.put("text", "Save");
                params.put(name, "2");
                assertThatThrownBy(() -> handler.dispatch(method, params))
                        .as(method + " " + name)
                        .hasMessageContaining(name).hasMessageContaining("whole number");
            }
        }
    }

    @Test
    void buildsTheLocateRangeCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("start", 120);
        params.put("end", 154);
        new JsonRpcHandler(recorder).dispatch("locateRange", params);

        LocateRangeCommand cmd = (LocateRangeCommand) recorder.calls.get(0).command();
        assertThat(cmd.start()).isEqualTo(120);
        assertThat(cmd.end()).isEqualTo(154);
    }

    @Test
    void locateRangeRefusesAMissingEndRatherThanReadingItAsZero() throws Exception {
        // wholeNumber answers 0 for an absent parameter, which here would be refused as an empty
        // range instead of as the omission it is.
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());
        for (String missing : new String[] { "start", "end" }) {
            ObjectNode params = M.createObjectNode();
            params.put(missing.equals("start") ? "end" : "start", 10);
            assertThatThrownBy(() -> handler.dispatch("locateRange", params))
                    .as("without " + missing)
                    .hasMessageContaining(missing).hasMessageContaining("Missing required parameter");
        }
    }

    @Test
    void locateRangeRefusesAFractionRatherThanTruncatingIt() throws Exception {
        // canConvertToInt() is true for 1.5 and asInt() answers 1, so without the second check a
        // driver asking for 1.5 would be given a box over the wrong characters and told nothing.
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());
        for (String name : new String[] { "start", "end" }) {
            ObjectNode params = M.createObjectNode();
            params.put("start", 10);
            params.put("end", 20);
            params.put(name, 1.5);
            assertThatThrownBy(() -> handler.dispatch("locateRange", params))
                    .as(name + " as 1.5")
                    .hasMessageContaining(name).hasMessageContaining("whole number");
        }
    }

    @Test
    void aWholeNumberWrittenWithADecimalPointIsStillAWholeNumber() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("start", 10.0);
        params.put("end", 20.0);
        new JsonRpcHandler(recorder).dispatch("locateRange", params);

        LocateRangeCommand cmd = (LocateRangeCommand) recorder.calls.get(0).command();
        assertThat(cmd.start()).isEqualTo(10);
        assertThat(cmd.end()).isEqualTo(20);
    }

    @Test
    void locateRangeRefusesAStringRatherThanReadingItAsZero() throws Exception {
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());
        ObjectNode params = M.createObjectNode();
        params.put("start", "10");
        params.put("end", 20);
        assertThatThrownBy(() -> handler.dispatch("locateRange", params))
                .hasMessageContaining("start").hasMessageContaining("whole number");
    }

    @Test
    void setCursorRefusesAPositionItCannotRead() throws Exception {
        // It used to call get("position").asInt() directly: absent threw an NPE, surfacing as an
        // internal error, and "12" was read as 0 — the caret quietly going to the document's start.
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());

        assertThatThrownBy(() -> handler.dispatch("setCursor", M.createObjectNode()))
                .as("no position at all")
                .hasMessageContaining("Missing required parameter").hasMessageContaining("position");

        ObjectNode asText = M.createObjectNode();
        asText.put("position", "12");
        assertThatThrownBy(() -> handler.dispatch("setCursor", asText))
                .as("a position in quotes")
                .hasMessageContaining("position").hasMessageContaining("whole number");

        ObjectNode asFraction = M.createObjectNode();
        asFraction.put("position", 12.5);
        assertThatThrownBy(() -> handler.dispatch("setCursor", asFraction))
                .as("a fractional position")
                .hasMessageContaining("position").hasMessageContaining("whole number");
    }

    @Test
    void setCursorStillTakesAPositionItCanRead() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("position", 42);
        params.put("relative", true);
        new JsonRpcHandler(recorder).dispatch("setCursor", params);

        SetCursorCommand cmd = (SetCursorCommand) recorder.calls.get(0).command();
        assertThat(cmd.position()).isEqualTo(42);
        assertThat(cmd.relative()).isTrue();
    }

    @Test
    void buildsTheLocateNodeCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("select", "//tgroup[1]/@cols");
        params.put("part", "value");
        params.put("nth", 2);
        ObjectNode namespaces = params.putObject("namespaces");
        namespaces.put("n", "urn:n");
        new JsonRpcHandler(recorder).dispatch("locateNode", params);

        LocateNodeCommand cmd = (LocateNodeCommand) recorder.calls.get(0).command();
        assertThat(cmd.select()).isEqualTo("//tgroup[1]/@cols");
        assertThat(cmd.part()).isEqualTo("value");
        assertThat(cmd.nth()).isEqualTo(2);
        assertThat(cmd.namespaces()).containsEntry("n", "urn:n");
    }

    @Test
    void locateNodeNeedsNeitherPartNorNamespaces() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("select", "//title");
        new JsonRpcHandler(recorder).dispatch("locateNode", params);

        LocateNodeCommand cmd = (LocateNodeCommand) recorder.calls.get(0).command();
        assertThat(cmd.part()).as("the executor defaults this to the element").isNull();
        assertThat(cmd.namespaces()).isEmpty();
        assertThat(cmd.nth()).isZero();
    }

    @Test
    void locateNodeRefusesANamespacesParameterOfTheWrongShape() throws Exception {
        JsonRpcHandler handler = new JsonRpcHandler(new RecordingExecutor());

        ObjectNode asList = M.createObjectNode();
        asList.put("select", "//d:p");
        asList.putArray("namespaces").addObject().put("prefix", "d");
        assertThatThrownBy(() -> handler.dispatch("locateNode", asList))
                .as("a list of pairs, which is the natural wrong guess")
                .hasMessageContaining("maps a prefix to a URI");

        ObjectNode asString = M.createObjectNode();
        asString.put("select", "//d:p");
        asString.put("namespaces", "d=urn:d");
        assertThatThrownBy(() -> handler.dispatch("locateNode", asString))
                .hasMessageContaining("maps a prefix to a URI");

        ObjectNode nestedValue = M.createObjectNode();
        nestedValue.put("select", "//d:p");
        nestedValue.putObject("namespaces").putObject("d").put("uri", "urn:d");
        assertThatThrownBy(() -> handler.dispatch("locateNode", nestedValue))
                .as("a nested object would asText() to the empty URI")
                .hasMessageContaining("must be a string");
    }

    @Test
    void buildsTheRevealNodeCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("select", "//tgroup[1]");
        params.put("part", "start-tag");
        params.put("align", "top");
        new JsonRpcHandler(recorder).dispatch("revealNode", params);

        RevealNodeCommand cmd = (RevealNodeCommand) recorder.calls.get(0).command();
        assertThat(cmd.select()).isEqualTo("//tgroup[1]");
        assertThat(cmd.part()).isEqualTo("start-tag");
        assertThat(cmd.align()).isEqualTo("top");
    }

    @Test
    void revealTakesAnAlignmentToo() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "text");
        params.put("text", "Save");
        params.put("align", "center");
        new JsonRpcHandler(recorder).dispatch("reveal", params);

        assertThat(((RevealCommand) recorder.calls.get(0).command()).align()).isEqualTo("center");
    }

    @Test
    void anAlignmentIsOptionalOnBoth() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode reveal = M.createObjectNode();
        reveal.put("what", "text");
        reveal.put("text", "Save");
        new JsonRpcHandler(recorder).dispatch("reveal", reveal);
        assertThat(((RevealCommand) recorder.calls.get(0).command()).align())
                .as("the executor reads a missing alignment as nearest").isNull();

        ObjectNode node = M.createObjectNode();
        node.put("select", "//title");
        new JsonRpcHandler(recorder).dispatch("revealNode", node);
        assertThat(((RevealNodeCommand) recorder.calls.get(1).command()).align()).isNull();
    }

    @Test
    void theMethodMapKnowsRevealNode() {
        assertThat(JsonRpcHandler.commandClassOf("revealNode")).isEqualTo(RevealNodeCommand.class);
    }

    @Test
    void theMethodMapKnowsLocateNode() {
        assertThat(JsonRpcHandler.commandClassOf("locateNode")).isEqualTo(LocateNodeCommand.class);
    }

    @Test
    void theMethodMapKnowsLocateRange() {
        assertThat(JsonRpcHandler.commandClassOf("locateRange")).isEqualTo(LocateRangeCommand.class);
    }

    @Test
    void revealTakesTheSameOffsetLocateDoes() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "text");
        params.put("text", "Save");
        params.put("offset", 2);
        new JsonRpcHandler(recorder).dispatch("reveal", params);

        assertThat(((RevealCommand) recorder.calls.get(0).command()).offset()).isEqualTo(2);
    }

    @Test
    void buildsTheRevealCommand() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        ObjectNode params = M.createObjectNode();
        params.put("what", "panel");
        params.put("panel", "Project Validation");
        params.put("to", "end");
        new JsonRpcHandler(recorder).dispatch("reveal", params);

        RevealCommand cmd = (RevealCommand) recorder.calls.get(0).command();
        assertThat(cmd.what()).isEqualTo("panel");
        assertThat(cmd.panel()).isEqualTo("Project Validation");
        assertThat(cmd.to()).isEqualTo("end");
    }

    @Test
    void theMethodMapKnowsRevealToo() {
        assertThat(JsonRpcHandler.commandClassOf("reveal")).isEqualTo(RevealCommand.class);
    }

    @Test
    void theMethodMapKnowsLocateToo() {
        assertThat(JsonRpcHandler.commandClassOf("locate")).isEqualTo(LocateCommand.class);
    }
}
