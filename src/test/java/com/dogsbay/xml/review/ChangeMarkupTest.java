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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ValidationResult;
import com.dogsbay.dogsbayaieditor.validate.DocumentValidator;

class ChangeMarkupTest {

    @TempDir
    Path dir;

    private static final String HEAD = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
        """;
    private static final String BASE = HEAD + """
        <task id="rec">
          <title>Record a track</title>
          <shortdesc>Press the red button to start recording.</shortdesc>
          <prolog><metadata><keywords><keyword>record</keyword></keywords></metadata></prolog>
          <taskbody>
            <steps>
              <step><cmd>Click <uicontrol>Record</uicontrol>.</cmd></step>
              <step><cmd>Speak into the microphone.</cmd></step>
            </steps>
          </taskbody>
        </task>
        """;

    private final ChangeMarkup engine = new ChangeMarkup("ai:claude-acp", "2026-09-04T10:00:00Z");

    /** The two invariants every case must hold. */
    private String markAndCheck(String base, String modified) throws Exception {
        String marked = engine.mark(base, modified);
        assertThat(ProposalOps.acceptedView(marked)).as("accept all == modified").isEqualTo(modified);
        assertThat(normalise(ProposalOps.rejectAll(marked, null))).as("reject all == baseline, modulo whitespace")
                .isEqualTo(normalise(base));
        assertValid(marked);
        return marked;
    }

    private void assertValid(String xml) throws Exception {
        Path f = dir.resolve("t" + System.nanoTime() + ".dita");
        Files.writeString(f, xml);
        ValidationResult r = DocumentValidator.validate(f, null, List.of());
        assertThat(r.errors()).as("marked document validates against the bundled DTD:\n" + xml).isEmpty();
    }

    @Test
    void wrappingExistingContentIsMarkedAsAWrapperSoRejectUnwraps() throws Exception {
        String modified = BASE.replace("<steps>", "<steps>\n      <stepsection>Before you start</stepsection>")
                .replace("<taskbody>\n    <steps>", "<taskbody>\n    <context><p>Wrapped.</p></context>\n    <steps>");
        // simpler, unambiguous wrap: put the two steps' section into a new <context><p> is not valid; use a note wrapper in shortdesc
        String base = BASE;
        String wrapped = base.replace("<shortdesc>Press the red button to start recording.</shortdesc>",
                "<shortdesc><ph>Press the red button to start recording.</ph></shortdesc>");
        String marked = markAndCheck(base, wrapped);
        assertThat(marked).contains("<ph status=\"new\" rev=\"ai:claude-acp review-wrapper\">Press the red button");
        Proposal p = ProposalIndex.scan(marked).get(0);
        assertThat(p.wrapper()).isTrue();
        assertThat(p.synthetic()).as("a real element the agent added, not our own mark").isFalse();
        assertThat(ProposalOps.reject(marked, p)).isEqualTo(base);
        assertThat(ProposalOps.accept(marked, p)).isEqualTo(wrapped);
    }

    @Test
    void unwrappingContentIsMarkedAsADeletedWrapperSoRejectRewraps() throws Exception {
        String base = BASE.replace("<shortdesc>Press the red button to start recording.</shortdesc>",
                "<shortdesc><ph id=\"k\">Press the red button to start recording.</ph></shortdesc>");
        String unwrapped = BASE;
        String marked = markAndCheck(base, unwrapped);
        assertThat(marked).contains("<ph id=\"k\" status=\"deleted\" rev=\"ai:claude-acp review-wrapper\">Press the red button to start recording.</ph>");
        assertThat(ProposalOps.rejectAll(marked, null)).isEqualTo(base);
    }

    @Test
    void aRenamedElementUndoesExactly() throws Exception {
        String modified = BASE.replace("<shortdesc>Press the red button to start recording.</shortdesc>",
                "<shortdesc>Press the red button to start recording.</shortdesc>")
                .replace("<step><cmd>Speak into the microphone.</cmd></step>",
                         "<step><cmd>Speak into the microphone.</cmd><info>Quietly.</info></step>");
        markAndCheck(BASE, modified);   // an inserted child element inside an existing step
        // A rename around unchanged text reads as one wrapper removed and one added:
        // undoing unwraps the new element and re-wraps the old, which is exact.
        String renamed = BASE.replace("<uicontrol>Record</uicontrol>", "<b>Record</b>");
        String marked = markAndCheck(BASE, renamed);
        assertThat(marked).contains("review-wrapper");
        assertThat(ProposalOps.rejectAll(marked, null)).isEqualTo(BASE);
        assertThat(ProposalOps.acceptedView(marked)).isEqualTo(renamed);
    }

    @Test
    void insertingAMiddleParagraphIsOneBlockProposalNotAShiftedTextDiff() throws Exception {
        String base = HEAD + "<task id=\"t\"><title>T</title><taskbody><context>"
                + "<p>alpha</p><p>beta</p><p>gamma</p></context></taskbody></task>\n";
        String modified = base.replace("<p>beta</p>", "<p>beta</p><p>inserted</p>");
        String marked = markAndCheck(base, modified);
        assertThat(ProposalIndex.scan(marked)).singleElement().satisfies(p -> {
            assertThat(p.kind()).isEqualTo(Proposal.Kind.INSERT);
            assertThat(p.element()).isEqualTo("p");
            assertThat(p.text()).isEqualTo("inserted");
        });
        String replaced = base.replace("<p>beta</p>", "<note>gamma</note>");
        String marked2 = markAndCheck(base, replaced);
        assertThat(marked2).doesNotContain("<note status=\"new\" rev=\"ai:claude-acp\"><p");
    }

    @Test
    void aOneWordChangeInALongTopicIsCheapAndMarkedAlone() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 1300; i++) {
            body.append("<p>para ").append(i).append(" has some words in it</p>\n");
        }
        String base = HEAD + "<task id=\"t\"><title>T</title><taskbody><context>" + body + "</context></taskbody></task>\n";
        String modified = base.replace("<p>para 700 has some words in it</p>", "<p>para 700 has several words in it</p>");
        long start = System.nanoTime();
        String marked = markAndCheck(base, modified);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(5));
        assertThat(ProposalIndex.scan(marked)).hasSize(2);
        assertThat(marked.indexOf("</task>")).isEqualTo(marked.lastIndexOf("</task>"));
    }

    @Test
    void aDeltaTooLargeToReviewIsRefusedNotMangled() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            a.append("<p>a").append(i).append("</p>");
            b.append("<p>b").append(i).append("</p>");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.mark("<body>" + a + "</body>", "<body>" + b + "</body>"))
                .isInstanceOf(ChangeMarkup.TooDifferent.class);
    }

    @Test
    void illFormedInputIsRefusedUpFront() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.mark(BASE, BASE + "<p>cut off"))
                .hasMessageContaining("not well-formed");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ChangeMarkup("claude", "t"))
                .hasMessageContaining("author must be an id");
    }

    @Test
    void textChangeInsideAPhFreeInlineElementBecomesAPair() throws Exception {
        String modified = BASE.replace("<uicontrol>Record</uicontrol>", "<uicontrol>Rec</uicontrol>");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).doesNotContain("<uicontrol><ph").doesNotContain("<uicontrol status=\"new\" rev=\"ai:claude-acp\"><ph");
        assertThat(marked).contains("<uicontrol status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">Record</uicontrol>"
                + "<uicontrol status=\"new\" rev=\"ai:claude-acp\">Rec</uicontrol>");
    }

    @Test
    void deletedTextInsideAPhFreeElementIsAPairToo() throws Exception {
        String base = BASE.replace("<keyword>record</keyword>", "<keyword>record</keyword>");
        String modified = base.replace("<keyword>record</keyword>", "<keyword></keyword>");
        String marked = markAndCheck(base, modified);
        assertThat(marked).doesNotContain("<keyword><ph");
    }

    @Test
    void humanRevisionMarksOnAnElementAreKeptThroughAMarkAndBack() throws Exception {
        String base = BASE.replace("<step><cmd>Speak into the microphone.</cmd></step>",
                "<step rev=\"2.1\" status=\"changed\"><cmd>Speak into the microphone.</cmd></step>");
        String modified = base.replace("<step rev=\"2.1\" status=\"changed\"><cmd>Speak", "<step rev=\"2.1\" status=\"changed\" importance=\"required\"><cmd>Speak");
        String marked = markAndCheck(base, modified);
        assertThat(marked).contains("rev=\"2.1 ai:claude-acp review-prev-status-changed\"");
    }

    private static String normalise(String s) {
        return s.replaceAll("\\s+", " ").replace("> <", "><").strip();
    }

    @Test
    void aWordChangeBecomesOneDeleteInsertPair() throws Exception {
        String modified = BASE.replace("Speak into the microphone.", "Speak clearly into the microphone.");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("Speak <ph status=\"new\" rev=\"ai:claude-acp review-mark\">clearly </ph>into the microphone.");
        List<Proposal> ps = ProposalIndex.scan(marked);
        assertThat(ps).hasSize(1);
        assertThat(ps.get(0).kind()).isEqualTo(Proposal.Kind.INSERT);
    }

    @Test
    void aReplacementCoalescesIntoAdjacentMarks() throws Exception {
        String modified = BASE.replace("Press the red button to start recording.", "Press the red button to begin.");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<ph status=\"deleted\" rev=\"ai:claude-acp review-mark\" otherprops=\"review-deleted\">start recording.</ph>"
                + "<ph status=\"new\" rev=\"ai:claude-acp review-mark\">begin.</ph>");
        assertThat(ProposalIndex.scan(marked)).hasSize(2);
    }

    @Test
    void anInsertedBlockIsMarkedNewAsAWhole() throws Exception {
        String modified = BASE.replace("</steps>", "  <step><cmd>Press stop.</cmd></step>\n    </steps>");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<step status=\"new\" rev=\"ai:claude-acp\"><cmd>Press stop.</cmd></step>");
        assertThat(ProposalIndex.scan(marked)).hasSize(1);
        assertThat(marked).as("nothing inside the new block is marked again").doesNotContain("<cmd status");
    }

    @Test
    void aRemovedBlockIsPutBackMarkedDeleted() throws Exception {
        String modified = BASE.replace("      <step><cmd>Speak into the microphone.</cmd></step>\n", "");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<step status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">"
                + "<cmd>Speak into the microphone.</cmd></step>");
        assertThat(ProposalIndex.scan(marked)).singleElement().extracting(Proposal::kind).isEqualTo(Proposal.Kind.DELETE);
    }

    @Test
    void changesSeparatedOnlyByASpaceAreOneProposal() throws Exception {
        String base = BASE.replace("Press the red button to start recording.",
                "Position the microphone 6-12 inches from your mouth.");
        String modified = base.replace("6-12 inches", "15-30 cm");
        String marked = markAndCheck(base, modified);
        assertThat(marked).contains("otherprops=\"review-deleted\">6-12 inches</ph>")
                .contains("review-mark\">15-30 cm</ph>");
        assertThat(ProposalIndex.scan(marked)).extracting(Proposal::kind)
                .containsExactly(Proposal.Kind.DELETE, Proposal.Kind.INSERT);
    }

    @Test
    void aSoftWrapInProseDoesNotSplitTheProposal() throws Exception {
        String base = BASE.replace("Press the red button to start recording.",
                "Position the microphone 6-12\n      inches from your mouth.");
        String modified = base.replace("6-12\n      inches", "15-30\n      cm");
        String marked = markAndCheck(base, modified);
        assertThat(ProposalIndex.scan(marked)).extracting(Proposal::kind)
                .containsExactly(Proposal.Kind.DELETE, Proposal.Kind.INSERT);
    }

    @Test
    void changesSeparatedByAnUnchangedWordStayApart() throws Exception {
        String modified = BASE.replace("Press the red button to start", "Press the green button to begin");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("review-deleted\">red</ph>").contains("review-mark\">green</ph>")
                .contains("review-deleted\">start</ph>").contains("review-mark\">begin</ph>")
                .contains("</ph> button to <ph");
        assertThat(ProposalIndex.scan(marked)).hasSize(4);
    }

    @Test
    void linesOfACodeblockStayApart() throws Exception {
        String base = BASE.replace("<step><cmd>Speak into the microphone.</cmd></step>",
                "<step><cmd>Run:</cmd><info><codeblock>make build\nmake test</codeblock></info></step>");
        String modified = base.replace("make build\nmake test", "gradle assemble\nninja check");
        String marked = markAndCheck(base, modified);
        assertThat(ProposalIndex.scan(marked)).hasSize(4);
        assertThat(marked).contains("review-mark\">gradle assemble</ph>\n<ph")
                .contains("review-mark\">ninja check</ph>");
    }

    @Test
    void aOneSidedRunNextToASpaceNeverProducesABlankMark() throws Exception {
        String modified = BASE.replace("Click <uicontrol>Record</uicontrol>.", "Click <uicontrol>Record</uicontrol>, then speak.");
        String marked = markAndCheck(BASE, modified);
        assertThat(ProposalIndex.scan(marked)).allSatisfy(p -> assertThat(p.text()).isNotBlank());
        assertThat(marked).doesNotContain("review-deleted\"> </ph>").doesNotContain("review-mark\"> </ph>");

        String trimmed = BASE.replace("Press the red button", "red button");
        String marked2 = markAndCheck(BASE, trimmed);
        assertThat(ProposalIndex.scan(marked2)).allSatisfy(p -> assertThat(p.text()).isNotBlank());
    }

    @Test
    void joinRuleIsPure() {
        assertThat(ChangeMarkup.Plan.joinsChanges(" ", true, true, false)).isTrue();
        assertThat(ChangeMarkup.Plan.joinsChanges("\n   ", true, true, false)).isTrue();
        assertThat(ChangeMarkup.Plan.joinsChanges("\n", true, true, true)).isFalse();
        assertThat(ChangeMarkup.Plan.joinsChanges("\u2028", true, true, true)).isFalse();
        assertThat(ChangeMarkup.Plan.joinsChanges(" ", false, true, false)).isFalse();
        assertThat(ChangeMarkup.Plan.joinsChanges(" ", true, false, false)).isFalse();
        assertThat(ChangeMarkup.Plan.joinsChanges("word", true, true, false)).isFalse();
    }

    @Test
    void aDeletionSpanningAnInlineElementRestoresTheElement() throws Exception {
        String modified = BASE.replace("Click <uicontrol>Record</uicontrol>.", "Click.");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<uicontrol status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">Record</uicontrol>");
        assertThat(marked).as("the deleted period travels as text").contains("review-mark\" otherprops=\"review-deleted\">.</ph>");
    }

    @Test
    void anAttributeChangeIsRecordedAsARestorablePair() throws Exception {
        String modified = BASE.replace("<step><cmd>Speak", "<step importance=\"required\"><cmd>Speak");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<step status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">"
                + "<cmd>Speak into the microphone.</cmd></step>");
        assertThat(marked).contains("<step importance=\"required\" status=\"new\" rev=\"ai:claude-acp\"><cmd>Speak");
        assertThat(ProposalOps.rejectAll(marked, null)).as("reject brings the old attributes back exactly")
                .isEqualTo(BASE);
    }

    @Test
    void textOnlyElementsBecomeAPairWhereTheyMayRepeat() throws Exception {
        String modified = BASE.replace("<keyword>record</keyword>", "<keyword>recording</keyword>");
        String marked = markAndCheck(BASE, modified);
        assertThat(marked).contains("<keyword status=\"deleted\" rev=\"ai:claude-acp\" otherprops=\"review-deleted\">record</keyword>"
                + "<keyword status=\"new\" rev=\"ai:claude-acp\">recording</keyword>");
        assertThat(marked).as("no ph inside a keyword").doesNotContain("<keyword status=\"new\" rev=\"ai:claude-acp\"><ph");
        assertThat(ProposalOps.rejectAll(marked, null)).isEqualTo(BASE);
    }

    @Test
    void whenAPairIsInvalidTheChangedMarkKeepsThePreviousVersionAndRejectRestoresIt() throws Exception {
        String base = BASE.replace("<step><cmd>Speak into the microphone.</cmd></step>",
                "<step><cmd>Speak into the microphone.</cmd><info><p>Use <keyword>mono</keyword>.</p></info></step>");
        String modified = base.replace("<keyword>mono</keyword>", "<keyword>stereo</keyword>");
        // A validator that treats the keyword as a singleton stands in for a strict grammar.
        ChangeMarkup strict = new ChangeMarkup("ai:claude-acp", "2026-09-04T10:00:00Z",
                xml -> !xml.contains("</keyword><keyword"));
        String marked = strict.mark(base, modified);

        assertThat(marked).contains("<keyword status=\"changed\" rev=\"ai:claude-acp\">stereo</keyword>"
                + "<draft-comment author=\"ai:claude-acp\"");
        assertThat(marked).contains("<data name=\"" + ChangeMarkup.PREVIOUS_XML + "\" value=\"mono\"/>");
        assertValid(marked);
        assertThat(ProposalOps.acceptedView(marked)).isEqualTo(modified);
        List<Proposal> ps = ProposalIndex.scan(marked);
        assertThat(ps).as("the note belongs to the change; it is not a comment of its own")
                .extracting(Proposal::kind).containsExactly(Proposal.Kind.CHANGED);
        assertThat(ProposalOps.reject(marked, ps.get(0))).as("reject restores the previous text and drops the note")
                .isEqualTo(base);
        assertThat(ProposalOps.accept(marked, ps.get(0))).as("accept strips the mark and the note").isEqualTo(modified);
    }

    @Test
    void aChangeThatKeptNoPreviousVersionRefusesToPretendOnReject() {
        String xml = "<topic id=\"t\"><title>T</title><body><p><keyword status=\"changed\" rev=\"ai:x\">k</keyword></p></body></topic>";
        Proposal p = ProposalIndex.scan(xml).get(0);
        assertThat(ProposalOps.accept(xml, p)).contains("<keyword>k</keyword>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ProposalOps.reject(xml, p))
                .hasMessageContaining("kept no previous version");
    }

    @Test
    void whitespaceOnlyDifferencesAreNotMarked() throws Exception {
        String modified = BASE.replace("Press the red button", "Press  the red button");
        String marked = engine.mark(BASE, modified);
        assertThat(ProposalIndex.scan(marked)).isEmpty();
        assertThat(marked).isEqualTo(modified);
    }

    @Test
    void editingInsideAnExistingProposalTreatsItAsOrdinaryMarkup() throws Exception {
        String base = BASE.replace("Speak into the microphone.",
                "Speak <ph status=\"new\" rev=\"ai:codex-acp\">clearly </ph>into the microphone.");
        String modified = base.replace("into the microphone.", "into the mic.");
        String marked = engine.mark(base, modified);
        assertThat(marked).contains("rev=\"ai:codex-acp\">clearly </ph>");
        assertThat(marked).contains("<ph status=\"deleted\" rev=\"ai:claude-acp review-mark\" otherprops=\"review-deleted\">microphone.</ph>"
                + "<ph status=\"new\" rev=\"ai:claude-acp review-mark\">mic.</ph>");
        assertThat(ProposalIndex.scan(marked)).extracting(Proposal::author)
                .containsExactly("ai:codex-acp", "ai:claude-acp", "ai:claude-acp");
        assertValid(marked);
    }

    @Test
    void aWholeFileRewriteStillYieldsAcceptAllEqualsModified() throws Exception {
        String modified = HEAD + """
            <task id="rec">
              <title>Record your first track</title>
              <shortdesc>Arm a track and record from the microphone.</shortdesc>
              <prolog><metadata><keywords><keyword>record</keyword><keyword>microphone</keyword></keywords></metadata></prolog>
              <taskbody>
                <context><p>You need a microphone.</p></context>
                <steps>
                  <step><cmd>Click <uicontrol>Record</uicontrol>.</cmd></step>
                  <step><cmd>Speak into the microphone.</cmd></step>
                  <step><cmd>Click <uicontrol>Stop</uicontrol>.</cmd></step>
                </steps>
              </taskbody>
            </task>
            """;
        String marked = markAndCheck(BASE, modified);
        assertThat(ProposalIndex.scan(marked)).isNotEmpty();
    }

    @Test
    void aTextNodeRewrittenWholesaleIsOneDeleteAndOneInsertNotAnError() throws Exception {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            a.append("alpha").append(i).append(' ');
            b.append("beta").append(i).append(' ');
        }
        String base = HEAD + "<task id=\"t\"><title>T</title><taskbody><context><p>" + a.toString().strip()
                + "</p></context></taskbody></task>\n";
        String modified = base.replace(a.toString().strip(), b.toString().strip());
        String marked = markAndCheck(base, modified);
        assertThat(ProposalIndex.scan(marked)).hasSize(2);
    }

    @Test
    void identicalInputProducesNoMarks() {
        assertThat(engine.mark(BASE, BASE)).isEqualTo(BASE);
    }

    @Test
    void wordsKeepWhitespaceAsTokensSoJoinsAreExact() {
        assertThat(String.join("", ChangeMarkup.words(" a  b\nc "))).isEqualTo(" a  b\nc ");
        assertThat(ChangeMarkup.words("a b")).containsExactly("a", " ", "b");
    }
}
