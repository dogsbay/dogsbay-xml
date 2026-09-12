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

package com.dogsbay.dogsbayaieditor.commands;

/**
 * One requested metadata change for {@code metadata_set}: a field, a value, and a
 * mode ({@code set} overwrite, {@code fill} only-if-absent, {@code append} add to a
 * list, {@code remove}). The {@code field} is a metadata element name (e.g.
 * {@code audience}, {@code created}); name/content fields take {@code "name=value"}.
 */
public record MetadataSetSpec(String field, String value, String mode) {}
