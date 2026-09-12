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

package com.dogsbay.dogsbayaieditor.links.scheme;

/**
 * One controlled-value violation: a profiling attribute carries a value that the
 * governing subjectScheme doesn't allow. Value-level (no file/line) — the scanning
 * command attaches the source location.
 *
 * @param attribute  the governed attribute (e.g. {@code platform})
 * @param value      the offending token (e.g. {@code macos})
 * @param message    a human-readable description
 * @param suggestion the nearest allowed value (e.g. {@code mac}), or null if none close
 */
public record ConditionFinding(String attribute, String value, String message, String suggestion) {
}
