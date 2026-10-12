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

/**
 * One reference found by where-used.
 *
 * @param source    absolute path of the referencing file
 * @param line      1-based line of the referencing element
 * @param element   referencing element name
 * @param attribute referencing attribute (href, conref, keyref, ...)
 * @param value     the attribute value as written
 * @param category  semantic category (In maps / Content reuse / Links / Images / Other)
 * @param viaKey    key name when the reference is key-mediated, else null
 */
public record ReferenceInfo(
    String source,
    int line,
    String element,
    String attribute,
    String value,
    String category,
    String viaKey
) {}
