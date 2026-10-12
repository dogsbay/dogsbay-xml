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

package com.dogsbay.xml.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A glossary key in the preview, against what the same source publishes.
 *
 * <p>The shapes here are the tutorial's own: a keydef per entry in {@code keydefs-glossary.ditamap}, an entry
 * holding a {@code <glossterm>} and a {@code <glossdef>}, and {@code <abbreviated-form>} in the prose. Before
 * this, an {@code <abbreviated-form>} drew nothing at all — the word was simply missing from the sentence —
 * and a {@code <term>} or an {@code <xref>} drew the key's own name, {@code gl-clipping}.
 */
class GlossaryPreviewTest {

	@TempDir
	File dir;

	private File write(String name, String xml) throws Exception {
		File f = new File(dir, name);
		Files.writeString(f.toPath(), xml);
		return f;
	}

	/** The tutorial's glossary map: one keydef per entry, both entries beside it. */
	private File glossaryMap() throws Exception {
		write("g-clipping.dita", """
				<glossentry id="gl-clipping"><glossterm>Clipping</glossterm>
				<glossdef>Distortion when a signal exceeds the maximum level.</glossdef></glossentry>
				""");
		write("g-hz.dita", """
				<glossentry id="gl-hz"><glossterm>Hertz</glossterm>
				<glossBody><glossSurfaceForm>Hertz (Hz)</glossSurfaceForm>
				<glossAlt><glossAcronym>Hz</glossAcronym></glossAlt></glossBody></glossentry>
				""");
		return write("root.ditamap", """
				<map><keydef keys="gl-clipping" href="g-clipping.dita"/>
				<keydef keys="gl-hz" href="g-hz.dita"/></map>
				""");
	}

	private String preview(File map, String body) throws Exception {
		return DitaConverter.convert(
				"<concept id=\"c\"><title>T</title><conbody>" + body + "</conbody></concept>",
				null, new PreviewOptions(map, null));
	}

	@Test
	void anAbbreviatedFormSaysTheTerm() throws Exception {
		String html = preview(glossaryMap(), "<p>A <abbreviated-form keyref=\"gl-clipping\"/> is bad.</p>");

		assertThat(html).contains(">Clipping<");
		assertThat(html).as("resolved, not shown in the unresolved style").contains("keyref-resolved");
		// The sentence used to read "A  is bad." — the gap is the whole of what was reported.
		assertThat(html).doesNotContain("A  is");
		assertThat(html).doesNotContain(">gl-clipping<");
	}

	@Test
	void theDefinitionIsTheHoverText() throws Exception {
		String html = preview(glossaryMap(), "<p><abbreviated-form keyref=\"gl-clipping\"/></p>");

		assertThat(html).contains("title=\"Distortion when a signal exceeds the maximum level.\"");
	}

	@Test
	void theFirstMentionIsSpeltOutAndTheNextIsNot() throws Exception {
		// What a build does, and the reason an entry carries three forms at all.
		String html = preview(glossaryMap(),
				"<p>Measured in <abbreviated-form keyref=\"gl-hz\"/>.</p>"
				+ "<p>44100 <abbreviated-form keyref=\"gl-hz\"/>.</p>");

		assertThat(html).contains(">Hertz (Hz)<").contains(">Hz<");
		assertThat(html.indexOf(">Hertz (Hz)<")).isLessThan(html.indexOf(">Hz<"));
		assertThat(html.split(">Hertz \\(Hz\\)<", -1)).as("spelt out once").hasSize(2);
	}

	@Test
	void aTermAndAnAbbreviatedFormOnOneKeyAreOneSeries() throws Exception {
		// abbreviated-form specialises term, so a build counts them together: whichever comes first
		// is the mention that gets spelt out.
		String html = preview(glossaryMap(),
				"<p>Measured in <term keyref=\"gl-hz\"/>.</p>"
				+ "<p>44100 <abbreviated-form keyref=\"gl-hz\"/>.</p>");

		assertThat(html).contains(">Hertz (Hz)<");
		assertThat(html.split(">Hertz \\(Hz\\)<", -1)).as("spelt out once, by the term").hasSize(2);
		assertThat(html).contains(">Hz<");
	}

	@Test
	void aTermSaysTheTermAndNotTheKey() throws Exception {
		String html = preview(glossaryMap(), "<p>A <term keyref=\"gl-clipping\"/> is bad.</p>");

		assertThat(html).contains(">Clipping<");
		assertThat(html).doesNotContain(">gl-clipping<");
	}

