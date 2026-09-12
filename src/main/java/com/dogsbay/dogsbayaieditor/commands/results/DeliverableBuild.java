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
 * Build result for one DITA-project deliverable: where it was built, its
 * transtype, whether it succeeded, and any diagnostics produced.
 *
 * @param name      the deliverable name
 * @param transtype the output type built (e.g. {@code html5})
 * @param outputDir the directory the output was written to (absolute)
 * @param success   true when DITA-OT produced no ERROR/FATAL diagnostics
 * @param messages  the captured diagnostics (errors and warnings), in log order
 */
public record DeliverableBuild(
    String name,
    String transtype,
    String outputDir,
    boolean success,
    List<DitaOtMessage> messages
) {}
