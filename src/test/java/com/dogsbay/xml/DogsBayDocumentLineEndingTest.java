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

package com.dogsbay.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for line ending detection and preservation in DogsBayDocument.
 */
class DogsBayDocumentLineEndingTest {

    @Test
    void detectsLfLineEndings() {
        assertEquals(DogsBayDocument.LINE_ENDING_LF,
            DogsBayDocument.detectLineEnding("line1\nline2\nline3\n"));
    }

    @Test
    void detectsCrlfLineEndings() {
        assertEquals(DogsBayDocument.LINE_ENDING_CRLF,
            DogsBayDocument.detectLineEnding("line1\r\nline2\r\nline3\r\n"));
    }

    @Test
    void detectsCrLineEndings() {
        assertEquals(DogsBayDocument.LINE_ENDING_CR,
            DogsBayDocument.detectLineEnding("line1\rline2\rline3\r"));
    }

    @Test
    void detectsMixedLineEndingsPicksDominant() {
        // 3 CRLF, 1 LF — CRLF should win
        assertEquals(DogsBayDocument.LINE_ENDING_CRLF,
            DogsBayDocument.detectLineEnding("a\r\nb\r\nc\r\nd\n"));
    }

    @Test
    void defaultsToLfForNoLineEndings() {
        assertEquals(DogsBayDocument.LINE_ENDING_LF,
            DogsBayDocument.detectLineEnding("no line endings here"));
    }

    @Test
    void defaultsToLfForNull() {
        assertEquals(DogsBayDocument.LINE_ENDING_LF,
            DogsBayDocument.detectLineEnding(null));
    }

    @Test
    void defaultsToLfForEmptyString() {
        assertEquals(DogsBayDocument.LINE_ENDING_LF,
            DogsBayDocument.detectLineEnding(""));
    }

    @Test
    void detectsSingleCrlf() {
        assertEquals(DogsBayDocument.LINE_ENDING_CRLF,
            DogsBayDocument.detectLineEnding("one line\r\n"));
    }

    @Test
    void detectsSingleLf() {
        assertEquals(DogsBayDocument.LINE_ENDING_LF,
            DogsBayDocument.detectLineEnding("one line\n"));
    }
}
