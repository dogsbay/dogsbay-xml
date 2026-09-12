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
package com.dogsbay.xml.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogsbay.xml.review.ChangeMarkup;
import com.dogsbay.xml.review.ProposalIndex;
import org.junit.jupiter.api.Test;

/** The preview shows a document with open proposals as if they were accepted. */
class DitaConverterReviewTest {

    private static final String BASE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <topic id="t"><title>Export</title><body>
        <p>Press the green button to export.</p>
        <p>Old paragraph that goes away.</p>
        </body></topic>
        """;
    private static final String NEW = """
        <?xml version="1.0" encoding="UTF-8"?>
        <topic id="t"><title>Export</title><body>
        <p>Press the red button to export.</p>
        <p>A new paragraph.</p>
        </body></topic>
        """;

    @Test
    void rendersAcceptedViewWithoutMarksOrComments() {
        String marked = new ChangeMarkup("ai:claude-acp", "2026-09-05T10:00:00Z").mark(BASE, NEW);
        assertThat(ProposalIndex.scan(marked)).isNotEmpty();
        marked = com.dogsbay.xml.review.ProposalOps.addComment(marked, marked.indexOf("</p>"), "user:ann",
                "2026-09-05T10:01:00Z", "Check the colour");

        String html = DitaConverter.convert(marked, null);

        assertThat(html).contains("red button").contains("A new paragraph.")
                .doesNotContain("green button").doesNotContain("Old paragraph")
                .doesNotContain("Check the colour").doesNotContain("<del class").doesNotContain("<ins class");
    }

    @Test
    void plainDocumentUnaffected() {
        assertThat(DitaConverter.convert(BASE, null)).contains("green button");
    }

    @Test
    void showChangesDrawsInsertionsDeletionsAndComments() {
        String marked = new ChangeMarkup("ai:claude-acp", "2026-09-05T10:00:00Z").mark(BASE, NEW);
        marked = com.dogsbay.xml.review.ProposalOps.addComment(marked, marked.indexOf("</p>"), "user:ann",
                "2026-09-05T10:01:00Z", "Check the colour");

        String html = DitaConverter.convert(marked, null, new PreviewOptions(null, null, true));

        assertThat(html).contains("<ins class=\"review-new\"").contains("red")
                .contains("<del class=\"review-deleted\"").contains("green")
                .contains("A new paragraph.").contains("Old paragraph")
                .contains("class=\"review-comment\"").contains("user:ann").contains("Check the colour");
    }

    @Test
    void showChangesHidesTheEnginesPreviousVersionNote() {
        String withNote = "<topic id=\"t\"><title>T</title><body><p status=\"changed\" rev=\"ai:x\">Now."
                + "<draft-comment outputclass=\"dogsbay-previous\">Previous version kept for review."
                + "<data name=\"dogsbay:previous-xml\" value=\"&lt;p&gt;Then.&lt;/p&gt;\"/></draft-comment></p></body></topic>";
        String html = DitaConverter.convert(withNote, null, new PreviewOptions(null, null, true));
        assertThat(html).contains("<mark class=\"review-changed\"").contains("Now.")
                .doesNotContain("Previous version kept").doesNotContain("Then.");
    }

    @Test
    void aPlainRevisionIsNotDrawnAsAProposal() {
        String human = "<topic id=\"t\"><title>T</title><body><p status=\"new\" rev=\"2.1\">Added in 2.1.</p></body></topic>";
        String html = DitaConverter.convert(human, null, new PreviewOptions(null, null, true));
        assertThat(html).contains("Added in 2.1.").doesNotContain("<ins class");
    }

    @Test
    void wrapperProposalsAndDecidedCommentsAreNotDrawnAsChanges() {
        String marked = "<topic id=\"t\"><title>T</title><body><p>Press the "
                + "<b status=\"deleted\" rev=\"ai:claude-acp review-wrapper\">red</b> button "
                + "<ph status=\"new\" rev=\"ai:claude-acp review-wrapper\">now</ph></p></body></topic>";
        String html = DitaConverter.convert(marked, null, new PreviewOptions(null, null, true));
        assertThat(html).contains("red").contains("now").doesNotContain("<del class").doesNotContain("<ins class");

        String decided = "<topic id=\"t\"><title>T</title><body><p>Text<draft-comment author=\"user:ann\" "
                + "disposition=\"rejected\">Old worry</draft-comment><draft-comment>hand note</draft-comment>"
                + "<draft-comment author=\"ai:x\">Still open</draft-comment></p></body></topic>";
        String html2 = DitaConverter.convert(decided, null, new PreviewOptions(null, null, true));
        assertThat(html2).contains("Still open").doesNotContain("Old worry").doesNotContain("hand note");
    }
}
