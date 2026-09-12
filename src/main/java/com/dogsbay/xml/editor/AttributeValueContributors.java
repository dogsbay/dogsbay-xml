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

package com.dogsbay.xml.editor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extension point for attribute-value autocompletion. Plugins register a
 * {@link ValueProvider}; when the editor computes the completion candidates for an
 * attribute value, it merges in the providers' values (e.g. a DITA subjectScheme's
 * controlled values) alongside the grammar/schema-provided ones.
 *
 * <p>A static registry rather than a service because the editor component
 * ({@code xml.editor}) cannot depend on the plugin API. Plugins register through
 * {@code UIService.addAttributeValueProvider} rather than directly.
 */
public final class AttributeValueContributors {

    /** Contributes legal values for an attribute on an element. */
    public interface ValueProvider {
        /**
         * @param elementName     the (local) name of the element being edited, or null
         * @param attributeName   the (local) name of the attribute being edited
         * @param file            the document file (for project/scheme resolution), or null
         * @param docTypePublicId the document's DOCTYPE public id (e.g.
         *                        {@code -//OASIS//DTD DITA Topic//EN}), or null — the
         *                        authoritative signal of the document's vocabulary,
         *                        so a provider need not guess from the extension
         * @return additional legal values (possibly empty, never null)
         */
        Set<String> contribute(String elementName, String attributeName, File file,
                String docTypePublicId);
    }

    private static final CopyOnWriteArrayList<ValueProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private AttributeValueContributors() {
    }

    public static void register(ValueProvider provider) {
        if (provider != null) {
            PROVIDERS.addIfAbsent(provider);
        }
    }

    public static void unregister(ValueProvider provider) {
        PROVIDERS.remove(provider);
    }

    /**
     * The union of every provider's values for this attribute, in registration
     * order. A failing provider is isolated — the others still contribute.
     */
    public static Set<String> collect(String elementName, String attributeName, File file,
            String docTypePublicId) {
        Set<String> values = new LinkedHashSet<>();
        if (attributeName == null) {
            return values;
        }
        for (ValueProvider provider : PROVIDERS) {
            try {
                Set<String> contributed = provider.contribute(
                        elementName, attributeName, file, docTypePublicId);
                if (contributed != null) {
                    values.addAll(contributed);
                }
            } catch (Exception e) {
                // one broken provider must not break completion
            }
        }
        return values;
    }

    /** Test hook. */
    static void clear() {
        PROVIDERS.clear();
    }
}
