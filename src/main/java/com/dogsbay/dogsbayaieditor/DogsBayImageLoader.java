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

package com.dogsbay.dogsbayaieditor;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.ImageIcon;

/**
 * Loads and caches {@link ImageIcon}s by classpath resource name or URL. Each
 * unique key is loaded once; subsequent lookups return the cached instance.
 *
 * <p>Resource-name lookups resolve against the editor's extension class loader
 * (see {@link DogsBayAIEditor#getStaticExtensionClassLoader()}), so plugin and
 * application icons are both reachable.
 */
public final class DogsBayImageLoader {

    /** Lazy holder — initialised on first call to {@link #get()}, thread-safe by JLS. */
    private static final class Holder {
        static final DogsBayImageLoader INSTANCE = new DogsBayImageLoader();
    }

    private final ClassLoader classLoader;
    private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

    public DogsBayImageLoader() {
        // Fall back to this class's own loader when the editor's extension
        // classloader isn't set (e.g. early startup, or a standalone launch
        // path) — icon resources live on the app classpath either way.
        ClassLoader cl = DogsBayAIEditor.getStaticExtensionClassLoader();
        this.classLoader = (cl != null) ? cl : DogsBayImageLoader.class.getClassLoader();
    }

    /** @return the shared loader instance. */
    public static DogsBayImageLoader get() {
        return Holder.INSTANCE;
    }

    /**
     * @param name a classpath resource path (e.g. {@code "com/dogsbay/.../Foo.gif"}).
     * @return the icon for {@code name}, loaded once and cached.
     */
    public ImageIcon getImage(String name) {
        return cache.computeIfAbsent(name, key -> {
            URL resource = classLoader.getResource(key);
            // Degrade gracefully (empty icon) rather than NPE'ing the whole UI if a
            // resource is missing/renamed — the button just renders without an image.
            return resource != null ? new ImageIcon(resource) : new ImageIcon();
        });
    }

    /**
     * @param url the location of the image.
     * @return the icon for {@code url}, cached by the URL's string form so an
     *         equal URL returns the same instance.
     */
    public ImageIcon getImage(URL url) {
        return cache.computeIfAbsent(url.toString(), key -> new ImageIcon(url));
    }
}
