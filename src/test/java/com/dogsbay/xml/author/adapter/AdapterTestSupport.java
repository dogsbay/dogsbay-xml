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

package com.dogsbay.xml.author.adapter;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public final class AdapterTestSupport {

    private AdapterTestSupport() {
    }

    public static Element parse(String xml) {
        try {
            return reader().read(new StringReader(xml)).getRootElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("bad test xml: " + e.getMessage(), e);
        }
    }

    public static Document parseResource(String path) {
        try (var in = AdapterTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("missing test resource: " + path);
            }
            return reader().read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("bad test resource " + path + ": " + e.getMessage(), e);
        }
    }

    private static SAXReader reader() throws Exception {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        // corpus topics carry DOCTYPEs pointing at catalog-resolved DTDs we don't fetch
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setIncludeExternalDTDDeclarations(false);
        return reader;
    }
}
