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

package com.dogsbay.xml.review;

import java.util.List;
import java.util.function.Predicate;

/**
 * A validator that says which errors a document has, not only whether it has any. {@link ChangeMarkup}
 * uses it to check that review marks add no error of their own, which a yes-or-no validator cannot tell
 * for a document that already has errors: marks that nested a new step inside the next one went through
 * on a topic invalid for unrelated reasons (2026-09-18).
 */
public interface ErrorReporting extends Predicate<String> {

    /** The document's errors, as messages without positions (marks move text, so lines shift). */
    List<String> errors(String xml);

    @Override
    default boolean test(String xml) {
        return errors(xml).isEmpty();
    }
}
