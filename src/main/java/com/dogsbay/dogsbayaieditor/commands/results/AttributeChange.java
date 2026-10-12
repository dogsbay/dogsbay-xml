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
 * One attribute {@code set_attribute} set, or would set in a dry run, carried as a finding in the
 * {@code BatchResult}.
 *
 * @param file     the file changed (absolute path)
 * @param element  the selector the attribute was set on
 * @param name     the attribute
 * @param value    the value set
 * @param replaced true when the element already had the attribute, so a plan says what it is overwriting
 */
public record AttributeChange(String file, String element, String name, String value, boolean replaced) {}
