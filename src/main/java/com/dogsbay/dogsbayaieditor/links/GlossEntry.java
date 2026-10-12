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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * What a glossary entry offers the places that have to show a term in someone else's sentence.
 *
 * <p>A key whose target is a {@code <glossentry>} carries its name in {@code <glossterm>}, not in a
 * {@code <title>}, so everything that reads titles comes back with nothing and the reader is shown the key's
 * own name — or, for an {@code <abbreviated-form>}, nothing at all.
 *
 * <p>Which of the forms a reference gets is the referring document's business, not this file's: a build gives
 * the first mention of a key the surface form and every later one the acronym. All of them are read here and
 * the caller chooses.
 *
 * @param glossterm the term itself, never null in a well-formed entry
 * @param surfaceForm the {@code <glossSurfaceForm>}, or null — "Hertz (Hz)" for a term "Hertz"
 * @param acronym the preferred {@code <glossAcronym>} or {@code <glossAbbreviation>}, or null
 * @param definition the {@code <glossdef>}, or null; what a build puts in the hover text
 */
public record GlossEntry(String glossterm, String surfaceForm, String acronym, String definition) {

	/** How a build reads the first mention of a key: the surface form when there is one. */
	public String firstMention() {
		return surfaceForm != null ? surfaceForm : glossterm;
	}

	/**
	 * How a build reads every later mention: the acronym when there is one.
	 *
	 * <p>Not the surface form — "Hertz (Hz)" belongs to the first mention, and a page that said it twice
	 * would be saying the reader had not been told.
	 */
	public String laterMention() {
		return acronym != null ? acronym : glossterm;
	}

	/**
	 * The entry in {@code file}, or null when it holds something else — a topic, a map, or nothing readable.
	 *
	 * <p>Parsed rather than read with a pattern, which {@link TopicTitle} can get away with: choosing among
	 * several acronyms is a question about siblings, and a glossary has a handful of entries where a project
	 * has hundreds of topics.
	 */
	public static GlossEntry read(File file) {
		return read(file, null);
	}

	/**
	 * The entry {@code href}'s fragment names, or the file's own entry when it names none.
	 *
	 * <p>A {@code <glossgroup>} holds several entries in one file, each referenced as
	 * {@code units.dita#gl-hertz}. Reading only the root element returned nothing for every one of
	 * them, so an {@code <abbreviated-form>} pointing into a group rendered empty — the same fault
	 * that was reported for root-level entries on 2026-10-08, in the one place it had been missed.
	 * An href naming the group itself still answers null: a container is not a term.
	 *
	 * @param href the key's href, whose fragment (if any) picks the entry; the path part is ignored
	 */
	public static GlossEntry read(File file, String href) {
		if (file == null || !file.isFile() || !looksLikeAnEntry(file)) {
			return null;
		}
		Reader reader = new Reader(fragmentOf(href));
		try {
			var sax = HardenedSax.newReader();
			sax.setContentHandler(reader);
			sax.parse(new InputSource(file.toURI().toString()));
		} catch (StopParsing stop) {
			// the root element said this is not a glossary entry; nothing more to read
			return null;
		} catch (Exception e) {
			return null;        // unreadable is the same as absent to every caller
		}
		return reader.entry();
	}

	/** The id a fragment names, or null; {@code #topic/element} addresses the topic part. */
	private static String fragmentOf(String href) {
		if (href == null) {
			return null;
		}
		int hash = href.indexOf('#');
		if (hash < 0 || hash == href.length() - 1) {
			return null;
		}
		String fragment = href.substring(hash + 1);
		int slash = fragment.indexOf('/');
		return slash < 0 ? fragment : fragment.substring(0, slash);
	}

	/** How much of the file the word {@code glossentry} has to appear in for it to be worth parsing. */
	private static final int HEAD_BYTES = 4096;

	/**
	 * A cheap no for the files that are not entries, which is most of them.
	 *
	 * <p>The caller asks of every key's target, and setting up a parser costs about a millisecond — real
	 * money on a map with hundreds of keys. An entry says {@code glossentry} in its root tag, its doctype
	 * or its {@code @class}; a file whose head says it nowhere cannot be one. A topic that happens to use
	 * the word is parsed and turned down, which costs what this used to cost for everything.
	 */
	private static boolean looksLikeAnEntry(File file) {
		try (var in = new java.io.FileInputStream(file)) {
			byte[] head = in.readNBytes(HEAD_BYTES);
			String text = new String(head, java.nio.charset.StandardCharsets.UTF_8);
			// "glossgroup" as well: a group's first entry can sit past this window, behind a title
			// and a prolog, and the root tag is the one thing guaranteed to be inside it.
			return text.contains("glossentry") || text.contains("glossgroup");
		} catch (Exception e) {
			return false;
		}
	}

	/** Thrown to abandon a file whose root element is not a glossentry. */
	private static final class StopParsing extends SAXException {
		private static final long serialVersionUID = 1L;
	}

	/**
	 * One alternative form and the status that follows it.
	 *
	 * <p>A {@code <glossStatus>} applies to the siblings before it inside its {@code <glossAlt>}, so the
	 * status is recorded against the form it was met after rather than looked up again later.
	 */
	private record Alt(String text, String status) {
	}

