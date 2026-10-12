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

package com.dogsbay.dogsbayaieditor.validate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shorter validator messages for reports over many files. A DITA content-model
 * error spells out the whole model (sixty-odd permitted elements, about 500
 * characters), and a project report repeated it once per file per deliverable.
 * The element name and the fact of the mismatch carry the decision; a
 * single-file validation keeps the full text for fixing.
 */
public final class ValidationMessages {

    /** Xerces DTD: The content of element type "x" must match "(a|b|...)". */
    private static final Pattern DTD_CONTENT = Pattern.compile(
            "^The content of element type \"([^\"]+)\" must match \".*\"\\.?$", Pattern.DOTALL);

    /** Xerces XSD: cvc-complex-type.2.4.a: Invalid content ... One of '{...}' is expected. */
    private static final Pattern XSD_EXPECTED = Pattern.compile(
            "^(cvc-complex-type\\.2\\.4\\.[a-z]: .*?)\\s*One of '\\{.*\\}' is expected\\.?$", Pattern.DOTALL);

    private ValidationMessages() {
    }

    /** The message with any content-model enumeration removed; other messages unchanged. */
    public static String compact(String message) {
        if (message == null) {
            return null;
        }
        Matcher dtd = DTD_CONTENT.matcher(message);
        if (dtd.matches()) {
            return "The content of element type \"" + dtd.group(1) + "\" does not match its content model.";
        }
        Matcher xsd = XSD_EXPECTED.matcher(message);
        if (xsd.matches()) {
            return xsd.group(1).trim();
        }
        return message;
    }
}
