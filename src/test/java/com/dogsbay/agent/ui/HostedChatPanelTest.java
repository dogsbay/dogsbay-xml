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
package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.acp.AcpWire;
import com.dogsbay.agent.acp.AcpWire.PlanEntry;
import com.dogsbay.agent.acp.AcpWire.Update;

class HostedChatPanelTest {

    private final List<String> calls = new ArrayList<>();
    private final HostedChatPanel panel = new HostedChatPanel("Claude", "T1", "commands only",
            new HostedChatPanel.Actions() {
                @Override public void submit(String text) { calls.add("submit:" + text); }
                @Override public void abort() { calls.add("abort"); }
                @Override public void login() { calls.add("login"); }
                @Override public void followChanged(boolean f) { calls.add("follow:" + f); }
                @Override public String mention(java.io.File file) {
                    return file.getName().endsWith(".dita") ? "@topics/" + file.getName() : null;
                }
            });

    @Test
    void rendersMessagesThoughtsToolsAndPlansReadably() throws Exception {
        panel.setReady(true);
        panel.render(new Update.AgentMessage("Hello "));
        panel.render(new Update.AgentMessage("world"));
        panel.render(new Update.AgentThought("let me think\nabout it"));
        panel.render(new Update.ToolCall("c1", "Read a.dita", "read", "pending", null, List.of("/p/a.dita")));
        panel.render(new Update.ToolCallUpdate("c1", "Read a.dita", "read", "completed", null, List.of()));
        panel.render(new Update.Plan(List.of(new PlanEntry("first", "high", "completed"),
                new PlanEntry("second", "low", "pending"))));
        panel.render(new Update.AgentMessage("done"));
        panel.setBusy(false);

        String t = panel.transcriptText();
        assertThat(t).contains("Agent: Hello world");
        assertThat(t).contains("⋯ let me think about it");
        assertThat(t).contains("→ read: Read a.dita  (/p/a.dita)");
        assertThat(t).contains("← read: Read a.dita");
        assertThat(t).contains("✓ first").contains("· second");
        assertThat(t).contains("Agent: done");
    }

    @Test
    void availableCommandsBecomeANote() throws Exception {
        var raw = AcpWire.JSON.readTree("""
            {"sessionUpdate":"available_commands_update","availableCommands":[{"name":"init"},{"name":"review"}]}""");
        panel.render(new Update.Other("available_commands_update", raw));
        assertThat(panel.transcriptText()).contains("Agent commands: /init  /review");

        StringBuilder many = new StringBuilder("{\"sessionUpdate\":\"available_commands_update\",\"availableCommands\":[");
        for (int i = 0; i < 40; i++) {
            many.append(i > 0 ? "," : "").append("{\"name\":\"c").append(i).append("\"}");
        }
        many.append("]}");
        panel.render(new Update.Other("available_commands_update", AcpWire.JSON.readTree(many.toString())));
        assertThat(panel.transcriptText()).contains("40 agent commands available").doesNotContain("/c39");
    }

    @Test
    void loginButtonIsHiddenUntilAsked() {
        panel.setLoginVisible(true);
        panel.setLoginVisible(false);
        assertThat(calls).isEmpty();
    }

    @Test
    void aFinishedMessageIsRestyledFromItsMarkdown() throws Exception {
        panel.setReady(true);
        panel.render(new Update.AgentMessage("All **three** checks:\n\n- DTD"));
        panel.render(new Update.AgentMessage(" ok\n- links ok"));
        assertThat(panel.transcriptText()).contains("**three**");   // raw while streaming
        panel.render(new Update.ToolCall("c2", "Validate", "other", "pending", null, List.of()));
        String t = panel.transcriptText();
        assertThat(t).contains("Agent: All three checks:\n\n• DTD ok\n• links ok\n");
        assertThat(t).doesNotContain("**");
    }

    @Test
    void endOfTurnRendersTheReplyAndNotesInBetweenSurvive() throws Exception {
        panel.setReady(true);
        panel.setBusy(true);
        panel.render(new Update.AgentMessage("## Result\n\n- **DTD** ok"));
        panel.note("Claude is asking permission to run: Validate");
        panel.render(new Update.AgentMessage("Done: `all` good"));
        panel.setBusy(false);
        String t = panel.transcriptText();
        assertThat(t).contains("Result\n\n• DTD ok").contains("• Claude is asking permission to run: Validate")
                .contains("Agent: Done: all good").doesNotContain("**").doesNotContain("`");
    }

