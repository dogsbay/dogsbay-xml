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

package com.dogsbay.xml;

import com.dogsbay.xml.format.FormatStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DOM-aware sentence reflow: prose is reflowed; verbatim blocks are not. */
class ReflowSentencesTest {

    private static String reflow(String xml) throws Exception {
        return XMLUtilities.reflowSentences(xml, null, "UTF-8", FormatStyle.defaults());
    }

    /** Strip the per-line continuation indent so assertions focus on the breaks. */
    private static String dedent(String s) {
        return s.replaceAll("\n\\s*", "\n");
    }

    @Test
    @DisplayName("prose is reflowed to one sentence per line (continuation lines indented)")
    void reflowsProse() throws Exception {
        String out = reflow("<concept><conbody><p>First sentence. Second sentence. Third.</p></conbody></concept>");
        assertThat(dedent(out)).contains("<p>First sentence.\nSecond sentence.\nThird.</p>");
        // continuation lines align with the block element's own indent (the <p> at 4 spaces)
        assertThat(out).contains("\n    <p>First sentence.\n    Second sentence.\n    Third.</p>");
    }

    @Test
    @DisplayName("FLUSH continuation puts later sentences at column 0")
    void flushContinuation() throws Exception {
        FormatStyle flush = FormatStyle.defaults()
                .withTextContinuation(FormatStyle.TextContinuation.FLUSH);
        String out = XMLUtilities.reflowSentences(
                "<concept><conbody><p>First sentence. Second sentence.</p></conbody></concept>",
                null, "UTF-8", flush);
        assertThat(out).contains("First sentence.\nSecond sentence.");   // no leading indent
    }

    @Test
    @DisplayName("an inline element mid-sentence is NOT broken onto its own line")
    void inlineElementMidSentenceStaysInline() throws Exception {
        // the source had a line break before the inline element; reflow must collapse it
        String out = reflow("<concept><conbody><p>Sound is a continuous\n"
                + "    <ph>waveform</ph> of pressure. A microphone converts it.</p></conbody></concept>");
        assertThat(out).contains("continuous <ph>waveform</ph> of pressure.");
        assertThat(dedent(out)).contains("of pressure.\nA microphone converts it.");
    }

    @Test
    @DisplayName("codeblock content is NOT reflowed (verbatim)")
    void leavesCodeblockAlone() throws Exception {
        String code = "echo one. echo two. echo three.";
        String out = reflow("<concept><conbody><codeblock>" + code + "</codeblock></conbody></concept>");
        assertThat(out).contains("<codeblock>" + code + "</codeblock>");   // single line, untouched
    }

    @Test
    @DisplayName("inline tags stay on their sentence")
    void inlineTagsStayInline() throws Exception {
        String out = reflow("<concept><conbody><p>Open it. Click <uicontrol>Save</uicontrol> now. Done.</p></conbody></concept>");
        assertThat(out).contains("Click <uicontrol>Save</uicontrol> now.");
        assertThat(dedent(out)).contains("Open it.\nClick");
    }

    @Test
    @DisplayName("reflow keeps its breaks even when the style disables preserve-text-breaks")
    void reflowSurvivesPreserveOff() throws Exception {
        FormatStyle noPreserve = FormatStyle.defaults().withPreserveTextLineBreaks(false);
        String out = XMLUtilities.reflowSentences(
                "<concept><conbody><p>First sentence. Second sentence.</p></conbody></concept>",
                null, "UTF-8", noPreserve);
        // text-only <p> must NOT be re-collapsed back to one line
        assertThat(dedent(out)).contains("<p>First sentence.\nSecond sentence.</p>");
    }

    @Test
    @DisplayName("a sentence ending in a common word (not a real abbreviation) still breaks")
    void breaksAfterCommonWords() throws Exception {
        String out = reflow("<concept><conbody><p>The signal reached its max. Then it dropped.</p></conbody></concept>");
        assertThat(dedent(out)).contains("its max.\nThen it dropped.");
    }

    @Test
    @DisplayName("reflowing an already-reflowed document is stable")
    void idempotent() throws Exception {
        String once = reflow("<concept><conbody><p>One. Two. Three.</p></conbody></concept>");
        assertThat(reflow(once)).isEqualTo(once);
    }
}
