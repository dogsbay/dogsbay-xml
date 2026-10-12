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

package com.dogsbay.dogsbayaieditor.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

/**
 * The PDF fonts are in the bundle, registered, and used.
 *
 * <p>Stock DITA-OT renders PDF in the base-14 PostScript fonts, and Times-Roman has no Hebrew, no minus sign and no
 * arrows. FOP resolves a font-family list to one font and does not consult it again per character, so a missing
 * character is dropped with a warning and nothing on the page — a PDF quietly missing text.
 *
 * <p>Liberation rather than DejaVu because DejaVu's serif face has no Hebrew, and body text is serif.
 */
class BundledPdfFontsTest {

    private static final String BUNDLE =
            "/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-"
            + DefaultDitaOtFramework.VERSION + ".zip";

    /** Every entry in the bundled framework zip. */
    private static List<String> entries() throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream in = BundledPdfFontsTest.class.getResourceAsStream(BUNDLE)) {
            assumeTrue(in != null, "no bundled framework in this build");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }

    /** One entry's contents. */
    private static String read(String path) throws Exception {
        try (InputStream in = BundledPdfFontsTest.class.getResourceAsStream(BUNDLE)) {
            assumeTrue(in != null, "no bundled framework in this build");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().equals(path)) {
                        return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        throw new IllegalStateException("Not in the bundle: " + path);
    }

    @Test
    void everyFaceIsThere() throws Exception {
        List<String> fonts = entries().stream()
                .filter(name -> name.contains("/org.dita.pdf2/fonts/") && name.endsWith(".ttf"))
                .toList();

        // three families, four faces each: a bold Hebrew heading needs the bold face as much as the regular
        assertThat(fonts).hasSize(12);
        for (String family : new String[] { "LiberationSerif", "LiberationSans", "LiberationMono" }) {
            for (String face : new String[] { "Regular", "Bold", "Italic", "BoldItalic" }) {
                assertThat(fonts).anyMatch(name -> name.endsWith(family + "-" + face + ".ttf"));
            }
        }
    }

    @Test
    void theLicenceTravelsWithThem() throws Exception {
        assertThat(entries()).anyMatch(name -> name.contains("/fonts/LICENSE.liberation-fonts"));
    }

    @Test
    void fopIsToldWhereTheyAreRelativeToItsOwnConfig() throws Exception {
        String xconf = read("dita-ot/plugins/org.dita.pdf2.fop/cfg/fop.xconf");

        assertThat(xconf).contains("<font embed-url=\"../../org.dita.pdf2/fonts/LiberationSerif-Regular.ttf\">");
        assertThat(xconf).contains("<font-triplet name=\"Liberation Serif\" style=\"normal\" weight=\"bold\"/>");
        // an absolute path would be the build machine's, and would resolve to nothing on anyone else's
        assertThat(xconf).doesNotContain("embed-url=\"/");
    }

    @Test
    void theMappingsNameThemFirstAndKeepTheStockListBehind() throws Exception {
        String mappings = read("dita-ot/plugins/org.dita.pdf2/cfg/fo/font-mappings.xml");

        assertThat(mappings).contains("Liberation Serif, Times New Roman, Times,");
        assertThat(mappings).contains("Liberation Sans, Helvetica,");
        assertThat(mappings).contains("Liberation Mono, Courier New, Courier,");
    }

    @Test
    void theStockMappingShipsSoTimesCanBeHadBack() throws Exception {
        String stock = read("dita-ot/plugins/org.dita.pdf2/cfg/fo/font-mappings-base14.xml");

        // DITA-OT's own file, unchanged: copy it into a customization directory as fo/font-mappings.xml
        assertThat(stock).contains("Times New Roman, Times,").doesNotContain("Liberation");
    }
}