    @Test
    void lineOrientedRepliesKeepTheirLinesAndProseIsUntouched() throws Exception {
        panel.setReady(true);
        panel.render(new Update.AgentMessage("Errors found:\nmy_file.xml:12: expected </b>\nother.xml:30: bad"));
        panel.setBusy(false);
        assertThat(panel.transcriptText()).contains("Errors found:\nmy_file.xml:12: expected </b>\nother.xml:30: bad");
    }

    @Test
    void replayedHistoryShowsEachTurnUnderItsOwnSpeaker() throws Exception {
        panel.setReady(true);
        panel.render(new Update.UserMessage("validate a.dita"));
        panel.render(new Update.AgentMessage("**Done.**"));
        panel.render(new Update.UserMessage("now b.dita"));
        panel.render(new Update.AgentMessage("## Result"));
        panel.setBusy(false);
        String t = panel.transcriptText();
        assertThat(t).contains("You: validate a.dita\n").contains("Agent: Done.\n")
                .contains("You: now b.dita\n").contains("Agent: Result");
        assertThat(t).doesNotContain("Done.## Result").doesNotContain("**");
    }

    @Test
    void slashCompletionsComeFromTheAgentsCommands() throws Exception {
        var raw = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        var cmds = raw.putArray("availableCommands");
        cmds.addObject().put("name", "review").put("description", "Review the current change");
        cmds.addObject().put("name", "reflow").put("description", "Reflow sentences");
        cmds.addObject().put("name", "init");
        panel.render(new Update.Other("available_commands_update", raw));

        assertThat(panel.completions("/re")).extracting(HostedChatPanel.Command::name).containsExactly("review", "reflow");
        assertThat(panel.completions("/")).hasSize(3);
        assertThat(panel.completions("/review now")).isEmpty();
        assertThat(panel.completions("review")).isEmpty();
        assertThat(panel.completions("/x")).isEmpty();
    }

    @Test
    void droppedProjectFilesBecomeMentionsAndOthersAreReported() throws Exception {
        panel.setReady(true);
        boolean any = panel.dropFiles(List.of(new java.io.File("/p/topics/a.dita"), new java.io.File("/tmp/x.txt")));
        assertThat(any).isTrue();
        assertThat(panel.inputText()).isEqualTo("@topics/a.dita ");
        assertThat(panel.transcriptText()).contains("Not attached (").contains(": x.txt");
    }

    @Test
    void replayedAttachmentsStayOutOfTheTranscript() throws Exception {
        panel.setReady(true);
        panel.render(new Update.UserMessage("@topics/a.dita  summarize"));
        panel.render(new Update.UserMessage("[@a.dita](file:///p/topics/a.dita)"));
        panel.render(new Update.UserMessage("\n<context ref=\"file:///p/topics/a.dita\">\n<topic id=\"a\"/>\n</context>"));
        panel.render(new Update.UserMessage("[resource: file:///p/topics/a.dita]"));
        panel.render(new Update.UserMessage(""));
        panel.render(new Update.AgentMessage("A short topic."));
        panel.setBusy(false);
        String t = panel.transcriptText();
        assertThat(t).contains("You: @topics/a.dita  summarize\n").contains("Agent: A short topic.");
        assertThat(t).doesNotContain("<context").doesNotContain("file:///").doesNotContain("<topic");
        assertThat(t.split("You: ")).hasSize(2);
        assertThat(HostedChatPanel.isAttachmentEcho("plain question with [brackets] inside")).isFalse();
    }

    @Test
    void textSentFromOutsideGoesAsAPromptOrWaitsInTheBoxWhileBusy() throws Exception {
        panel.setReady(true);
        panel.submitText("  validate topics/a.dita  ");
        assertThat(calls).contains("submit:validate topics/a.dita");
        assertThat(panel.transcriptText()).contains("You: validate topics/a.dita\n");

        // a turn is now running (submit set busy): the next selection waits
        panel.submitText("and check links");
        assertThat(calls).doesNotContain("submit:and check links");
        assertThat(panel.inputText()).isEqualTo("and check links");
        assertThat(panel.transcriptText()).contains("A turn is running; the selection is in the box");
    }
}
