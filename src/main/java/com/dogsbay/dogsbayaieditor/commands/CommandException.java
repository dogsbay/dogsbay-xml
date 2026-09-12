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

import java.util.Map;

/**
 * Typed exception for command execution failures.
 */
public class CommandException extends Exception {

    public enum ErrorCode {
        FILE_NOT_FOUND,
        PARSE_ERROR,
        VALIDATION_ERROR,
        SCHEMA_NOT_FOUND,
        EDITOR_NOT_RUNNING,
        DOCUMENT_NOT_OPEN,
        XPATH_ERROR,
        TRANSFORM_ERROR,
        PERMISSION_DENIED,
        INVALID_ARGUMENT,
        INTERNAL_ERROR,
        /** The document changed since the agent last read it; re-read and retry. */
        CONFLICT,
        /** Another agent session holds the write lease on the document. */
        LOCKED
    }

    private final ErrorCode code;

    public CommandException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CommandException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
