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
 * Generic success/failure result for commands that don't return structured data.
 */
public record CommandResult(
    boolean success,
    String message
) {
    public static CommandResult ok() {
        return new CommandResult(true, "OK");
    }

    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult fail(String message) {
        return new CommandResult(false, message);
    }
}
