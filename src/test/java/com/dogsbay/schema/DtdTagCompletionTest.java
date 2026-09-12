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

package com.dogsbay.schema;

import com.dogsbay.schema.dtd.DTDDocument;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tag prompting must keep working from a DTD alone.
 *
 * <p>XML Schema support and its Castor-backed model were removed; DTD and RelaxNG are the
 * remaining grammars. Completion consumes {@link SchemaDocument#getElements()} →
 * {@link ElementInformation} without knowing which grammar produced it, so these tests pin
 * down that the DTD implementation really does supply that model.
 */
public class DtdTagCompletionTest {

    private DTDDocument load() throws Exception {
        File dtd = new File("src/test/resources/commands/test.dtd");
        assertTrue(dtd.exists(), "test fixture DTD should exist");
        return new DTDDocument(dtd.toURI().toURL());
    }

    @Test
    void aDtdIsASchemaDocument() throws Exception {
        SchemaDocument doc = load();

        assertEquals(SchemaDocument.TYPE_DTD, doc.getType(),
                "a DTD must report itself as a DTD grammar");
    }

    @Test
    void elementsAreExposedForCompletion() throws Exception {
        SchemaDocument doc = load();

        Vector elements = doc.getElements();
        assertNotNull(elements);
        assertFalse(elements.isEmpty(), "the DTD's elements must be available to complete against");

        boolean sawRoot = false;
        for (Object o : elements) {
            assertInstanceOf(ElementInformation.class, o,
                    "completion consumes ElementInformation, whatever the grammar");
            if ("root".equals(((ElementInformation) o).getName())) {
                sawRoot = true;
            }
        }
        assertTrue(sawRoot, "expected the declared 'root' element");
    }

    @Test
    void childElementsDriveContextSensitivePrompting() throws Exception {
        SchemaDocument doc = load();

        ElementInformation root = null;
        for (Object o : doc.getElements()) {
            if ("root".equals(((ElementInformation) o).getName())) {
                root = (ElementInformation) o;
            }
        }
        assertNotNull(root);

        Vector children = root.getChildElements();
        assertNotNull(children, "children drive what is offered inside an element");
        assertFalse(children.isEmpty(), "<!ELEMENT root (title, item+)> declares children");
    }

    @Test
    void globalElementsAreAvailable() throws Exception {
        SchemaDocument doc = load();

        assertNotNull(doc.getGlobalElements());
    }
}
