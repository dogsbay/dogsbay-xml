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

/** Replacing an exact piece of a text, for {@link EditDocumentCommand}. */
public final class TextEdit {

    private TextEdit() {
    }

    /** An edit's result: the new text, or why the edit did not apply. */
    public record Result(String text, int occurrences, String error) {
        public boolean applied() {
            return error == null;
        }
    }

    /**
     * Replace {@code find} in {@code text}: the only occurrence, or every occurrence when
     * {@code all}. An edit that matches nothing, or matches more than once where one was
     * meant, changes nothing and says so: the caller can then include more of the
     * surrounding text, or ask for every occurrence.
     */
    public static Result apply(String text, String find, String replace, boolean all) {
        if (find == null || find.isEmpty()) {
            return new Result(text, 0, "find must not be empty");
        }
        int occurrences = 0;
        for (int at = text.indexOf(find); at >= 0; at = text.indexOf(find, at + find.length())) {
            occurrences++;
        }
        if (occurrences == 0) {
            return new Result(text, 0, "The text to replace is not in the document. It must match exactly, "
                    + "whitespace and line breaks included.");
        }
        if (occurrences > 1 && !all) {
            return new Result(text, occurrences, "The text to replace appears " + occurrences + " times. "
                    + "Include enough of the surrounding text to make it unique, or pass all=true to replace "
                    + "every occurrence.");
        }
        String edited = all ? text.replace(find, replace)
                : new StringBuilder(text).replace(text.indexOf(find), text.indexOf(find) + find.length(), replace).toString();
        return new Result(edited, occurrences, null);
    }
}
