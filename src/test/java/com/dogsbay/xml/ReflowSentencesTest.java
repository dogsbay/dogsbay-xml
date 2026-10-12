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

    @Test
    @DisplayName("reflow keeps a character the document's encoding cannot represent")
    void reflowKeepsUnrepresentableCharacters() throws Exception {
        // Reflow used to destroy what Format preserved — the same document, the same encoding, two
        // different answers — because it kept the lossy String -> bytes -> String round-trip.
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p>Caf\u00e9 a \u2192 b. Second sentence.</p></root>\n";

        String out = XMLUtilities.reflowSentences(source, null, "ISO-8859-1", FormatStyle.defaults());

        assertThat(out).contains("Caf\u00e9");
        assertThat(out).containsPattern("a &#(8594|x2192); b");
        assertThat(java.nio.charset.Charset.forName("ISO-8859-1").newEncoder().canEncode(out))
                .as("reflowed text must be writable in the file's encoding: " + out)
                .isTrue();
    }

    @Test
    @DisplayName("reflow accepts a byte-order mark, and keeps it")
    void reflowKeepsAByteOrderMark() throws Exception {
        // Format handled a BOM while Reflow refused the same file outright.
        String source = "\ufeff<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root><p>One sentence. Two sentences.</p></root>\n";

        String out = XMLUtilities.reflowSentences(source, null, "UTF-8", FormatStyle.defaults());

        assertThat(out).startsWith("\ufeff");
        assertThat(out.substring(1)).doesNotContain("\ufeff");
        assertThat(out).contains("One sentence.");
    }

    @Test
    @DisplayName("reflow leaves escaped characters exactly as they were")
    void reflowDoesNotReEscape() throws Exception {
        // This reader does not substitute entities, so `&amp;` is five characters of text in the
        // tree. Reflow re-serialised with doc.asXML(), which escapes, so every run escaped what the
        // last run had escaped: &amp;amp; -> &amp;amp;amp; -> ... without bound.
        String source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n  <p>A &amp;amp; B &lt; C. Second one here.</p>\n</root>\n";

        String once = XMLUtilities.reflowSentences(source, null, "UTF-8", FormatStyle.defaults());
        String twice = XMLUtilities.reflowSentences(once, null, "UTF-8", FormatStyle.defaults());

        assertThat(once).contains("A &amp;amp; B &lt; C");
        assertThat(once).doesNotContain("&amp;amp;amp;").doesNotContain("&amp;lt;");
        assertThat(twice).as("and it stays put on a second run").isEqualTo(once);
    }

    @Test
    @DisplayName("reflow keeps every escaped character it was given")
    void reflowKeepsEveryEscape() throws Exception {
        String source = "<root><p>&lt;tag&gt; &amp; &quot;q&quot; &apos;a&apos; done. Next.</p></root>";

        String out = XMLUtilities.reflowSentences(source, null, "UTF-8", FormatStyle.defaults());

        assertThat(out).contains("&lt;tag&gt;").contains("&quot;q&quot;").contains("&apos;a&apos;");
        assertThat(out).doesNotContain("&amp;lt;").doesNotContain("&amp;quot;");
    }
}
