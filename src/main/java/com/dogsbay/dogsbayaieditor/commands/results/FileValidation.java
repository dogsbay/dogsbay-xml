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
 * Per-file outcome of a project-wide validation, carried as a finding in a
 * {@code BatchResult}. Only failing files are typically retained as findings;
 * passing files are reflected in the batch's {@code passed} count.
 *
 * @param file   the validated file (absolute path)
 * @param valid  true when the file has no errors
 * @param errors the validation errors for this file (empty when valid)
 */
public record FileValidation(
    String file,
    boolean valid,
    List<ValidationError> errors
) {
    public FileValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
