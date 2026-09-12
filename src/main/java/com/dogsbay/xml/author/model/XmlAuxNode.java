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

package com.dogsbay.xml.author.model;

/**
 * A comment or processing instruction preserved across the round-trip.
 * Aux nodes found between blocks attach to the following block (or to the
 * parent's trailing list when at the end of its content) and are re-emitted
 * in the same place on export.
 */
public record XmlAuxNode(Kind kind, String target, String text) {

    public enum Kind { COMMENT, PROCESSING_INSTRUCTION }

    public static XmlAuxNode comment(String text) {
        return new XmlAuxNode(Kind.COMMENT, null, text);
    }

    public static XmlAuxNode pi(String target, String text) {
        return new XmlAuxNode(Kind.PROCESSING_INSTRUCTION, target, text);
    }
}
