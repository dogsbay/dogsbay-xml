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

package com.dogsbay.dogsbayaieditor.component;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;
import javax.swing.ProgressMonitor;

/**
 * This AutomaticProgressMonitor is used to ...
 *
 * @version $Revision: 1.3 $, $Date: 2004/05/03 18:40:19 $
 * @author Dogsbay
 */
public class AutomaticProgressMonitor extends ProgressMonitor {
	private int progress = 0;
	private Timer timer = null;
	private Thread thread = null;
	private String title = null;
	private int interval = -1;
	
	public AutomaticProgressMonitor( Component parent, String title, String message, int interval) {
		super( parent, message, title, 0 , 100);
		
		this.title = title;
		this.interval = interval;
	}
	
	public void start() {
		this.thread = Thread.currentThread();
		progress = 0;

		timer = new Timer( interval, new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				if ( isCanceled()) {
					thread.stop();
					stop();
				} else {
					if ( progress != 98)	{
						progress += 1;
					}

					setProgress( progress);
				}
			}
		});

//		thread.start();
		timer.start();
	}

	public void stop() {
//		timer.stop();
//		timer = null;
		progress = 99;
		setProgress( progress);
	}

	public void close() {
		timer.stop();
		timer = null;
		super.close();
	}
}
