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

import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * Deep-validation result for one DITA-project deliverable: the diagnostics
 * DITA-OT's {@code validate} transtype produced for that deliverable's map
 * (optionally DITAVAL-filtered). Unlike the static {@link DeliverableValidation},
 * this exercises the real preprocessing pipeline.
 *
 * @param name     the deliverable name
 * @param map      the deliverable's root map (absolute path)
 * @param ditaval  the DITAVAL filter applied, or null if none
 * @param success  true when no ERROR/FATAL diagnostics were produced
 * @param messages the captured diagnostics (errors and warnings), in log order
 */
public record DitaOtValidation(
    String name,
    String map,
    String ditaval,
    boolean success,
    List<DitaOtMessage> messages
) {}
