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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file's text in the encoding it declares, and how it was stored, so a change can be written back the
 * same way: a byte order mark, else the XML declaration's encoding, else UTF-8. The mark is dropped from
 * the text, as the editor does, and put back on writing.
 *
 * @param text          the decoded text
 * @param charset       the file's encoding
 * @param byteOrderMark the file's byte order mark, empty when it has none
 */
public record DecodedFile(String text, Charset charset, byte[] byteOrderMark) {

    private static final Pattern ENCODING = Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    /**
     * Read {@code file}.
     *
     * @throws CharacterCodingException when the bytes are not valid in the file's encoding
     */
    public static DecodedFile read(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return decode(b, 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return decode(b, 2, StandardCharsets.UTF_16BE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return decode(b, 2, StandardCharsets.UTF_16LE);
        }
        Charset charset = StandardCharsets.UTF_8;
        Matcher m = ENCODING.matcher(new String(b, 0, Math.min(b.length, 200), StandardCharsets.ISO_8859_1));
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1));
            } catch (RuntimeException unknown) {
                // keep UTF-8
            }
            // Without a byte order mark, UTF-16 is big-endian; the UTF-16 encoder would add a mark the file never had.
            if (charset.equals(StandardCharsets.UTF_16)) {
                charset = StandardCharsets.UTF_16BE;
            }
        }
        return decode(b, 0, charset);
    }

    private static DecodedFile decode(byte[] b, int mark, Charset charset) throws CharacterCodingException {
        String text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b, mark, b.length - mark)).toString();
        byte[] bom = new byte[mark];
        System.arraycopy(b, 0, bom, 0, mark);
        return new DecodedFile(text, charset, bom);
    }

    /**
     * {@code newText} in the file's charset, after its byte order mark.
     *
     * @throws CharacterCodingException when the charset cannot store a character, rather than silently
     *         writing a replacement
     */
    public byte[] encode(String newText) throws CharacterCodingException {
        ByteBuffer buffer = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(newText));
        byte[] body = new byte[buffer.remaining()];
        buffer.get(body);
        byte[] out = new byte[byteOrderMark.length + body.length];
        System.arraycopy(byteOrderMark, 0, out, 0, byteOrderMark.length);
        System.arraycopy(body, 0, out, byteOrderMark.length, body.length);
        return out;
    }
}
