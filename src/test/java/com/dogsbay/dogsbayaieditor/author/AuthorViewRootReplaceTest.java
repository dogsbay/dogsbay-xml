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

package com.dogsbay.dogsbayaieditor.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;

class AuthorViewRootReplaceTest {

    @Test
    void replaceRootPreservesDocLevelNodesAndOrder() throws Exception {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        Document doc = reader.read(new StringReader(
                "<?xml version=\"1.0\"?>"
                        + "<?xml-model href=\"s.rnc\"?>"
                        + "<!-- header comment -->"
                        + "<concept id=\"old\"><title>Old</title></concept>"
                        + "<!-- trailing comment -->"));

        Element newRoot = DocumentFactory.getInstance().createElement("concept");
        newRoot.addAttribute("id", "new");
        newRoot.addElement("title").setText("New");

        AuthorView.replaceRoot(doc, newRoot);

        assertThat(doc.getRootElement()).isSameAs(newRoot);
        String xml = doc.asXML();
        int pi = xml.indexOf("xml-model");
        int header = xml.indexOf("header comment");
        int root = xml.indexOf("<concept id=\"new\">");
        int trailing = xml.indexOf("trailing comment");
        assertThat(pi).isLessThan(header);
        assertThat(header).isLessThan(root);
        assertThat(root).isLessThan(trailing);
        assertThat(xml).doesNotContain("Old");
    }
}
