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

package com.dogsbay.dogsbayaieditor.links.scheme;

import java.util.Set;

/**
 * One {@code <enumerationdef>} from a subjectScheme: binds a profiling attribute
 * (e.g. {@code platform}) to a subject subtree, whose flattened keys are the legal
 * values for that attribute.
 *
 * @param attribute     the bound attribute name (from {@code <attributedef name=…>})
 * @param subjectKeyref the {@code keyref} of the bound subject, or null if inline
 * @param allowedValues the legal values (the bound subject's key subtree, flattened)
 */
public record EnumerationBinding(String attribute, String subjectKeyref, Set<String> allowedValues) {

    public EnumerationBinding {
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
    }
}
