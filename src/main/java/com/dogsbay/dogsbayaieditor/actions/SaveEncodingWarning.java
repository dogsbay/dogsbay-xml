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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.Component;

import javax.swing.JOptionPane;

import com.dogsbay.xml.DogsBayDocument;

/**
 * Says so when a save could not keep a character.
 *
 * <p>Almost everything survives: a character the file's encoding has no byte for is written as a
 * numeric reference, and a CDATA section holding one is rewritten as escaped text. What cannot be
 * saved is a character inside a <b>comment</b> or a <b>processing instruction</b>, because a
 * reference there is six characters of text rather than the character — there is no spelling for it.
 *
 * <p>That used to happen silently, leaving a question mark on disk and nothing to say why, so this
 * is deliberately a dialog rather than a status line: it is rare, and it is the author's content.
 */
final class SaveEncodingWarning {

    private SaveEncodingWarning() {}

    /**
     * Warn if saving {@code document} lost anything. Does nothing in the ordinary case.
     *
     * @param parent   the component to centre the dialog on.
     * @param document the document just saved.
     */
    static void warnIfAnythingWasLost( Component parent, DogsBayDocument document) {
        if ( document == null) {
            return;
        }
        String lost;
        try {
            lost = document.unescapableCharacters();
        } catch ( Exception e) {
            return;   // never let a warning break a save that already succeeded
        }
        if ( lost == null || lost.isEmpty()) {
            return;
        }

        JOptionPane.showMessageDialog( parent,
                "Saved " + document.getName() + ", but " + describe( lost) + " could not be written in "
                        + document.getEncoding() + ".\n\n"
                        + "It sits where no character reference can stand in — a comment, a "
                        + "processing instruction, the DOCTYPE, or a name.\n"
                        + "Save the file as UTF-8 to keep it, or replace the character.",
                "Saved with a loss",
                JOptionPane.WARNING_MESSAGE);
    }

    /** Shared with the RPC refusal, so both say the same thing. */
    private static String describe( String characters) {
        return com.dogsbay.xml.XMLUtilities.describeCharacters( characters);
    }

}