	/** Collects the forms, in document order, with each one's status. */
	private static final class Reader extends DefaultHandler {

		/** The {@code @id} to look for inside the file, or null when the root element is the entry. */
		private final String wantedId;
		/** Elements open inside the wanted entry; -1 once it has closed. */
		private int inside;
		private boolean done;

		Reader() {
			this(null);
		}

		Reader(String wantedId) {
			this.wantedId = wantedId;
		}

		private boolean sawRoot;
		private String glossterm;
		private String surfaceForm;
		private String definition;
		private final List<Alt> alts = new ArrayList<>();
		/** Forms met in the current {@code <glossAlt>} that no status has closed off yet. */
		private final List<String> pending = new ArrayList<>();

		private final StringBuilder text = new StringBuilder();
		private String collecting;
		private int depth;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes a) throws SAXException {
			if (done) {
				return;
			}
			String name = local(localName, qName);
			if (!sawRoot) {
				if (wantedId == null) {
					sawRoot = true;
					if (!isGlossentry(name, a.getValue("class"))) {
						throw new StopParsing();
					}
					return;
				}
				// Skipping towards the entry the fragment names, however deep the groups go.
				if (isGlossentry(name, a.getValue("class")) && wantedId.equals(a.getValue("id"))) {
					sawRoot = true;
				}
				return;
			}
			if (wantedId != null) {
				inside++;
			}
			switch (name) {
				// A glossterm nested in a glossAlt is a variant of the term, not the term: only the
				// entry's own one counts, and it is the first one met.
				// First non-empty one wins, as a build's first-in-document-order select does. A later
				// glossterm is a variant inside a glossAlt, not the entry's own term.
				case "glossterm" -> start(glossterm == null ? "glossterm" : null);
				case "glossSurfaceForm" -> start(surfaceForm == null ? "glossSurfaceForm" : null);
				case "glossdef" -> start(definition == null ? "glossdef" : null);
				case "glossAcronym", "glossAbbreviation" -> start("alt");
				case "glossStatus" -> {
					// Closes off every form met since the last status in this glossAlt.
					String status = a.getValue("value");
					pending.forEach(form -> alts.add(new Alt(form, status)));
					pending.clear();
				}
				default -> {
					if (collecting != null) {
						depth++;    // markup inside a form: its text is part of the form
					}
				}
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			if (collecting != null) {
				text.append(ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			if (done) {
				return;
			}
			// The wanted entry's own end tag: everything after it belongs to another entry.
			if (sawRoot && wantedId != null && inside-- == 0) {
				done = true;
				return;
			}
			if (collecting == null) {
				if ("glossAlt".equals(local(localName, qName))) {
					// Forms with no status after them, kept in the order they were written.
					pending.forEach(form -> alts.add(new Alt(form, null)));
					pending.clear();
				}
				return;
			}
			if (depth > 0) {
				depth--;
				return;
			}
			// Whitespace collapsed as a build's normalize-space collapses it: a term or a definition
			// written over three lines is one sentence, not one carrying the source's line breaks.
			String value = text.toString().trim().replaceAll("\\s+", " ");
			switch (collecting) {
				case "glossterm" -> glossterm = blankToNull(value);
				case "glossSurfaceForm" -> surfaceForm = blankToNull(value);
				case "glossdef" -> definition = blankToNull(value);
				case "alt" -> {
					if (!value.isEmpty()) {
						pending.add(value);
					}
				}
				default -> { }
			}
			collecting = null;
			text.setLength(0);
		}

		/** Null when the file held no term: a glossentry without one names nothing. */
		GlossEntry entry() {
			if (glossterm == null) {
				return null;
			}
			return new GlossEntry(glossterm, surfaceForm, acronym(), definition);
		}

		/**
		 * The acronym a build would show, by the order a build looks in: one marked
		 * {@code preferred}, then one with any status that is not {@code prohibited} or {@code obsolete},
		 * then one with no status at all.
		 */
		private String acronym() {
			for (Alt alt : alts) {
				if ("preferred".equals(alt.status())) {
					return alt.text();
				}
			}
			for (Alt alt : alts) {
				if (alt.status() != null
						&& !"prohibited".equals(alt.status()) && !"obsolete".equals(alt.status())) {
					return alt.text();
				}
			}
			for (Alt alt : alts) {
				if (alt.status() == null) {
					return alt.text();
				}
			}
			return null;
		}

		private void start(String what) {
			collecting = what;
			depth = 0;
			text.setLength(0);
		}

		private static boolean isGlossentry(String name, String ditaClass) {
			// A specialisation of glossentry has its own element name and says so in @class, which is
			// present when the file was read with its DTD and absent when it was not.
			return "glossentry".equals(name)
					|| (ditaClass != null && ditaClass.contains(" glossentry/glossentry "));
		}

		private static String local(String localName, String qName) {
			return localName != null && !localName.isEmpty() ? localName : qName;
		}

		private static String blankToNull(String value) {
			return value.isEmpty() ? null : value;
		}
	}
}
