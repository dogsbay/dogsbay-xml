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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * <p>DEBUG and TRACE are dropped, and so is most of INFO: DITA-OT narrates every step at that level. The
 * exception is an INFO message carrying one of its own codes — {@code DOTJ047I}, "the file is not included in
 * any map" — which is a diagnostic in everything but severity, is documented and searchable by that code, and
 * is the one a reader is sent looking for. Those are kept, and whether to show them is the caller's business.
 *
 * <p>FATAL has no SLF4J level; DITA-OT logs it via {@code error()} with a {@code [FATAL]} tag in the text,
 * which {@link DitaOtMessage#parse} recovers.
 *
 * <p>A message identical to one already captured — same severity, code, location and words — is kept once.
 * Two files with the same problem differ by location and are both reported; the same problem reported twice
 * about the same line is the toolkit repeating itself.
 */
public final class CollectingDitaOtLogger extends AbstractLogger {

    private static final long serialVersionUID = 1L;

    private final List<DitaOtMessage> messages = new ArrayList<>();
    /** What has already been said, so saying it again adds nothing. */
    private final Set<DitaOtMessage> seen = new HashSet<>();

    /** The diagnostics captured so far, in log order. */
    public List<DitaOtMessage> messages() {
        return messages;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker,
            String messagePattern, Object[] arguments, Throwable throwable) {
        if (level != Level.WARN && level != Level.ERROR && level != Level.INFO) {
            return;
        }
        String text = MessageFormatter.basicArrayFormat(messagePattern, arguments);
        if (throwable != null && (text == null || text.isBlank())) {
            text = throwable.getMessage();
        }
        if (level == Level.INFO && !carriesACode(text)) {
            // Narration, not a diagnostic — and turned away before parsing it. A publish logs thousands of
            // these, and matching three regular expressions against each one to discard it is a cost paid on
            // every build for nothing.
            return;
        }
        DitaOtMessage message = DitaOtMessage.parse(level, text);
        // Exact repeats carry nothing: DITA-OT reports "No rule for 'audience=podcaster' was found in the
        // DITAVAL file" once per element it filtered, and a reader looking for the one DOTJ047I line has to
        // scan past them (reported 2026-09-29). Same code, same file, same line, same words — said once.
        if (seen.add(message)) {
            messages.add(message);
        }
    }

    /** Whether the text has a DITA-OT code in it ({@code [DOTJ047I]}), cheaply enough to ask of every line. */
    private static boolean carriesACode(String text) {
        if (text == null || text.length() < 10) {
            return false;
        }
        int at = text.indexOf("[DOT");
        return at >= 0 && text.indexOf(']', at) > at;
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
    // On, or a caller that asks before logging — DITA-OT's own LoggerListener does — never offers the INFO
    // messages, and the coded ones would be filtered by a switch that had already been read as "no".
    @Override public boolean isInfoEnabled() { return true; }
    @Override public boolean isInfoEnabled(Marker marker) { return true; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isWarnEnabled(Marker marker) { return true; }
    @Override public boolean isErrorEnabled() { return true; }
    @Override public boolean isErrorEnabled(Marker marker) { return true; }
}
