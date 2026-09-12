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
 * One governed attribute and its allowed values, as listed by {@code list_subjects}.
 *
 * @param attribute the governed profiling attribute (e.g. {@code platform})
 * @param values    the legal values (the bound subject subtree, flattened + sorted)
 */
public record SubjectDefinition(String attribute, List<String> values) {
    public SubjectDefinition {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
