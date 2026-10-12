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

package com.dogsbay.dogsbayaieditor.links;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a build would show for a glossary key, decided without a transform.
 *
 * <p>Every rule here was read out of DITA-OT's {@code org.dita.html5/xsl/topic.xsl} — {@code
 * getMatchingSurfaceForm}, {@code getMatchingAcronym} and {@code getMatchingGlossdef} — rather than out of
 * the spec's prose, because the published pages the preview is being matched against come from that file.
 */
class GlossEntryTest {

	@TempDir
	File dir;

	private File write(String xml) throws Exception {
		File f = new File(dir, "g.dita");
		Files.writeString(f.toPath(), xml);
		return f;
	}

	@Test
	void theTermIsTheWholeOfASimpleEntry() throws Exception {
		// The tutorial's own shape, from stage 12 onwards.
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="gl-clipping"><glossterm>Clipping</glossterm>
				<glossdef>Distortion when a signal exceeds the maximum level.</glossdef></glossentry>
				"""));

		assertThat(entry.glossterm()).isEqualTo("Clipping");
		assertThat(entry.definition()).isEqualTo("Distortion when a signal exceeds the maximum level.");
		assertThat(entry.surfaceForm()).isNull();
		assertThat(entry.acronym()).isNull();
		// With neither a surface form nor an acronym, every mention reads the same.
		assertThat(entry.firstMention()).isEqualTo("Clipping");
		assertThat(entry.laterMention()).isEqualTo("Clipping");
	}

	@Test
	void theFirstMentionIsSpeltOutAndTheRestAreNot() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="gl-hz"><glossterm>Hertz</glossterm>
				<glossBody><glossSurfaceForm>Hertz (Hz)</glossSurfaceForm>
				<glossAlt><glossAcronym>Hz</glossAcronym></glossAlt></glossBody></glossentry>
				"""));

		assertThat(entry.firstMention()).isEqualTo("Hertz (Hz)");
		assertThat(entry.laterMention()).isEqualTo("Hz");
	}

	@Test
	void anAbbreviationCountsAsAnAcronym() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Decibel</glossterm>
				<glossBody><glossAlt><glossAbbreviation>dB</glossAbbreviation></glossAlt></glossBody></glossentry>
				"""));

		assertThat(entry.laterMention()).isEqualTo("dB");
	}

	@Test
	void thePreferredFormWinsOverOneWithNoStatus() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Sample rate</glossterm><glossBody>
				<glossAlt><glossAcronym>SR</glossAcronym></glossAlt>
				<glossAlt><glossAcronym>Fs</glossAcronym><glossStatus value="preferred"/></glossAlt>
				</glossBody></glossentry>
				"""));

		assertThat(entry.laterMention()).isEqualTo("Fs");
	}

	@Test
	void aProhibitedOrObsoleteFormIsNotOffered() throws Exception {
		// A build skips these and falls through to the term, which is the one thing it knows is current.
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Waveform</glossterm><glossBody>
				<glossAlt><glossAcronym>WF</glossAcronym><glossStatus value="prohibited"/></glossAlt>
				<glossAlt><glossAcronym>WAV</glossAcronym><glossStatus value="obsolete"/></glossAlt>
				</glossBody></glossentry>
				"""));

		assertThat(entry.acronym()).isNull();
		assertThat(entry.laterMention()).isEqualTo("Waveform");
	}

	@Test
	void aStatusAppliesToTheFormItFollows() throws Exception {
		// <glossStatus> closes off the forms written before it inside its <glossAlt>. "BD" is prohibited
		// and "BitD", written after that status, is not — so it is the one a later mention gets.
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Bit depth</glossterm><glossBody>
				<glossAlt><glossAcronym>BD</glossAcronym><glossStatus value="prohibited"/>
				<glossAcronym>BitD</glossAcronym></glossAlt>
				</glossBody></glossentry>
				"""));

		assertThat(entry.laterMention()).isEqualTo("BitD");
	}

	@Test
	void aDefinitionWrittenOverSeveralLinesIsOneSentence() throws Exception {
		// The tutorial's definitions are wrapped in the source, and the run of spaces showed up in
		// the preview's tooltip.
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Waveform</glossterm>
				<glossdef>A visual representation of an audio signal.
				In Audacity, louder sounds appear as taller waves.</glossdef></glossentry>
				"""));

		assertThat(entry.definition())
				.isEqualTo("A visual representation of an audio signal. "
						+ "In Audacity, louder sounds appear as taller waves.");
	}

	@Test
	void markupInsideATermIsFlattenedToItsText() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Sample rate (<i>Fs</i>)</glossterm></glossentry>
				"""));

		assertThat(entry.glossterm()).isEqualTo("Sample rate (Fs)");
	}

	@Test
	void aTermVariantInsideAnAlternativeIsNotTheTerm() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<glossentry id="g"><glossterm>Normalization</glossterm><glossBody>
				<glossAlt><glossSynonym>Normalisation</glossSynonym>
				<glossterm>Peak normalization</glossterm></glossAlt></glossBody></glossentry>
				"""));

		assertThat(entry.glossterm()).isEqualTo("Normalization");
	}

	@Test
	void aTopicIsNotAGlossaryEntry() throws Exception {
		// What makes this worth a test: the caller asks of every key's target, most of which are topics,
		// and a topic's <title> must not come back as a term.
		assertThat(GlossEntry.read(write("""
				<concept id="c"><title>What is digital audio?</title><conbody/></concept>
				"""))).isNull();
	}

	@Test
	void aSpecialisedEntryIsStillAnEntry() throws Exception {
		GlossEntry entry = GlossEntry.read(write("""
				<audioterm id="g" class="- topic/topic concept/concept glossentry/glossentry audio-d/audioterm ">
				<glossterm class="- topic/title glossentry/glossterm ">Headroom</glossterm></audioterm>
				"""));

		assertThat(entry).isNotNull();
		assertThat(entry.glossterm()).isEqualTo("Headroom");
	}

	@Test
	void anEntryWithNoTermNamesNothing() throws Exception {
		assertThat(GlossEntry.read(write("<glossentry id=\"g\"><glossterm/></glossentry>"))).isNull();
	}

	@Test
	void anUnreadableFileIsTheSameAsAnAbsentOne() throws Exception {
		assertThat(GlossEntry.read(write("<glossentry><glossterm>Half"))).isNull();
		assertThat(GlossEntry.read(new File(dir, "nothing-here.dita"))).isNull();
		assertThat(GlossEntry.read(null)).isNull();
	}

	@Test
	void anHrefFragmentPicksOneEntryOutOfAGroup() throws Exception {
		// What an <abbreviated-form keyref="gl-hertz"/> has to render from: the entry sits inside a
		// <glossgroup> with its siblings, and reading the root element found a container with no
		// term, so the mention rendered empty.
		File group = write("""
				<glossgroup id="audio-units"><title>Units</title>
				  <glossentry id="gl-decibel">
				    <glossterm>decibel</glossterm>
				    <glossdef>A logarithmic unit.</glossdef>
				    <glossBody>
				      <glossSurfaceForm>decibel (dB)</glossSurfaceForm>
				      <glossAlt><glossAbbreviation>dB</glossAbbreviation></glossAlt>
				    </glossBody>
				  </glossentry>
				  <glossentry id="gl-hertz">
				    <glossterm>hertz</glossterm>
				    <glossdef>Cycles per second.</glossdef>
				  </glossentry>
				</glossgroup>
				""");

		GlossEntry decibel = GlossEntry.read(group, "glossary/audio-units.dita#gl-decibel");
		assertThat(decibel.glossterm()).isEqualTo("decibel");
		assertThat(decibel.firstMention()).isEqualTo("decibel (dB)");
		assertThat(decibel.laterMention()).isEqualTo("dB");
		assertThat(decibel.definition()).isEqualTo("A logarithmic unit.");

		// The second entry's own forms, and none of its sibling's: the reader stops at its end tag.
		GlossEntry hertz = GlossEntry.read(group, "#gl-hertz");
		assertThat(hertz.glossterm()).isEqualTo("hertz");
		assertThat(hertz.surfaceForm()).isNull();
		assertThat(hertz.acronym()).isNull();

		// A fragment naming nothing in the file, and the group itself: neither is a term.
		assertThat(GlossEntry.read(group, "#gl-nothing")).isNull();
		assertThat(GlossEntry.read(group, "glossary/audio-units.dita")).isNull();
		assertThat(GlossEntry.read(group)).isNull();
	}

	@Test
	void anEntryIsStillReadWhenTheHrefNamesItsFileAlone() throws Exception {
		// The ordinary case, which must not regress: one entry per file, keydef without a fragment.
		File entry = write("<glossentry id=\"gl-clipping\"><glossterm>Clipping</glossterm></glossentry>");
		assertThat(GlossEntry.read(entry, "glossary/g-clipping.dita").glossterm()).isEqualTo("Clipping");
		// And an element-level fragment, which addresses the topic part before the slash.
		assertThat(GlossEntry.read(entry, "g.dita#gl-clipping/term").glossterm()).isEqualTo("Clipping");
	}

	@Test
	void aDoctypeDoesNotSendItLookingForTheDtd() throws Exception {
		// Every file in the tutorial's glossary carries one, and the DTD is not beside it.
		GlossEntry entry = GlossEntry.read(write("""
				<?xml version="1.0" encoding="UTF-8"?>
				<!DOCTYPE glossentry PUBLIC "-//OASIS//DTD DITA Glossary Entry//EN" "glossentry.dtd">
				<glossentry id="gl-clipping"><glossterm>Clipping</glossterm></glossentry>
				"""));

		assertThat(entry.glossterm()).isEqualTo("Clipping");
	}
}
