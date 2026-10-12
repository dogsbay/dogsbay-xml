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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Writes characters the output encoding cannot represent as numeric character references.
 *
 * <p>An encoder asked for a character outside its charset substitutes {@code '?'} without
 * complaint, so an arrow in a Latin-1 document became a question mark on the first save and could
 * never be recovered. A numeric reference says the same thing in characters the encoding does have.
 *
 * <p>Only safe for element text and attribute values — the two places a numeric reference is read
 * back as a reference rather than as literal text. Comments, CDATA sections and processing
 * instructions must be left alone, so callers apply this at the entity-escaping hooks and nowhere
 * else.
 *
 * <p><b>Known limitation.</b> That means a character the encoding cannot represent is <em>still</em>
 * lost inside a comment, a CDATA section or a processing instruction: the encoder substitutes
 * {@code '?'} and nothing detects it. There is no way to spell such a character in those places —
 * {@code &#8594;} inside a comment is six characters of text, not an arrow — so the real options are
 * to refuse the write or to report it, not to escape. Pinned by
 * {@code FormatEngineTest.commentsAndCdataAreNotNumericEscaped}, which exists to stop the escaping
 * being extended there by mistake, and should not be read as saying the loss is acceptable.
 *
 * <p>Not thread-safe: holds one {@link CharsetEncoder}. Each writer keeps its own, which is what
 * the single-threaded serializers need.
 */
final class UnrepresentableEscaper {

    private static final java.util.Set<Charset> FULL_UNICODE = java.util.Set.of(
            java.nio.charset.StandardCharsets.UTF_8,
            java.nio.charset.StandardCharsets.UTF_16,
            java.nio.charset.StandardCharsets.UTF_16BE,
            java.nio.charset.StandardCharsets.UTF_16LE,
            Charset.forName( "UTF-32"),
            Charset.forName( "UTF-32BE"),
            Charset.forName( "UTF-32LE"));

    private final Charset charset;
    private final boolean encodesEverything;
    private CharsetEncoder encoder;

    /**
     * @param encoding the output encoding, as an XML or Java charset name (may be null or blank,
     *                 which is treated as UTF-8).
     */
    UnrepresentableEscaper( String encoding) {
        Charset resolved;
        try {
            resolved = encoding == null || encoding.isBlank()
                    ? Charset.forName( "UTF-8")
                    : Charset.forName( XMLUtilities.mapXMLEncodingToJava( encoding));
        } catch ( Exception e) {
            resolved = Charset.forName( "UTF-8");
        }
        this.charset = resolved;
        // These cover the whole of Unicode, so there is nothing to escape and no reason to walk the
        // text — the overwhelmingly common case. Named explicitly rather than tested by name prefix,
        // so a charset that merely *looks* like a Unicode one cannot turn the escaping off.
        this.encodesEverything = FULL_UNICODE.contains( resolved);
    }

    /**
     * @param text character data about to be written (may be null).
     * @return the same text, with unrepresentable characters replaced by {@code &#NNNN;}.
     */
    String escape( String text) {
        if ( text == null || text.isEmpty() || encodesEverything) {
            return text;
        }

        StringBuilder escaped = null;
        for ( int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt( i);
            int width = Character.charCount( codePoint);

            // By code point, not by char: an astral-plane character is a surrogate pair and neither
            // half can be encoded alone, so testing chars would escape both halves separately. A
            // lone surrogate is left alone: no charset can encode one, and `&#55296;` is not a legal
            // character reference, so escaping it would turn unparseable input into unparseable
            // output. Such a document is already malformed.
            if ( codePoint < 0x80 || isLoneSurrogate( codePoint, width) || canEncode( codePoint, text, i, width)) {
                if ( escaped != null) {
                    escaped.append( text, i, i + width);
                }
            } else {
                if ( escaped == null) {
                    escaped = new StringBuilder( text.length() + 16).append( text, 0, i);
                }
                escaped.append( "&#").append( codePoint).append( ';');
            }
            i += width;
        }
        return escaped == null ? text : escaped.toString();
    }

    /**
     * A surrogate that is not half of a pair. {@code codePointAt} returns a paired surrogate as the
     * combined code point (width 2), so a width of 1 is the unpaired case — the only one to leave
     * alone, since no charset can encode it and {@code &#55296;} is not a legal character reference.
     */
    private static boolean isLoneSurrogate( int codePoint, int width) {
        return width == 1 && Character.isSurrogate( (char) codePoint);
    }


    /**
     * Escape unrepresentable characters in a whole serialized document, in place.
     *
     * <p>For the save path, which writes the editor's text buffer straight to bytes rather than
     * through the serializer: an author who pastes an arrow into a Latin-1 topic had it written as
     * {@code '?'}, permanently. The serializer's own escaping cannot help there because it works on a
     * tree and this is text.
     *
     * <p>So this walks the markup, and escapes only where a numeric reference is read back <em>as</em>
     * a reference: character data, and the inside of quoted attribute values. Comments, CDATA
     * sections, processing instructions, the DOCTYPE and element or attribute names are skipped,
     * because {@code &#8594;} in any of those is six characters of text. A character the encoding
     * cannot hold is therefore still lost inside a comment, a CDATA section or a PI — see this
     * class's note above; {@link #findUnescapable} reports those so a caller can say so.
     *
     * @param xml the serialized document (may be null).
     * @return the same document, with unrepresentable characters escaped wherever that is legal.
     */
    String escapeDocument( String xml) {
        if ( xml == null || xml.isEmpty() || encodesEverything) {
            return xml;
        }
        // A byte-order mark is U+FEFF, which a non-Unicode encoding cannot store — so escaping it
        // produced `&#65279;` ahead of the XML declaration, and "Content is not allowed in prolog".
        // Handled here rather than at each call site: every caller that writes bytes can be handed a
        // document with a mark, and one of them prepends it deliberately.
        String bom = ByteOrderMark.of( xml);
        String body = bom.isEmpty() ? xml : ByteOrderMark.strip( xml);

        StringBuilder out = new StringBuilder( body.length() + 32);
        scan( body, out);
        return bom + out;
    }

    /**
     * The characters that survive escaping, because nothing could spell them where they stand.
     *
     * <p>Derived from the escaped output rather than tracked region by region: whatever is still
     * unrepresentable after {@link #escapeDocument} has run is, by construction, something this class
     * could not fix. Bookkeeping per region missed three whole categories — element and attribute
     * names, anything inside what the scanner took for a tag, and (reported but mislabelled) the
     * DOCTYPE — each of which wrote a question mark to disk while telling the caller nothing.
     *
     * @param xml the serialized document (may be null).
     * @return those characters, in order, without duplicates. Empty when the save loses nothing.
     */
    String findUnescapable( String xml) {
        if ( xml == null || xml.isEmpty() || encodesEverything) {
            return "";
        }
        StringBuilder lost = new StringBuilder();
        // Without the mark: it is set aside on the way out, not lost (see escapeDocument).
        noteUnescapable( ByteOrderMark.strip( escapeDocument( xml)), lost);
        return lost.toString();
    }

    /**
     * Walk the markup once, writing the escaped document to {@code out}.
     */
    private void scan( String xml, StringBuilder out) {
        int i = 0;
        int n = xml.length();
        while ( i < n) {
            if ( xml.startsWith( "<!--", i)) {
                i = copyVerbatim( xml, i, xml.indexOf( "-->", i + 4), 3, out);
            } else if ( xml.startsWith( "<![CDATA[", i)) {
                i = copyOrDemoteCdata( xml, i, out);
            } else if ( xml.startsWith( "<?", i)) {
                i = copyVerbatim( xml, i, xml.indexOf( "?>", i + 2), 2, out);
            } else if ( xml.startsWith( "<!DOCTYPE", i)) {
                i = copyVerbatim( xml, i, endOfDoctype( xml, i), 1, out);
            } else if ( xml.charAt( i) == '<') {
                i = copyTag( xml, i, out);
            } else {
                // Character data, where a numeric reference is a reference.
                int next = xml.indexOf( '<', i);
                int end = next < 0 ? n : next;
                out.append( escape( xml.substring( i, end)));
                i = end;
            }
        }
    }


    /**
     * Copy a CDATA section, or rewrite it as escaped text when it holds a character the encoding
     * cannot represent.
     *
     * <p>A numeric reference inside a CDATA section is six characters of text, so the character
     * cannot be spelled where it stands. But a CDATA section and the equivalent escaped text say
     * exactly the same thing to a reader, so the section can be demoted and the character kept —
     * which beats writing {@code '?'} and losing it. Only done when there is something to save: an
     * ordinary CDATA section is copied through untouched.
     *
     * @return the index just past the section.
     */
    private int copyOrDemoteCdata( String xml, int start, StringBuilder out) {
        int closeAt = xml.indexOf( "]]>", start + 9);
        int end = closeAt < 0 ? xml.length() : closeAt + 3;
        int contentStart = start + 9;
        int contentEnd = closeAt < 0 ? xml.length() : closeAt;
        String content = xml.substring( contentStart, Math.max( contentStart, contentEnd));

        if ( closeAt < 0 || !hasUnrepresentable( content)) {
            out.append( xml, start, end);
            return end;
        }

        // Demote: the markup characters CDATA was protecting have to be escaped by hand now, and
        // then the unrepresentable ones become references.
        String asText = content
                .replace( "&", "&amp;")
                .replace( "<", "&lt;")
                .replace( ">", "&gt;");

        // A trailing ']' is the one character that can still form `]]>` — not inside the content,
        // which no longer holds a '>', but by meeting a '>' that follows the section in the document.
        // `<p><![CDATA[x]]]]>>y</p>` is a legal way to write `x]]>y`, and demoting its first half
        // produced `<p>x]]>y</p>`, which no parser will read back. Interior ']' is left alone, so a
        // demoted codeblock does not fill up with references.
        int trailing = asText.length();
        while ( trailing > 0 && asText.charAt( trailing - 1) == ']') {
            trailing--;
        }
        if ( trailing < asText.length()) {
            StringBuilder safe = new StringBuilder( asText.length() + 16);
            safe.append( asText, 0, trailing);
            safe.append( "&#93;".repeat( asText.length() - trailing));
            asText = safe.toString();
        }

        out.append( escape( asText));
        return end;
    }

    /** Whether any character here is one the encoding cannot represent. */
    private boolean hasUnrepresentable( String text) {
        for ( int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt( i);
            int width = Character.charCount( codePoint);
            if ( codePoint >= 0x80 && !isLoneSurrogate( codePoint, width)
                    && !canEncode( codePoint, text, i, width)) {
                return true;
            }
            i += width;
        }
        return false;
    }

    /**
     * Copy a region that cannot hold a numeric reference, noting anything lost in it.
     *
     * @param closeAt the index where the region's closing delimiter starts, or -1 when the document
     *                is truncated — in which case the rest is copied as it stands.
     * @param closeLength the length of that closing delimiter.
     */
    private int copyVerbatim( String xml, int start, int closeAt, int closeLength, StringBuilder out) {
        int end = closeAt < 0 ? xml.length() : closeAt + closeLength;
        out.append( xml, start, end);
        return end;
    }

    /** Copy a tag, escaping inside quoted attribute values and nowhere else. */
    private int copyTag( String xml, int start, StringBuilder out) {
        int i = start;
        int n = xml.length();
        char quote = 0;
        int runStart = -1;
        while ( i < n) {
            char c = xml.charAt( i);
            if ( quote == 0 && ( c == '"' || c == '\'')) {
                quote = c;
                out.append( c);
                runStart = i + 1;
            } else if ( quote != 0 && c == quote) {
                out.append( escape( xml.substring( runStart, i)));
                out.append( c);
                quote = 0;
                runStart = -1;
            } else if ( quote == 0) {
                out.append( c);
                if ( c == '>') {
                    return i + 1;
                }
            }
            i++;
        }
        // A tag the document never closes: copy what is left of the value as it stands.
        if ( runStart >= 0) {
            out.append( escape( xml.substring( runStart)));
        }
        return n;
    }

    /** The end of a DOCTYPE, internal subset included. */
    private static int endOfDoctype( String xml, int start) {
        int bracket = xml.indexOf( '[', start);
        int close = xml.indexOf( '>', start);
        if ( bracket >= 0 && ( close < 0 || bracket < close)) {
            int endSubset = xml.indexOf( ']', bracket);
            if ( endSubset >= 0) {
                close = xml.indexOf( '>', endSubset);
            }
        }
        return close;
    }

    /** Record each distinct character in {@code region} that the encoding cannot hold. */
    private void noteUnescapable( String region, StringBuilder lost) {
        for ( int i = 0; i < region.length(); ) {
            int codePoint = region.codePointAt( i);
            int width = Character.charCount( codePoint);
            if ( codePoint >= 0x80 && !isLoneSurrogate( codePoint, width)
                    && !canEncode( codePoint, region, i, width)) {
                String ch = region.substring( i, i + width);
                if ( lost.indexOf( ch) < 0) {
                    lost.append( ch);
                }
            }
            i += width;
        }
    }

    private boolean canEncode( int codePoint, String text, int i, int width) {
        if ( encoder == null) {
            encoder = charset.newEncoder();
        }
        // The single-char form for the basic plane, which is every realistic case, so a large
        // Latin-1 or Shift_JIS document does not allocate a substring per character.
        return width == 1 ? encoder.canEncode( text.charAt( i))
                          : encoder.canEncode( text.substring( i, i + width));
    }
}
