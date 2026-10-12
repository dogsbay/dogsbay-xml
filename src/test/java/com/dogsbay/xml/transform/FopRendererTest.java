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

package com.dogsbay.xml.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;

/**
 * The XSL-FO renderer on the editor's classpath renders.
 *
 * <p>It was FOP 0.20.5, a vendored jar dated July 2003. Its API — {@code Driver}, {@code AWTRenderer},
 * {@code MessageHandler} — was replaced wholesale in FOP 1.0, and it could not render what DITA-OT's PDF plugin
 * emits, which is why the editor could not build a PDF at all.
 *
 * <p>This pins the version and the two entry points everything else uses, so a dependency change that took either
 * away would fail here rather than the next time someone asked for a PDF.
 */
class FopRendererTest {

    private static final String MINIMAL_FO = """
        <?xml version="1.0" encoding="UTF-8"?>
        <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
          <fo:layout-master-set>
            <fo:simple-page-master master-name="page" page-width="210mm" page-height="297mm">
              <fo:region-body margin="20mm"/>
            </fo:simple-page-master>
          </fo:layout-master-set>
          <fo:page-sequence master-reference="page">
            <fo:flow flow-name="xsl-region-body">
              <fo:block>Hello from the renderer.</fo:block>
            </fo:flow>
          </fo:page-sequence>
        </fo:root>
        """;

    private byte[] render(String mimeType) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = FopFactory.newInstance(new java.io.File(".").getAbsoluteFile().toURI())
                .newFop(mimeType, out);
        TransformerFactory.newInstance().newTransformer().transform(
                new StreamSource(new java.io.StringReader(MINIMAL_FO)),
                new SAXResult(fop.getDefaultHandler()));
        return out.toByteArray();
    }

    @Test
    void theRendererIsNotTheOneFromTwoThousandAndThree() {
        String version = org.apache.fop.Version.getVersion();

        assertThat(version).as("FOP version").doesNotStartWith("0.").startsWith("2.");
    }

    @Test
    void itRendersFoToPdf() throws Exception {
        byte[] pdf = render(MimeConstants.MIME_PDF);

        assertThat(pdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void itRendersTheOtherOutputsTheScenarioOffers() throws Exception {
        assertThat(render(MimeConstants.MIME_POSTSCRIPT)).isNotEmpty();
        assertThat(new String(render(MimeConstants.MIME_PLAIN_TEXT), StandardCharsets.UTF_8))
                .contains("Hello from the renderer");
    }

    @Test
    void itCannotRenderSvg() {
        // Not an oversight in the scenario dialog: FOP has had no SVG renderer since the 0.20 series, and asking
        // for one gets an UnsupportedOperationException out of the renderer's guts. The dialogs no longer offer it.
        assertThatThrownBy(() -> render(MimeConstants.MIME_SVG))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("image/svg+xml");
    }

    @Test
    void aScenarioSavedAsSvgIsToldWhatToChange() throws Exception {
        // reachable only from a scenario saved before the option went; a raw UnsupportedOperationException out of
        // FOP says nothing about what to do next
        var mimeTypeFor = ScenarioProcessor.class.getDeclaredMethod("mimeTypeFor", int.class);
        mimeTypeFor.setAccessible(true);

        assertThatThrownBy(() -> mimeTypeFor.invoke(null, ScenarioProperties.FOP_TYPE_SVG))
                .hasRootCauseInstanceOf(org.apache.fop.apps.FOPException.class)
                .rootCause()
                .hasMessageContaining("cannot produce")
                .hasMessageContaining("PDF, PostScript or text");
    }

    @Test
    void theOutputTypesTheDialogsOfferAllRender() throws Exception {
        var mimeTypeFor = ScenarioProcessor.class.getDeclaredMethod("mimeTypeFor", int.class);
        mimeTypeFor.setAccessible(true);

        for (int type : new int[] { ScenarioProperties.FOP_TYPE_PDF, ScenarioProperties.FOP_TYPE_PS,
                ScenarioProperties.FOP_TYPE_TXT }) {
            String mimeType = (String) mimeTypeFor.invoke(null, type);
            assertThat(render(mimeType)).as("output type %d (%s)", type, mimeType).isNotEmpty();
        }
    }
}
