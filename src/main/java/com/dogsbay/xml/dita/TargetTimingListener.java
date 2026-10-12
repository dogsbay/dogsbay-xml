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
import java.util.function.Consumer;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;

/**
 * Records when each Ant target of a DITA-OT build starts and finishes. Ant runs the
 * targets one after another (dependencies first), so each target's time is its own.
 */
public final class TargetTimingListener implements BuildListener {

    /** One target's run: its name and how long it took. */
    public record TargetTime(String name, long millis) {}

    private final List<TargetTime> times = new ArrayList<>();
    private final Consumer<String> onTargetStarted;
    private String current;
    private long started;

    /** @param onTargetStarted called with each target's name as it starts (may be null) */
    public TargetTimingListener(Consumer<String> onTargetStarted) {
        this.onTargetStarted = onTargetStarted;
    }

    /** The targets that ran, in order. */
    public List<TargetTime> times() {
        return List.copyOf(times);
    }

    @Override
    public void targetStarted(BuildEvent event) {
        current = event.getTarget().getName();
        started = System.nanoTime();
        if (onTargetStarted != null) {
            onTargetStarted.accept(current);
        }
    }

    @Override
    public void targetFinished(BuildEvent event) {
        String name = event.getTarget().getName();
        if (name.equals(current)) {
            times.add(new TargetTime(name, (System.nanoTime() - started) / 1_000_000));
            current = null;
        }
    }

    @Override
    public void buildStarted(BuildEvent event) {}

    @Override
    public void buildFinished(BuildEvent event) {}

    @Override
    public void taskStarted(BuildEvent event) {}

    @Override
    public void taskFinished(BuildEvent event) {}

    @Override
    public void messageLogged(BuildEvent event) {}
}
