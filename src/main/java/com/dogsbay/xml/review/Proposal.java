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

/**
 * One reviewable change or comment in a document, located by the source
 * offsets of the element that carries it. Ids are positional
 * ({@code kind@start}) and valid for the document text they were indexed from.
 *
 * @param synthetic true for the {@code <ph>} wrappers the engine itself wrote
 *                  around changed text (they carry {@code review-mark}); such a
 *                  wrapper is unwrapped when its mark comes off, whereas a real
 *                  element only loses its attributes
 * @param wrapper   true when the element's insertion or removal wrapped or
 *                  unwrapped content that was already there
 */
public record Proposal(
        Kind kind,
        String id,
        String author,
        String time,
        String text,
        int start,
        int end,
        String element,
        boolean synthetic,
        boolean wrapper) {

    public enum Kind { INSERT, DELETE, CHANGED, COMMENT }

    /** The attribute value marking a span removed by a proposal, for DITAVAL exclusion. */
    public static final String DELETED_PROP = Marks.DELETED;

    public boolean isChange() {
        return kind != Kind.COMMENT;
    }
}
