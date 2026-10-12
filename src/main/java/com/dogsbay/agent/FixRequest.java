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

package com.dogsbay.agent;

/**
 * A request to fix one validation problem with the agent
 * (plans/agent-quickfix-on-markers.md). Built from a marker the editor already
 * computed (file + line + message, and optionally what the schema expects) and
 * turned into a focused, single-error prompt so the agent fixes exactly that
 * problem and re-validates.
 *
 * @param file       the file containing the error (path or system id), or null
 * @param line       the 1-based line, or &lt;= 0 if unknown
 * @param message    the validation message
 * @param schemaHint what the grammar allows/expects at that point, or null
 */
public record FixRequest(String file, int line, String message, String schemaHint) {

    /** A focused instruction telling the agent to fix only this error and verify. */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder("Fix this validation error and nothing else.\n");
        if (file != null && !file.isBlank()) {
            sb.append("File: ").append(file).append('\n');
        }
        if (line > 0) {
            sb.append("Line: ").append(line).append('\n');
        }
        sb.append("Error: ").append(message == null ? "(no message)" : message).append('\n');
        if (schemaHint != null && !schemaHint.isBlank()) {
            sb.append("Allowed/expected here: ").append(schemaHint).append('\n');
        }
        sb.append("Make the minimal change that resolves it, then validate to confirm "
                + "the error is gone. If fixing it cleanly is ambiguous, explain the options "
                + "instead of guessing.");
        return sb.toString();
    }
}
