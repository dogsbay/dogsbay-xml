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

/**
 * A leading byte-order mark, set aside while a document is parsed and put back afterwards.
 *
 * <p>Once the bytes are decoded a BOM is the single character U+FEFF, sitting ahead of the XML
 * declaration where a parser rejects it outright — "Content is not allowed in prolog" — so files
 * saved by editors that write one could not be processed at all.
 *
 * <p>It is put back rather than dropped: whether a file carries a BOM is the file's business, and
 * formatting it is not the place to decide otherwise.
 */
public final class ByteOrderMark {

    /** A byte-order mark, as it appears once the bytes have been decoded. */
    public static final String BOM = "﻿";

    private ByteOrderMark() {}

    /** The document's leading BOM, or an empty string when it has none. */
    public static String of( String text) {
        return text != null && text.startsWith( BOM) ? BOM : "";
    }

    /** {@code text} without its leading BOM, if it had one. */
    public static String strip( String text) {
        return text != null && text.startsWith( BOM) ? text.substring( BOM.length()) : text;
    }
}
