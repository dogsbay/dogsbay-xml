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

package com.dogsbay.dogsbayaieditor.services.events;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * Fired when the workspace's <em>active deliverable</em> changes (the user picks a
 * different map · profile in the status bar, or a project (re)loads). Consumers —
 * deep validate, conditional preview, publish, the status-bar segment — react to
 * this single source of truth rather than tracking map/ditaval independently.
 *
 * @param deliverable the new active deliverable, or null when none (no DITA
 *                    project in the workspace)
 */
public record DeliverableChangedEvent(Deliverable deliverable) {}
