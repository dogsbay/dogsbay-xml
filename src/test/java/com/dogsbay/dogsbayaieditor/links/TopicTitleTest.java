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

package com.dogsbay.dogsbayaieditor.links;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a link to a topic should say, when the map does not say it.
 *
 * <p>{@link TopicTitle#forHref} is what stands between a reader and a link labelled with something that is not
 * the thing it points at. Most of these are about refusing to answer.
 */
class TopicTitleTest {

    @TempDir Path dir;

    /** {@link TopicTitle#read} for the tests whose titles use no keys. */
    private static String read(File topic) {
        return TopicTitle.read(topic, PRODUCT);
    }

    private File topic(String content) throws Exception {
        Path file = dir.resolve("t.dita");
        Files.writeString(file, content);
        return file.toFile();
    }

    // ── The title itself ────────────────────────────────────────────────

    @Test
    void readsTheTitle() throws Exception {
        assertThat(read(topic("<task id=\"install\"><title>Installing Audacity</title></task>")))
                .isEqualTo("Installing Audacity");
    }

    @Test
    void flattensMarkupInsideTheTitle() throws Exception {
        assertThat(read(topic(
                "<task id=\"i\"><title>Installing <keyword>Audacity</keyword></title></task>")))
                .isEqualTo("Installing Audacity");
    }

    @Test
    void resolvesNumericCharacterReferences() throws Exception {
        // The build shows an em dash; printing "&#8212;" in a link shows the reader the source
        assertThat(read(topic(
                "<task id=\"i\"><title>Audacity &#8212; getting started</title></task>")))
                .isEqualTo("Audacity — getting started");
        assertThat(read(topic(
                "<task id=\"i\"><title>Audacity &#x2014; getting started</title></task>")))
                .isEqualTo("Audacity — getting started");
    }

    @Test
    void anEscapedAmpersandDoesNotBecomeACharacterReference() throws Exception {
        assertThat(read(topic("<task id=\"i\"><title>Write &amp;#8212; literally</title></task>")))
                .isEqualTo("Write &#8212; literally");
    }

    @Test
    void aCommentedOutTitleIsNotTheTitle() throws Exception {
        assertThat(read(topic(
                "<task id=\"i\"><!-- <title>Old</title> --><title>New</title></task>")))
                .isEqualTo("New");
    }

    @Test
    void aFileWithNoTitleHasNone() throws Exception {
        assertThat(read(topic("<task id=\"i\"><taskbody/></task>"))).isNull();
        assertThat(read(new File(dir.toFile(), "absent.dita"))).isNull();
        assertThat(read(null)).isNull();
    }

    // ── Which titles a link may use ─────────────────────────────────────

    @Test
    void anHrefWithNoFragmentUsesTheTitle() throws Exception {
        File file = topic("<task id=\"install\"><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.forHref(file, "topics/t.dita", PRODUCT)).isEqualTo("Installing Audacity");
        assertThat(TopicTitle.forHref(file, null, PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aFragmentNamingTheRootTopicUsesTheTitle() throws Exception {
        File file = topic("<task id=\"install\"><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install", PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void theRootIdIsReadWhicheverQuotesItUses() throws Exception {
        // Single quotes are legal XML and common in generated DITA; the feature must not quietly switch off
        File file = topic("<task id='install'><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install", PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aFragmentAddressingAnElementInsideTheTopicGetsNothing() throws Exception {
        File file = topic("<task id=\"install\"><title>Installing Audacity</title>"
                + "<taskbody><steps><step id=\"step-3\"><cmd>Run it.</cmd></step></steps></taskbody></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install/step-3", PRODUCT)).isNull();
    }

    @Test
    void aFragmentThatMatchesAnIdDeeperInTheFileGetsNothing() throws Exception {
        // The root has no id of its own; without anchoring to the root's start tag the first id anywhere in
        // the file answered, and a link to a paragraph came back labelled with the whole topic's title.
        File file = topic("<task><title>Installing Audacity</title>"
                + "<taskbody><p id=\"intro\">Start here.</p></taskbody></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#intro", PRODUCT)).isNull();
    }

    @Test
    void anIdInACommentDoesNotStandInForTheRootsOwn() throws Exception {
        File file = topic("<!-- was <task id=\"old\"> -->\n"
                + "<task id=\"install\"><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install", PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aDoctypeWithAnInternalSubsetDoesNotHideTheRoot() throws Exception {
        // The subset holds its own '>' characters, so the prolog has to be skipped by its brackets
        File file = topic("<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE task PUBLIC \"-//OASIS//DTD DITA Task//EN\" \"task.dtd\" [\n"
                + "  <!ENTITY prodname \"Audacity\">\n"
                + "]>\n"
                + "<task id=\"install\"><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install", PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aTitleWithAnEntityTheDtdWouldResolveIsRefusedForALink() throws Exception {
        // The build prints "Installing Audacity"; a link reading "Installing &prodname;" shows the reader the
        // source. The key's own name is the better of the two, so nothing is offered here.
        File file = topic("<task id=\"install\"><title>Installing &prodname;</title></task>");

        assertThat(TopicTitle.forHref(file, "t.dita#install", PRODUCT)).isNull();
        assertThat(read(file))
                .as("an Explorer row still shows what it can")
                .isEqualTo("Installing &prodname;");
    }

    // ── A title written with a key ──────────────────────────────────────

    /** The stage 10 shape: the product name is a key, so the title cannot be read without resolving it. */
    private static final TopicTitle.KeyText PRODUCT = key -> "product-name".equals(key) ? "Audacity" : null;

    @Test
    void aTitleWrittenWithAKeyResolvesIt() throws Exception {
        File file = topic("<task id=\"install\">"
                + "<title>Installing <keyword keyref=\"product-name\"/></title></task>");

        assertThat(TopicTitle.read(file, PRODUCT)).isEqualTo("Installing Audacity");
        assertThat(TopicTitle.forHref(file, "t.dita", PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aKeyInTheMiddleOfATitleResolvesToo() throws Exception {
        // Dropping the element left "What is ?", which is not the title of anything
        File file = topic("<concept id=\"what\">"
                + "<title>What is <keyword keyref=\"product-name\"/>?</title></concept>");

        assertThat(TopicTitle.read(file, PRODUCT)).isEqualTo("What is Audacity?");
    }

    @Test
    void aKeyWrittenWithASeparateEndTagResolves() throws Exception {
        File file = topic("<task id=\"install\">"
                + "<title>Installing <keyword keyref=\"product-name\"></keyword></title></task>");

        assertThat(TopicTitle.read(file, PRODUCT)).isEqualTo("Installing Audacity");
    }

    @Test
    void aKeyrefElementWithItsOwnTextKeepsThatText() throws Exception {
        // DITA says the element's content wins over the key, and flattening the markup already leaves it
        File file = topic("<task id=\"install\">"
                + "<title>Installing <keyword keyref=\"product-name\">Audacity 3</keyword></title></task>");

        assertThat(TopicTitle.read(file, key -> "Audacity")).isEqualTo("Installing Audacity 3");
    }

    @Test
    void aTitleWhoseKeyDoesNotResolveIsNoTitle() throws Exception {
        // Better an honest fallback — the file name in the Explorer, the key's name in a link — than a title
        // with a hole in it.
        File file = topic("<task id=\"install\">"
                + "<title>Installing <keyword keyref=\"no-such-key\"/></title></task>");

        assertThat(TopicTitle.read(file, PRODUCT)).isNull();
        assertThat(TopicTitle.forHref(file, "t.dita", PRODUCT)).isNull();
        assertThat(TopicTitle.read(file, TopicTitle.KeyText.NONE)).isNull();
    }

    @Test
    void aTitleWithNoKeysDoesNotNeedAResolver() throws Exception {
        File file = topic("<task id=\"install\"><title>Installing Audacity</title></task>");

        assertThat(TopicTitle.read(file, TopicTitle.KeyText.NONE)).isEqualTo("Installing Audacity");
    }
}