	@Test
	void whatTheWriterWroteWinsOverTheEntry() throws Exception {
		// A build keeps the element's own content. Someone who wrote the words meant them.
		String html = preview(glossaryMap(),
				"<p><abbreviated-form keyref=\"gl-clipping\">clipped audio</abbreviated-form></p>");

		assertThat(html).contains("clipped audio");
		assertThat(html).doesNotContain(">Clipping<");
	}

	@Test
	void aLinkToAnEntryIsLabelledWithTheTerm() throws Exception {
		// An entry has no <title>, so the title the key space reads is empty and the link read
		// "gl-clipping". A build's link says "Clipping".
		String html = preview(glossaryMap(), "<p>See <xref keyref=\"gl-clipping\"/>.</p>");

		assertThat(html).contains(">Clipping<");
		assertThat(html).doesNotContain(">gl-clipping<");
	}

	@Test
	void aKeywordOnAnEntryStillNamesIt() throws Exception {
		String html = preview(glossaryMap(), "<p><keyword keyref=\"gl-clipping\"/></p>");

		assertThat(html).contains(">Clipping<");
	}

	@Test
	void aKeydefsOwnWordsStillLabelALink() throws Exception {
		// <linktext> in the map is what the writer of the map said, and it beats the entry in a link.
		String html = preview(linktextMap(), "<p>See <xref keyref=\"gl-clipping\"/>.</p>");

		assertThat(html).contains("Clipping (distortion)");
	}

	@Test
	void aLinkLabelDoesNotSpeakForATerm() throws Exception {
		// A build does not put a keydef's <linktext> inside a term: DITA-OT's KeyrefParser pushes
		// linktext only into link types, so the entry's own name is what a term says.
		String html = preview(linktextMap(), "<p>A <term keyref=\"gl-clipping\"/> is bad.</p>");

		assertThat(html).contains(">Clipping<");
		assertThat(html).doesNotContain("Clipping (distortion)");
	}

	@Test
	void aKeywordInTheKeydefIsObeyedEvenByATerm() throws Exception {
		// The other half of the same rule, and the reason the two are not one branch: KeyrefParser
		// pushes a keydef's <keyword> into an empty term as if the author had written it, so a build
		// shows the map's wording and not the entry's.
		write("g-clipping.dita", """
				<glossentry id="gl-clipping"><glossterm>Clipping</glossterm></glossentry>
				""");
		File map = write("root.ditamap", """
				<map><keydef keys="gl-clipping" href="g-clipping.dita"><topicmeta><keywords>
				<keyword>clipped signal</keyword></keywords></topicmeta></keydef></map>
				""");

		String html = preview(map, "<p>A <term keyref=\"gl-clipping\"/> is bad.</p>");

		assertThat(html).contains("clipped signal");
		assertThat(html).doesNotContain(">Clipping<");
	}

	/** A glossary entry whose keydef also carries a link label. */
	private File linktextMap() throws Exception {
		write("g-clipping.dita", """
				<glossentry id="gl-clipping"><glossterm>Clipping</glossterm></glossentry>
				""");
		return write("root.ditamap", """
				<map><keydef keys="gl-clipping" href="g-clipping.dita">
				<topicmeta><linktext>Clipping (distortion)</linktext></topicmeta></keydef></map>
				""");
	}

	@Test
	void anAbbreviatedFormOnSomethingThatIsNotAnEntrySaysSoPlainly() throws Exception {
		// A build logs DOTX060W and draws nothing. Drawing nothing is what was reported as a bug, so
		// the preview says what it could not resolve instead.
		write("installing.dita", """
				<task id="t"><title>Installing</title><taskbody/></task>
				""");
		File map = write("root.ditamap",
				"<map><keydef keys=\"install\" href=\"installing.dita\"/></map>");

		String html = preview(map, "<p><abbreviated-form keyref=\"install\"/></p>");

		assertThat(html).contains("install");
		assertThat(html).doesNotContain("<p></p>");
	}

	@Test
	void anAbbreviatedFormOnANonExistentKeyIsNotSilent() throws Exception {
		File map = write("root.ditamap", "<map><keydef keys=\"other\" href=\"x.dita\"/></map>");

		String html = preview(map, "<p><abbreviated-form keyref=\"no-such-key\"/></p>");

		assertThat(html).contains("class=\"keyref\"").contains("no-such-key");
	}
}
