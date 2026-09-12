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

import com.dogsbay.schema.AttributeValue;

/**
 * An attribute value contributed by a controlled-value source (a DITA
 * subjectScheme) rather than the grammar — a marker subtype so the completion
 * popup can render it distinctly from schema/DTD-provided values.
 */
public class SchemeAttributeValue extends AttributeValue {

    public SchemeAttributeValue(String value) {
        super(value, AttributeValue.NORMAL_TYPE);
    }
}
