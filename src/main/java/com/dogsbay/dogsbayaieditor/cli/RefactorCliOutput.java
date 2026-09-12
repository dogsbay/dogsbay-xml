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

package com.dogsbay.dogsbayaieditor.cli;

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/** Shared CLI rendering for refactor plans/results. */
final class RefactorCliOutput {

    private RefactorCliOutput() {
    }

    /** Prints warnings, headline, edits, and outcome; returns the exit code. */
    static int print(RefactorResult result, String headline) {
        for (String warning : result.warnings()) {
            System.out.println("WARNING: " + warning);
        }
        System.out.println(headline);
        for (RefactorResult.AttributeEditInfo edit : result.edits()) {
            if (edit.line() >= 0) {
                System.out.printf("  %s:%d  @%s: \"%s\" → \"%s\"%n",
                        edit.file(), edit.line(), edit.attribute(),
                        edit.oldValue(), edit.newValue());
            } else {
                System.out.printf("  %s  %s %s%n",
                        edit.file(), edit.attribute(), edit.newValue());
            }
        }
        if (result.applied()) {
            System.out.println(result.editsApplied() + " edit(s) in "
                    + result.filesChanged() + " file(s).");
            for (String failure : result.failures()) {
                System.out.println("FAILED: " + failure);
            }
            return result.failures().isEmpty() ? 0 : 1;
        }
        System.out.println(result.edits().size()
                + " edit(s) planned. Re-run with --apply to execute.");
        return 0;
    }
}
