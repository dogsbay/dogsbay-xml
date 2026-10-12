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

package com.dogsbay.xml.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

public class ScrollableEditorPanel extends JPanel implements Scrollable {
	private XmlEditorPane editor = null;
	
	public ScrollableEditorPanel( XmlEditorPane editor) {
		super( new BorderLayout());
		
		this.editor = editor;
		
		add( editor, BorderLayout.CENTER);
	}

	public Dimension getPreferredScrollableViewportSize() {
	    return getPreferredSize();
	}

	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return editor.getScrollableUnitIncrement( visibleRect, orientation, direction);
	}

	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return editor.getScrollableBlockIncrement( visibleRect, orientation, direction);
	}  

	public boolean getScrollableTracksViewportWidth() {
	
		if ( editor.isWrapped()) {
			return true;
		} else if ( getParent() instanceof JViewport) {
		    return (((JViewport)getParent()).getWidth() > getPreferredSize().width);
		}

		return false;
	}

	public boolean getScrollableTracksViewportHeight() {
		if ( getParent() instanceof JViewport) {
		    return (((JViewport)getParent()).getHeight() > getPreferredSize().height);
		}
		return false;
	}
	
	public void cleanup() {
		removeAll();
		
		editor = null;
	}
}
