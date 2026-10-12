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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

/** check_output_links: links in built output that a reader would find broken. */
class CheckOutputLinksCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private List<BrokenRef> check() throws Exception {
        return exec.execute(new CheckOutputLinksCommand(dir.toString()));
    }

    private void page(String name, String body) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "<html><body>\n" + body + "\n</body></html>");
    }

    @Test
    void aLinkToAPageThatWasNeverBuiltIsReported() throws Exception {
        page("index.html", "<a href=\"missing.html\">Gone</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> {
                    assertThat(ref.value()).isEqualTo("missing.html");
                    assertThat(ref.attribute()).isEqualTo("href");
                    assertThat(ref.line()).isEqualTo(2);
                    assertThat(ref.reason()).contains("missing.html");
                });
    }

    @Test
    void aLinkToAPageThatWasBuiltIsNotReported() throws Exception {
        page("index.html", "<a href=\"topic.html\">There</a>");
        page("topic.html", "<p>Here</p>");

        assertThat(check()).isEmpty();
    }

    @Test
    void aFragmentNoPageCarriesIsReported() throws Exception {
        page("index.html", "<a href=\"topic.html#section-two\">Two</a>");
        page("topic.html", "<p id=\"section-one\">One</p>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("section-two"));
    }

    @Test
    void aFragmentThePageCarriesIsNotReported() throws Exception {
        page("index.html", "<a href=\"topic.html#section-one\">One</a><a href=\"#top\">Top</a>");
        page("topic.html", "<p id=\"section-one\">One</p>");
        Files.writeString(dir.resolve("index.html"),
                "<html><body id=\"top\">\n<a href=\"topic.html#section-one\">One</a>"
                        + "<a href=\"#top\">Top</a>\n</body></html>");

        assertThat(check()).isEmpty();
    }

    @Test
    void anImageTheBuildDidNotCarryAcrossIsReported() throws Exception {
        page("index.html", "<img src=\"images/logo.png\"/>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.attribute()).isEqualTo("src"));
    }

    @Test
    void anImageTheBuildCarriedAcrossIsNotReported() throws Exception {
        page("index.html", "<img src=\"images/logo.png\"/>");
        Files.createDirectories(dir.resolve("images"));
        Files.writeString(dir.resolve("images/logo.png"), "not really a png");

        assertThat(check()).isEmpty();
    }

    @Test
    void aLinkToAFolderFindsItsIndex() throws Exception {
        page("index.html", "<a href=\"guide/\">Guide</a><a href=\"reference/\">Reference</a>");
        page("guide/index.html", "<p>Guide</p>");
        Files.createDirectories(dir.resolve("reference"));

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.value()).isEqualTo("reference/"));
    }

    @Test
    void aLinkFromTheSiteRootIsResolvedAgainstTheOutputFolder() throws Exception {
        page("guide/index.html", "<a href=\"/topic.html\">Root</a>");
        page("topic.html", "<p>Here</p>");

        assertThat(check()).isEmpty();
    }

    @Test
    void linksThatLeaveTheSiteAreNotFollowed() throws Exception {
        page("index.html", "<a href=\"https://example.com/nothing.html\">Out</a>"
                + "<a href=\"mailto:someone@example.com\">Mail</a>"
                + "<a href=\"//cdn.example.com/x.js\">CDN</a>");

        assertThat(check()).isEmpty();
    }

    @Test
    void aLinkThatClimbsOutOfTheOutputIsReported() throws Exception {
        page("guide/index.html", "<a href=\"../../elsewhere.html\">Out</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("outside"));
    }

    @Test
    void aPercentEncodedNameIsMatchedAgainstTheFileOnDisk() throws Exception {
        page("index.html", "<a href=\"getting%20started.html\">Start</a>");
        page("getting started.html", "<p>Start</p>");

        assertThat(check()).isEmpty();
    }


    @Test
    void aRelativeFolderArgumentReportsRatherThanCrashing() throws Exception {
        page("index.html", "<a href=\"missing.html\">Gone</a>");
        // the documented way to run it is `check-output-links out/` — a path relative to where the process started
        Path relative = Path.of("").toAbsolutePath().relativize(dir);
        assertThat(relative.isAbsolute()).isFalse();

        assertThat(OutputLinkChecker.check(relative)).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("missing.html"));
    }

    @Test
    void theFootnoteAnchorsDitaOtWritesAreNotReportedAsBroken() throws Exception {
        // html5 output still writes footnote anchors with name and no id
        page("t.html", "<p><a name=\"fnsrc_1\" href=\"#fntarg_1\"><sup>1</sup></a></p>"
                + "<div class=\"fn\"><a name=\"fntarg_1\" href=\"#fnsrc_1\"><sup>1</sup></a> note</div>");

        assertThat(check()).isEmpty();
    }

    @Test
    void aNameOnSomethingThatIsNotAnAnchorIsNotAnAnchor() throws Exception {
        page("index.html", "<meta name=\"sect\"/><a href=\"#sect\">There</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("sect"));
    }

    @Test
    void linksInsideCommentsAndScriptsAreNotFollowed() throws Exception {
        page("index.html", "<!--[if lt IE 9]><script src=\"js/respond.min.js\"></script><![endif]-->"
                + "<script>var t = 'href=\"' + id + '.html\"'; el.src=\"data.json\";</script>"
                + "<style>.h{background:url(gone-bg.png)}</style>");

        assertThat(check()).isEmpty();
    }

    @Test
    void anIdInsideAScriptDoesNotExcuseABrokenFragment() throws Exception {
        page("index.html", "<script>var s = 'id=\"sect\"';</script><a href=\"#sect\">There</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("sect"));
    }

    @Test
    void blankingCommentsKeepsTheLineNumbersHonest() throws Exception {
        Files.writeString(dir.resolve("index.html"),
                "<html>\n<!-- a comment\nspanning lines\n-->\n<a href=\"missing.html\">Gone</a>\n</html>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.line()).isEqualTo(5));
    }

    @Test
    void anAttributeEndingInIdIsNotAnAnchor() throws Exception {
        page("index.html", "<div data-id=\"sect\"></div><a href=\"#sect\">There</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("sect"));
    }

    @Test
    void aPageThatMovesItsOwnLinksWithBaseIsResolvedThroughIt() throws Exception {
        page("topics/a.html", "<head><base href=\"../\"></head><body><a href=\"topics/b.html\">B</a></body>");
        page("topics/b.html", "<p>B</p>");

        assertThat(check()).isEmpty();
    }

    @Test
    void aPageWhoseBaseLeavesTheSiteIsLeftAlone() throws Exception {
        page("index.html", "<head><base href=\"https://example.com/\"></head>"
                + "<body><a href=\"anything.html\">Out</a></body>");

        assertThat(check()).isEmpty();
    }

    @Test
    void outputWrittenAsXhtmlIsRead() throws Exception {
        page("index.xhtml", "<a href=\"gone.xhtml\">Gone</a><a href=\"there.xhtml#s1\">S1</a>");
        page("there.xhtml", "<p id=\"s2\">Two</p>");

        assertThat(check()).hasSize(2)
                .anySatisfy(ref -> assertThat(ref.value()).isEqualTo("gone.xhtml"))
                .anySatisfy(ref -> assertThat(ref.reason()).contains("s1"));
    }

    @Test
    void aFolderLinkFindsWhicheverIndexTheBuildWrote() throws Exception {
        page("index.html", "<a href=\"guide/\">Guide</a>");
        page("guide/index.htm", "<p>Guide</p>");

        assertThat(check()).isEmpty();
    }

    @Test
    void eachCandidateInASrcsetIsChecked() throws Exception {
        page("index.html", "<img srcset=\"there.png 1x, gone-2x.png 2x\"/>");
        Files.writeString(dir.resolve("there.png"), "not really a png");

        assertThat(check()).singleElement()
                .satisfies(ref -> {
                    assertThat(ref.attribute()).isEqualTo("srcset");
                    assertThat(ref.value()).isEqualTo("gone-2x.png");
                });
    }

    @Test
    void aPlusInAFileNameIsAPlus() throws Exception {
        page("index.html", "<a href=\"a+b.html\">Plus</a>");
        page("a+b.html", "<p>Plus</p>");

        assertThat(check()).isEmpty();
    }

    @Test
    void aFileThatIsNotUtf8DoesNotSilenceTheRestOfTheCheck() throws Exception {
        Files.write(dir.resolve("latin.html"), new byte[] {(byte) 0xE9, 0x41});
        page("index.html", "<a href=\"missing.html\">Gone</a>");

        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.source()).endsWith("index.html"));
    }

    @Test
    void everyPageIsReportedAndInTheOrderTheyWereRead() throws Exception {
        page("a.html", "<a href=\"gone-a.html\">A</a>");
        page("b.html", "<a href=\"gone-b.html\">B</a>");

        assertThat(check()).extracting(BrokenRef::value)
                .containsExactly("gone-a.html", "gone-b.html");
    }

    @Test
    void aFragmentIsMatchedExactly() throws Exception {
        page("index.html", "<a href=\"topic.html#Sect\">There</a>");
        page("topic.html", "<p id=\"sect\">Here</p>");

        // HTML ids are case-sensitive, so this really is a link a reader would find broken
        assertThat(check()).singleElement()
                .satisfies(ref -> assertThat(ref.reason()).contains("Sect"));
    }

    @Test
    void anEmptyOutputFolderHasNothingToReport() throws Exception {
        assertThat(check()).isEmpty();
    }
}
