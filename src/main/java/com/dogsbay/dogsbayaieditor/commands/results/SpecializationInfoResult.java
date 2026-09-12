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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.List;

/**
 * The DITA specialization of a file.
 *
 * @param file        the inspected file
 * @param root        root element local name
 * @param publicId    DOCTYPE public id, or null
 * @param systemId    DOCTYPE system id (the DTD), or null
 * @param classChain  {@code @class} module/type tokens, general → specific
 * @param domains     {@code @domains} module ids the doctype integrates
 * @param specialized true when the root is a specialization (class chain &gt; 1)
 */
public record SpecializationInfoResult(String file, String root, String publicId,
        String systemId, List<String> classChain, List<String> domains, boolean specialized) {

    public SpecializationInfoResult {
        classChain = classChain == null ? List.of() : List.copyOf(classChain);
        domains = domains == null ? List.of() : List.copyOf(domains);
    }
}
