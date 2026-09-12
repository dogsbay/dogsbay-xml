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

import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The desktop frame, displays the desktop services buttons and 
 * allows for adding, removing and opening services that are 
 * placed on the desktop.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/04/30 11:05:30 $
 * @author Dogsbay
 */
public abstract class StatusFrame extends JFrame{
	private static final boolean DEBUG = false;

	private WaitGlassPane waitPane = null;

	public StatusFrame() {
		// Init a Glass Pane for the wait cursor
		waitPane = new WaitGlassPane();
		setGlassPane(waitPane);

	}

	public abstract void setStatus( final String status);

	/**
	 * Sets the wait cursor on the editor frame.
	 *
	 * @param enabled true when wait is enabled.
	 */
	public void setWait(final boolean enabled) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				waitPane.setVisible(enabled);
			}
		});
	}

	private class WaitGlassPane extends JPanel {
		public WaitGlassPane() {
			setOpaque(false);
			addKeyListener(new KeyAdapter() {
			});
			addMouseListener(new MouseAdapter() {
			});
			super.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		}
	}
}
