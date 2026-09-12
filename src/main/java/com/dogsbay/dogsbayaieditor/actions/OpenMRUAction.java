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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.event.ActionEvent;
import java.net.URL;

import javax.swing.AbstractAction;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * An action that can be used to open a recently used XML Document.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/05/18 16:57:45 $
 * @author Dogsbay
 */
 public class OpenMRUAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private URL url = null;
 	private DogsBayAIEditor parent = null;

 	/**
	 * The constructor for the add action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public OpenMRUAction( DogsBayAIEditor parent, URL url, int index) {
 		super( index+" "+URLUtilities.getFileName( url));
		
		char[] chars = String.valueOf( index).toCharArray();

 		putValue( MNEMONIC_KEY, Integer.valueOf( chars[0]));

	 	this.parent = parent;
	 	this.url = url;
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		parent.setWait( true);
		parent.setStatus( "Opening ...");

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {

				try {
					parent.open( url, null, true);
			 	} finally {
			 		parent.setStatus( "Done");
			 		parent.setWait( false);
			 	}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
}
