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

package com.dogsbay.xml.dita;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;

/**
 * An SLF4J {@link org.slf4j.Logger} that captures DITA-OT's WARN/ERROR messages
 * into a list instead of printing them. Attached to a DITA-OT {@code Processor}
 * via {@code setLogger(...)} so a validate/build run yields structured
 * {@link DitaOtMessage}s (DITA-OT's {@code DITAOTLogger} is just an
 * {@code org.slf4j.Logger}).
 *
 * <p>INFO/DEBUG/TRACE are dropped — DITA-OT is chatty at those levels and they
 * carry no diagnostics. FATAL has no SLF4J level; DITA-OT logs it via
 * {@code error()} with a {@code [FATAL]} tag in the text, which
 * {@link DitaOtMessage#parse} recovers.
 */
public final class CollectingDitaOtLogger extends AbstractLogger {

    private static final long serialVersionUID = 1L;

    private final List<DitaOtMessage> messages = new ArrayList<>();

    /** The diagnostics captured so far, in log order. */
    public List<DitaOtMessage> messages() {
        return messages;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker,
            String messagePattern, Object[] arguments, Throwable throwable) {
        if (level != Level.WARN && level != Level.ERROR) {
            return;
        }
        String text = MessageFormatter.basicArrayFormat(messagePattern, arguments);
        if (throwable != null && (text == null || text.isBlank())) {
            text = throwable.getMessage();
        }
        messages.add(DitaOtMessage.parse(level, text));
    }

    @Override
    public String getName() {
        return "dogsbay.dita-ot.collecting";
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return CollectingDitaOtLogger.class.getName();
    }

    // DITA-OT checks these before emitting; keep WARN/ERROR on (and the chatty
    // lower levels off so the engine doesn't spend time formatting dropped lines).
    @Override public boolean isTraceEnabled() { return false; }
    @Override public boolean isTraceEnabled(Marker marker) { return false; }
    @Override public boolean isDebugEnabled() { return false; }
    @Override public boolean isDebugEnabled(Marker marker) { return false; }
    @Override public boolean isInfoEnabled() { return false; }
    @Override public boolean isInfoEnabled(Marker marker) { return false; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isWarnEnabled(Marker marker) { return true; }
    @Override public boolean isErrorEnabled() { return true; }
    @Override public boolean isErrorEnabled(Marker marker) { return true; }
}
