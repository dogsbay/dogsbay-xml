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

package com.dogsbay.dogsbayaieditor.services;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Simple publish/subscribe event system. All events are dispatched on the EDT.
 */
public class EventBus {
	private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

	public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
		subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
	}

	public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
		List<Consumer<?>> handlers = subscribers.get(eventType);
		if (handlers != null) {
			handlers.remove(handler);
		}
	}

	@SuppressWarnings("unchecked")
	public void publish(Object event) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> publish(event));
			return;
		}
		List<Consumer<?>> handlers = subscribers.get(event.getClass());
		if (handlers != null) {
			for (Consumer handler : handlers) {
				try {
					handler.accept(event);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
