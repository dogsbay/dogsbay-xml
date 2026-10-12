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

package com.dogsbay.xml.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.edit.StructuralEdit.Step;
import com.dogsbay.xml.review.ProposalOps;

/** Ordered structural edits, plain and as review proposals. */
class StructuralEditTest {

    private static final String AUTHOR = "ai:claude-acp";

    private static final String TOPIC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
            <task id="installing">
              <title>Installing</title>
              <taskbody>
                <steps>
                  <step platform="windows">
                    <cmd>Run the installer.</cmd>
                  </step>
                  <step platform="macos">
                    <cmd>Drag it to <filepath>Applications</filepath>.</cmd>
                  </step>
                  <step platform="linux">
                    <cmd>Use the package manager.</cmd>
                  </step>
                </steps>
              </taskbody>
            </task>
            """;

    private static Step step(String op, String select) {
        return new Step(op, select, null, null, null, null, null, null);
    }

    private static Step insert(String select, String position, String xml) {
        return new Step("insert", select, position, xml, null, null, null, null);
    }

    private static String plain(List<Step> steps) throws Exception {
        return StructuralEdit.apply(TOPIC, steps, null).text();
    }

    /**
     * Apply as proposals and check the marks are exact: accepting all of them gives the plain edit, and
     * rejecting all of them gives back the original.
     */
    private static String reviewed(List<Step> steps) throws Exception {
        return reviewed(TOPIC, steps);
    }

    private static String reviewed(String source, List<Step> steps) throws Exception {
        String marked = StructuralEdit.apply(source, steps, AUTHOR).text();
        assertThat(ProposalOps.acceptAll(marked, AUTHOR)).as("accept all = the plain edit:\n" + marked)
                .isEqualTo(StructuralEdit.apply(source, steps, null).text());
        assertThat(ProposalOps.rejectAll(marked, AUTHOR)).as("reject all = the original:\n" + marked)
                .isEqualTo(source);
        return marked;
    }

    private static Step op(String op, String select, String name, String value, String text) {
        return new Step(op, select, null, null, name, value, text, null);
    }

    @Test
    void insertsAStepBeforeItsSiblingAsANewStepNotAWrapper() throws Exception {
        // The edit that edit_document's diff marked as a wrapper nesting the Linux step (2026-09-18)
        List<Step> steps = List.of(insert("//step[@platform='linux']", "before",
                "<step platform=\"macos\"><cmd>Keep it in <filepath>/Applications</filepath>.</cmd></step>"));

        assertThat(plain(steps)).contains("""
                      <step platform="macos"><cmd>Keep it in <filepath>/Applications</filepath>.</cmd></step>
                      <step platform="linux">
                """);
        String marked = reviewed(steps);
        assertThat(marked).contains("<step platform=\"macos\" status=\"new\" rev=\"ai:claude-acp\"><cmd>Keep it in")
                .doesNotContain("review-wrapper");
    }

    @Test
    void keepsEverythingOutsideTheEditByteForByte() throws Exception {
        String result = plain(List.of(insert("//step[@platform='windows']", "after", "<step><cmd>Restart.</cmd></step>")));
        int at = TOPIC.indexOf("      <step platform=\"macos\">");
        assertThat(result).startsWith(TOPIC.substring(0, TOPIC.indexOf("      <step platform=\"macos\">")))
                .endsWith(TOPIC.substring(at));
        assertThat(result).contains("      </step>\n      <step><cmd>Restart.</cmd></step>\n      <step platform=\"macos\">");
    }

    @Test
    void insertsAsFirstOrLastChildWithTheChildrensIndentation() throws Exception {
        String first = plain(List.of(insert("//steps", "first-child", "<step><cmd>Download it.</cmd></step>")));
        assertThat(first).contains("<steps>\n      <step><cmd>Download it.</cmd></step>\n      <step platform=\"windows\">");
        String last = plain(List.of(insert("//steps", "last-child", "<step><cmd>Start it.</cmd></step>")));
        assertThat(last).contains("      </step>\n      <step><cmd>Start it.</cmd></step>\n    </steps>");
        reviewed(List.of(insert("//steps", "last-child", "<step><cmd>Start it.</cmd></step>")));
    }

    @Test
    void indentsAMultiLineFragmentToWhereItGoes() throws Exception {
        String result = plain(List.of(insert("#installing", "last-child", "<prolog>\n  <author>Docs team</author>\n</prolog>")));
        assertThat(result).contains("  </taskbody>\n  <prolog>\n    <author>Docs team</author>\n  </prolog>\n</task>");
    }

    @Test
    void deletesWholeLinesAndMarksADeletionInPlace() throws Exception {
        List<Step> steps = List.of(step("delete", "//step[@platform='linux']"));
        assertThat(plain(steps)).doesNotContain("linux").contains("      </step>\n    </steps>");
        String marked = reviewed(steps);
        assertThat(marked).contains("<step platform=\"linux\" status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">");
    }

    @Test
    void replacesMovesWrapsAndUnwrapsExactly() throws Exception {
        reviewed(List.of(new Step("replace", "//step[@platform='linux']/cmd", null, "<cmd>Install the package.</cmd>",
                null, null, null, null)));
        List<Step> move = List.of(new Step("move", "//step[@platform='linux']", "before", null, null, null, null,
                "//step[@platform='windows']"));
        assertThat(plain(move).indexOf("linux")).isLessThan(plain(move).indexOf("windows"));
        reviewed(move);
        List<Step> wrap = List.of(new Step("wrap", "//step[@platform='macos']/cmd/filepath", null,
                "<b/>", null, null, null, null));
        assertThat(plain(wrap)).contains("Drag it to <b><filepath>Applications</filepath></b>.");
        reviewed(wrap);
        List<Step> unwrap = List.of(step("unwrap", "//step[@platform='macos']/cmd/filepath"));
        assertThat(plain(unwrap)).contains("<cmd>Drag it to Applications.</cmd>");
    }

    @Test
    void changesNamesAttributesAndTextInPlace() throws Exception {
        List<Step> attr = List.of(new Step("set-attribute", "//step[@platform='macos']", null, null, "platform", "mac",
                null, null));
        assertThat(plain(attr)).contains("<step platform=\"mac\">");
        reviewed(attr);
        assertThat(plain(List.of(new Step("remove-attribute", "//step[@platform='macos']", null, null, "platform", null,
                null, null)))).contains("      <step>\n        <cmd>Drag it to");
        assertThat(plain(List.of(new Step("rename", "//step[@platform='linux']/cmd", null, null, "info", null, null,
                null)))).contains("<info>Use the package manager.</info>");
        List<Step> text = List.of(new Step("set-text", "//step[@platform='linux']/cmd", null, null, null, null,
                "Install it with apt & dnf.", null));
        assertThat(plain(text)).contains("<cmd>Install it with apt &amp; dnf.</cmd>");
        reviewed(text);
    }

    @Test
    void laterStepsSeeEarlierOnesIncludingWhatTheyInserted() throws Exception {
        // Ordered steps: insert an element, then change it, in one batch
        List<Step> steps = List.of(
                insert("//step[@platform='linux']", "after", "<step><cmd>Start it.</cmd></step>"),
                new Step("set-attribute", "//step[not(@platform)]", null, null, "importance", "optional", null, null),
                step("delete", "//step[@platform='windows']"));
        assertThat(plain(steps)).contains("<step importance=\"optional\"><cmd>Start it.</cmd></step>")
                .doesNotContain("windows");
        String marked = reviewed(steps);
        String tag = marked.substring(marked.lastIndexOf("<step", marked.indexOf("<cmd>Start it.")),
                marked.indexOf("<cmd>Start it."));
        assertThat(tag).as("a new element changed later in the batch is still just new")
                .contains("importance=\"optional\"").contains("status=\"new\"").doesNotContain("deleted");
    }

    @Test
    void anElementTheBatchDeletedIsGoneForLaterSteps() {
        assertThatThrownBy(() -> StructuralEdit.apply(TOPIC, List.of(
                step("delete", "//step[@platform='linux']"),
                step("delete", "//step[@platform='linux']")), AUTHOR))
                .hasMessageContaining("Step 2 (delete)").hasMessageContaining("matches 0 elements");
    }

    @Test
    void refusesASelectionThatIsNotExactlyOneElement() {
        assertThatThrownBy(() -> plain(List.of(step("delete", "//step"))))
                .hasMessageContaining("matches 3 elements; it must match exactly one");
        assertThatThrownBy(() -> plain(List.of(step("delete", "//step/@platform"))))
                .hasMessageContaining("must select an element");
        assertThatThrownBy(() -> plain(List.of(step("delete", "//step["))))
                .hasMessageContaining("not a valid XPath");
    }

    @Test
    void appliesAllOrNothingAndSaysWhichStepFailed() {
        assertThatThrownBy(() -> plain(List.of(
                step("delete", "//step[@platform='linux']"),
                insert("//steps", "sideways", "<step/>"))))
                .isInstanceOf(StructuralEdit.StepException.class)
                .hasMessageContaining("Step 2 (insert)").hasMessageContaining("unknown position \"sideways\"");
        assertThatThrownBy(() -> plain(List.of(insert("//steps", "last-child", "<step><cmd>unclosed</step>"))))
                .hasMessageContaining("not well-formed");
    }

    @Test
    void testStepsCheckWhatTheAgentReadIsStillThere() throws Exception {
        assertThat(plain(List.of(
                new Step("test", "//step[@platform='linux']/cmd", null, null, null, null, "Use the  package manager.", null),
                new Step("test", "//step[@platform='macos']", null, null, "platform", "macos", null, null))))
                .isEqualTo(TOPIC);
        assertThatThrownBy(() -> plain(List.of(new Step("test", "//step[@platform='linux']/cmd", null, null, null, null,
                "Use snap.", null)))).hasMessageContaining("test failed: the text of <cmd>");
    }

    @Test
    void refusesToRemoveTheDocumentElementOrSetTheTextOfStructure() {
        assertThatThrownBy(() -> plain(List.of(step("delete", "/task")))).hasMessageContaining("document element");
        assertThatThrownBy(() -> plain(List.of(new Step("set-text", "//steps", null, null, null, null, "x", null))))
                .hasMessageContaining("holds elements");
    }
    // ── cases from the review (2026-09-18) ──────────────────────────────

    @Test
    void marksLeaveTheRestOfATagAsWritten() throws Exception {
        String source = TOPIC.replace("<step platform=\"linux\">",
                "<step platform='linux'\n            importance=\"optional\" outputclass=\"&#169;\">");
        String marked = reviewed(source, List.of(step("delete", "//step[@platform='linux']")));
        assertThat(marked).contains("<step platform='linux'\n            importance=\"optional\"");
    }

    @Test
    void keepsTheDocumentsLineEndingsAndTabs() throws Exception {
        String crlf = TOPIC.replace("\n", "\r\n");
        String result = StructuralEdit.apply(crlf, List.of(insert("//steps", "last-child",
                "<step>\n  <cmd>Start it.</cmd>\n</step>")), null).text();
        assertThat(result.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
        reviewed(crlf, List.of(insert("//steps", "last-child", "<step><cmd>Start it.</cmd></step>")));

        String tabs = TOPIC.replaceAll("(?m)^((?:  )+)", "$1").replace("  ", "\t");
        String tabbed = StructuralEdit.apply(tabs, List.of(insert("//steps", "first-child",
                "<step>\n  <cmd>Download it.</cmd>\n</step>")), null).text();
        assertThat(tabbed).contains("<steps>\n\t\t\t<step>\n\t\t\t\t<cmd>Download it.</cmd>\n\t\t\t</step>\n\t\t\t<step");
    }

    @Test
    void neverReindentsCodeOrTextWithLineBreaks() throws Exception {
        String code = "<example><codeblock>if x:\n    run()\n</codeblock></example>";
        String result = plain(List.of(insert("//step[@platform='linux']", "after", code)));
        assertThat(result).contains(code);
        String prose = "<info>Two\nlines.</info>";
        assertThat(plain(List.of(insert("//step[@platform='linux']/cmd", "after", prose)))).contains(prose);
    }

    @Test
    void renamesAnElementWhoseEndTagHasSpaces() throws Exception {
        String source = TOPIC.replace("<cmd>Use the package manager.</cmd>", "<cmd>Use the package manager.</cmd >");
        assertThat(StructuralEdit.apply(source, List.of(op("rename", "//step[@platform='linux']/cmd", "info", null,
                null)), null).text()).contains("<info>Use the package manager.</info>");
    }

    @Test
    void anElementTheBatchInsertedStaysMarkedNewWhenReplacedWrappedOrMoved() throws Exception {
        Step add = insert("//step[@platform='linux']", "after", "<step id=\"s4\"><cmd>Start it.</cmd></step>");
        for (Step then : List.of(
                new Step("replace", "#s4", null, "<step id=\"s4\"><cmd>Open it.</cmd></step>", null, null, null, null),
                new Step("move", "#s4", "before", null, null, null, null, "//step[@platform='windows']"),
                new Step("wrap", "//step[@id='s4']/cmd", null, "<b/>", null, null, null, null),
                op("set-attribute", "#s4", "importance", "optional", null))) {
            String marked = reviewed(List.of(add, then));
            String tag = marked.substring(marked.indexOf("<step id=\"s4\""), marked.indexOf(">", marked.indexOf("<step id=\"s4\"")));
            assertThat(tag).as(then.op()).contains("status=\"new\"").doesNotContain("deleted");
            assertThat(tag.split("status=", -1)).as(then.op() + ": marked once").hasSize(2);
        }
    }

    @Test
    void whatTheBatchDeletedInsideAnElementIsGoneForLaterSteps() throws Exception {
        List<Step> steps = List.of(
                step("delete", "//step[@platform='macos']/cmd/filepath"),
                op("set-text", "//step[@platform='macos']/cmd", null, null, "Drag it to Applications."),
                new Step("test", "//step[@platform='macos']/cmd", null, null, null, null, "Drag it to Applications.", null));
        assertThat(plain(steps)).contains("<cmd>Drag it to Applications.</cmd>");
        reviewed(steps);

        List<Step> move = List.of(step("delete", "//step[@platform='macos']/cmd/filepath"),
                new Step("move", "//step[@platform='macos']", "after", null, null, null, null, "//step[@platform='linux']"));
        String marked = reviewed(move);
        assertThat(ProposalOps.acceptAll(marked, AUTHOR)).doesNotContain("filepath");
    }

    @Test
    void anAttributeNamedLikeTheIdentityDoesNotConfuseSelection() throws Exception {
        String source = TOPIC.replace("<step platform=\"windows\">", "<step platform=\"windows\" dogsbay-sx=\"3\">");
        String result = StructuralEdit.apply(source, List.of(step("delete", "//step[@platform='linux']")), null).text();
        assertThat(result).contains("dogsbay-sx=\"3\"").contains("windows").contains("macos").doesNotContain("linux");
        reviewed(source, List.of(step("delete", "//step[@platform='linux']")));
    }

    @Test
    void anXPathThatIsAValueIsAStepFailureNotACrash() {
        assertThatThrownBy(() -> plain(List.of(step("delete", "count(//step)"))))
                .isInstanceOf(StructuralEdit.StepException.class).hasMessageContaining("must select an element");
        assertThatThrownBy(() -> plain(List.of(step("delete", "//step/cmd/text()"))))
                .isInstanceOf(StructuralEdit.StepException.class).hasMessageContaining("must select an element");
    }

    @Test
    void refusesToChangeTheReviewMarkOfAnElementItInserted() {
        assertThatThrownBy(() -> StructuralEdit.apply(TOPIC, List.of(
                insert("//steps", "last-child", "<step id=\"s4\"><cmd>Start it.</cmd></step>"),
                op("remove-attribute", "#s4", "status", null, null)), AUTHOR))
                .hasMessageContaining("holds its review mark");
    }

    @Test
    void unwrapsInlineAsAProposalExactly() throws Exception {
        reviewed(List.of(step("unwrap", "//step[@platform='macos']/cmd/filepath")));
    }
}
