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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.List;

/**
 * The one sentence both verdicts end with, written once.
 *
 * <p>The command line and the Project Validation pane each write their own verdict, and twice now the two have
 * drifted: the pane still said "No pages to read" months after the command line had stopped. The part they say
 * about the built pages lives here so there is only one of it.
 */
public final class CheckWording {

	/**
	 * Past this many, the deliverables are counted rather than named. A project with seven of them read
	 * "every link in the pages of full, beginner-mac, beginner-windows, podcaster-linux, review,
	 * install-variants, collection leads somewhere" — accurate, and no longer a sentence. The command line
	 * names them on the {@code output   clean (...)} line immediately above either way.
	 */
	static final int MOST_NAMED = 3;

	private CheckWording() {
	}

	/**
	 * What the output stage found, with no closing punctuation: the caller decides what follows it.
	 *
	 * @param outputsChecked the deliverables whose pages were read, never null ({@link ProjectCheck}
	 *     replaces a null with an empty list) and empty when every deliverable wrote something with no
	 *     pages in it
	 */
	public static String linksVerdict(List<String> outputsChecked) {
		if (outputsChecked.isEmpty()) {
			// Not "every link ... leads somewhere": no link was looked at. A PDF-only project reaches here.
			return "every deliverable wrote something";
		}
		String where = outputsChecked.size() <= MOST_NAMED
				? "the pages of " + String.join(", ", outputsChecked)
				: "the pages of " + outputsChecked.size() + " deliverables";
		return "every link in " + where + " leads somewhere";
	}
}
