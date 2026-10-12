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

package com.dogsbay.dogsbayaieditor.commands;

import java.util.List;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLUtilities;

/**
 * Refuses a save that would lose a character, before anything is written.
 *
 * <p>Almost everything survives a save: a character the document's encoding cannot store is written
 * as a numeric reference, and a CDATA section holding one is rewritten as escaped text. What cannot
 * be written is a character inside a <b>comment</b> or a <b>processing instruction</b>, because a
 * reference there is literal text rather than the character.
 *
 * <p><b>Why this path refuses where the editor warns.</b> A person gets a dialog after the save and
 * can judge it. An agent cannot: it would be handed success, report the task done, and leave a
 * question mark on disk that nobody traced back. Refusing costs nothing that was not already at
 * risk, because the condition only arises from a change made in this session — a comment in a
 * Latin-1 file could not have held the character on disk to begin with, since a reference inside a
 * comment is just text.
 */
final class SaveLossGuard {

    private SaveLossGuard() {}

    /**
     * Refuse if <em>any</em> of these would lose a character, before any of them is written.
     *
     * <p>Save-all has to be all or nothing. Checking inside the write loop wrote the documents ahead
     * of the offender and left the rest untouched, reporting only the offender's name — so a caller
     * could not tell what state the workspace was in.
     *
     * @param docs the documents about to be saved, in the order they would be written.
     * @throws CommandException on the first that would lose a character, before anything is written.
     */
    static void refuseIfAnyWouldLoseCharacters(List<DogsBayDocument> docs) throws CommandException {
        if (docs == null) {
            return;
        }
        for (DogsBayDocument doc : docs) {
            refuseIfSaveWouldLoseCharacters(doc);
        }
    }

    /**
     * @param doc the document about to be saved.
     * @throws CommandException with {@link CommandException.ErrorCode#ENCODING_LOSS} when saving it
     *         would lose a character, naming the characters and what to do about it.
     */
    static void refuseIfSaveWouldLoseCharacters(DogsBayDocument doc) throws CommandException {
        if (doc == null) {
            return;
        }
        String lost = doc.unescapableCharacters();
        if (lost == null || lost.isEmpty()) {
            return;
        }
        boolean one = lost.codePointCount(0, lost.length()) == 1;
        // Deliberately does not say "in a comment". It can equally be a processing instruction, the
        // DOCTYPE, an element or attribute name, or text the document's own markup has made
        // unreachable — and naming the wrong one sends the reader looking in the wrong place.
        throw new CommandException(CommandException.ErrorCode.ENCODING_LOSS,
                "Not saved: " + doc.getName() + " holds " + XMLUtilities.describeCharacters(lost)
                        + " somewhere " + doc.getEncoding() + " cannot store "
                        + (one ? "it" : "them") + " and no character reference can stand in — a "
                        + "comment, a processing instruction, the DOCTYPE, or a name. Change the "
                        + "document's declared encoding to UTF-8, or replace "
                        + (one ? "that character" : "those characters") + ", then save again.");
    }
}
