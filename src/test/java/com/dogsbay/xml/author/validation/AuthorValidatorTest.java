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

package com.dogsbay.xml.author.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.model.AuthorDocument;

class AuthorValidatorTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();
    private final AuthorValidator validator = new AuthorValidator();

    private List<ValidationIssue> validate(String xml) {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(xml));
        return validator.validate(doc);
    }

    @Test
    void validTopicHasNoIssues() {
        assertThat(validate("""
                <task id="t"><title>Good</title><taskbody>
                <steps><step><cmd>Do it.</cmd></step></steps>
                </taskbody></task>""")).isEmpty();
    }

    @Test
    void duplicateIdsAreErrors() {
        var issues = validate("<concept id='c'><title>T</title><conbody><p id='x'>a</p><p id='x'>b</p></conbody></concept>");
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.isError()).isTrue();
            assertThat(i.message()).startsWith("Duplicate id \"x\"");
            assertThat(i.block().getPlainText()).isEqualTo("b");
        });
    }

    @Test
    void definitionEntriesNeedTermAndDescription() {
        var issues = validate("<concept id='c'><title>T</title><conbody><dl><dlentry><dd>only</dd></dlentry>"
                + "<dlentry><dt>term</dt></dlentry></dl></conbody></concept>");
        assertThat(issues).extracting(ValidationIssue::message)
                .containsExactly("Definition entry has no term", "Definition entry has no description");
        assertThat(issues.get(0).isError()).isTrue();
        assertThat(issues.get(1).isError()).isFalse();
    }

    @Test
    void aLinkWithoutATargetIsAWarning() {
        var issues = validate("<concept id='c'><title>T</title><conbody><p>x</p></conbody>"
                + "<related-links><link/><link keyref='k'/></related-links></concept>");
        assertThat(issues).extracting(ValidationIssue::message).containsExactly("Link has no target");
    }

    @Test
    void calsWidthsCountSpansAndColumnDefinitions() {
        var issues = validate("""
            <reference id="r"><title>T</title><refbody><table><tgroup cols="3">
              <colspec colname="c1"/><colspec colname="c2"/>
              <tbody>
                <row><entry namest="c1" nameend="c2">wide</entry><entry morerows="1">tall</entry></row>
                <row><entry>a</entry><entry>b</entry></row>
                <row><entry>a</entry><entry>b</entry></row>
              </tbody></tgroup></table></refbody></reference>""");
        assertThat(issues).extracting(ValidationIssue::message).containsExactly(
                "Table declares 3 columns but defines 2",
                "Row covers 2 columns; the table is 3 wide");
        assertThat(issues.get(1).block().getChildren().get(0).getPlainText()).as("the third row, after the span ran out")
                .isEqualTo("a");
    }

    @Test
    void missingTitleIsAnError() {
        assertThat(validate("<concept id='c'><conbody><p>x</p></conbody></concept>"))
                .anyMatch(i -> i.isError() && i.message().contains("no title"));
    }

    @Test
    void emptyTitleIsAnError() {
        assertThat(validate("<concept id='c'><title> </title><conbody><p>x</p></conbody></concept>"))
                .anyMatch(i -> i.isError() && i.message().contains("Title is empty"));
    }

    @Test
    void stepWithoutCmdIsAnErrorAndEmptyCmdAWarning() {
        var issues = validate("""
                <task id="t"><title>T</title><taskbody><steps>
                <step><info>only info</info></step>
                <step><cmd></cmd></step>
                </steps></taskbody></task>""");
        assertThat(issues).anyMatch(i -> i.isError() && i.message().contains("no command"));
        assertThat(issues).anyMatch(i -> !i.isError() && i.message().contains("Command is empty"));
    }

    @Test
    void conrefStepWithEmptyCmdIsNotFlagged() {
        // common real-world pattern: <step conref="..."><cmd/></step>
        var issues = validate("""
                <task id="t"><title>T</title><taskbody><steps>
                <step conref="shared.dita#s/x"><cmd/></step>
                </steps></taskbody></task>""");
        assertThat(issues).noneMatch(i -> i.message().contains("Command is empty"));
    }

    @Test
    void emptyListsAndItemsWarn() {
        var issues = validate("""
                <concept id="c"><title>T</title><conbody>
                <ul><li></li></ul>
                </conbody></concept>""");
        assertThat(issues).anyMatch(i -> i.message().contains("List item is empty"));
    }

    @Test
    void imageWithoutHrefWarnsButKeyrefIsFine() {
        var issues = validate("""
                <concept id="c"><title>T</title><conbody>
                <fig><title>F</title><image/></fig>
                <fig><title>G</title><image keyref="img-key"/></fig>
                </conbody></concept>""");
        assertThat(issues).filteredOn(i -> i.message().contains("Image has no href")).hasSize(1);
    }

    @Test
    void keyrefOnlyTitleCountsAsContent() {
        assertThat(validate("""
                <topic id="t"><title><keyword keyref="product-name"/></title>
                <body><p>x</p></body></topic>"""))
                .noneMatch(i -> i.message().contains("Title is empty"));
    }

    @Test
    void glossaryEntryNeedsATerm() {
        assertThat(validate("<glossentry id='g'><glossdef>x</glossdef></glossentry>"))
                .anyMatch(i -> i.isError() && i.message().contains("no term"));
        assertThat(validate("<glossentry id='g'><glossterm> </glossterm><glossdef>x</glossdef></glossentry>"))
                .anyMatch(i -> i.isError() && i.message().contains("term is empty"));
        assertThat(validate("<glossentry id='g'><glossterm>Waveform</glossterm><glossdef>x</glossdef></glossentry>"))
                .isEmpty();
    }

    @Test
    void mismatchedRowWidthsWarn() {
        var issues = validate("""
                <concept id="c"><title>T</title><conbody>
                <simpletable>
                  <sthead><stentry>A</stentry><stentry>B</stentry></sthead>
                  <strow><stentry>1</stentry></strow>
                </simpletable>
                </conbody></concept>""");
        assertThat(issues).anyMatch(i -> !i.isError() && i.message().contains("cells"));

        var calsIssues = validate("""
                <reference id="r"><title>T</title><refbody><section>
                <table><tgroup cols="2">
                  <tbody>
                    <row><entry>a</entry><entry>b</entry></row>
                    <row><entry>only</entry></row>
                  </tbody>
                </tgroup></table>
                </section></refbody></reference>""");
        assertThat(calsIssues).anyMatch(i -> i.message().contains("Row covers 1 columns"));
    }

    @Test
    void emptyTablesWarn() {
        assertThat(validate("""
                <concept id="c"><title>T</title><conbody>
                <simpletable><sthead><stentry>H</stentry></sthead></simpletable>
                </conbody></concept>"""))
                .anyMatch(i -> i.message().contains("no rows"));
    }

    @Test
    void corpusTopicsHaveNoErrors() {
        for (String name : new String[] {"recording-your-first-track", "what-is-audacity",
                "installing-audacity", "exporting-audio"}) {
            AuthorDocument doc = adapter.importDocument(
                    AdapterTestSupport.parseResource("/author/corpus/" + name + ".dita").getRootElement());
            assertThat(validator.validate(doc))
                    .as("errors in %s", name)
                    .noneMatch(ValidationIssue::isError);
        }
    }
}
