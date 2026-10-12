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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the EventBus publish/subscribe system.
 *
 * Note: These tests call publish() directly on the EDT (via test thread),
 * so the EDT guard in EventBus will defer via invokeLater. To test
 * synchronous delivery, we run tests that verify state after the bus
 * has processed events. For simplicity, we test the core logic by
 * invoking from within a headless-safe context.
 */
class EventBusTest {

	private EventBus eventBus;

	// Simple event types for testing
	record TestEventA(String message) {}
	record TestEventB(int value) {}

	@BeforeEach
	void setUp() {
		eventBus = new EventBus();
	}

	@Test
	void subscribeAndPublish_deliversEvent() throws Exception {
		AtomicReference<String> received = new AtomicReference<>();

		eventBus.subscribe(TestEventA.class, event -> received.set(event.message()));

		// Publish on EDT to get synchronous delivery
		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("hello"))
		);

		assertEquals("hello", received.get());
	}

	@Test
	void unsubscribe_stopsDelivery() throws Exception {
		AtomicInteger count = new AtomicInteger(0);
		Consumer<TestEventA> handler = event -> count.incrementAndGet();

		eventBus.subscribe(TestEventA.class, handler);

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("first"))
		);
		assertEquals(1, count.get());

		eventBus.unsubscribe(TestEventA.class, handler);

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("second"))
		);
		assertEquals(1, count.get(), "Handler should not receive events after unsubscribe");
	}

	@Test
	void publish_withNoSubscribers_doesNotThrow() throws Exception {
		// Should not throw even with no subscribers
		javax.swing.SwingUtilities.invokeAndWait(() ->
			assertDoesNotThrow(() -> eventBus.publish(new TestEventA("orphan")))
		);
	}

	@Test
	void multipleSubscribers_allReceiveEvent() throws Exception {
		List<String> received = new ArrayList<>();

		eventBus.subscribe(TestEventA.class, event -> received.add("sub1:" + event.message()));
		eventBus.subscribe(TestEventA.class, event -> received.add("sub2:" + event.message()));
		eventBus.subscribe(TestEventA.class, event -> received.add("sub3:" + event.message()));

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("broadcast"))
		);

		assertEquals(3, received.size());
		assertTrue(received.contains("sub1:broadcast"));
		assertTrue(received.contains("sub2:broadcast"));
		assertTrue(received.contains("sub3:broadcast"));
	}

	@Test
	void exceptionInSubscriber_doesNotPreventOthers() throws Exception {
		List<String> received = new ArrayList<>();

		eventBus.subscribe(TestEventA.class, event -> received.add("before"));
		eventBus.subscribe(TestEventA.class, event -> {
			throw new RuntimeException("intentional test error");
		});
		eventBus.subscribe(TestEventA.class, event -> received.add("after"));

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("test"))
		);

		assertEquals(2, received.size());
		assertTrue(received.contains("before"));
		assertTrue(received.contains("after"));
	}

	@Test
	void differentEventTypes_areIsolated() throws Exception {
		AtomicReference<String> receivedA = new AtomicReference<>();
		AtomicReference<Integer> receivedB = new AtomicReference<>();

		eventBus.subscribe(TestEventA.class, event -> receivedA.set(event.message()));
		eventBus.subscribe(TestEventB.class, event -> receivedB.set(event.value()));

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventA("only-a"))
		);

		assertEquals("only-a", receivedA.get());
		assertNull(receivedB.get(), "TestEventB subscriber should not receive TestEventA");

		javax.swing.SwingUtilities.invokeAndWait(() ->
			eventBus.publish(new TestEventB(42))
		);

		assertEquals(42, receivedB.get());
	}
}
