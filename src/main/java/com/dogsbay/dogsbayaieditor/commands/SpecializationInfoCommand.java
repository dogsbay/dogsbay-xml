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

import com.dogsbay.dogsbayaieditor.commands.results.SpecializationInfoResult;

/**
 * Report the DITA specialization of a single topic/map: its DOCTYPE, root element, the
 * {@code @class} generalization chain (general → specific), and the {@code @domains} it
 * integrates — surfacing that the editor understands a team's custom (or standard)
 * specialization.
 *
 * @param file the DITA file to inspect
 */
public record SpecializationInfoCommand(String file)
        implements Command<SpecializationInfoResult>, ReadOnlyCommand {}
