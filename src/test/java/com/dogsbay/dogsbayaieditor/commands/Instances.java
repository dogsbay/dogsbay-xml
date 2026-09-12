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

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Builds a command record from dummy component values, for structural tests. */
final class Instances {

    private Instances() {
    }

    static Command<?> dummy(Class<?> recordClass) {
        try {
            RecordComponent[] comps = recordClass.getRecordComponents();
            Class<?>[] types = new Class<?>[comps.length];
            Object[] args = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) {
                types[i] = comps[i].getType();
                args[i] = value(types[i]);
            }
            Constructor<?> ctor = recordClass.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return (Command<?>) ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build " + recordClass.getSimpleName(), e);
        }
    }

    private static Object value(Class<?> t) {
        if (t == String.class) return "x";
        if (t == Path.class) return Path.of("x");
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == int.class || t == Integer.class) return 0;
        if (t == List.class) return List.of();
        if (t == Map.class) return Map.of();
        return null;
    }
}
