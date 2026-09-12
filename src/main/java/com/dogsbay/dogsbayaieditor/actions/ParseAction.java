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
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;

/**
 * An action that can be used to parse the XML content.
 *
 * @version	$Revision: 1.5 $, $Date: 2005/04/28 13:47:52 $
 * @author Dogsbay
 */
 public class ParseAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private ImageIcon parsedIcon		= null;
	private ImageIcon unparsedIcon	= null;
	
	private Editor editor = null;
	private PluginViewPanel pluginViewPanel = null;
	
	private DogsBayAIEditor parent = null;

 	/**
	 * The constructor for the action which allows for validating
	 * the XML content.
	 *
	 * @param editor the XML Editor
	 */
 	public ParseAction( DogsBayAIEditor parent) {
		super( "Check Well-formedness");
		
		parsedIcon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Parse16.gif");
		unparsedIcon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Unparsed16.gif");
		
		this.parent = parent;

		if (DEBUG) System.out.println( "ParseAction( "+parent+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0, false));
		putValue( SMALL_ICON, unparsedIcon);
		putValue( SHORT_DESCRIPTION, "Check for well-formedness");
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		if ( view instanceof Editor) {
			editor = (Editor)view;
			pluginViewPanel = null;
		} else if ( view instanceof PluginViewPanel) {
			pluginViewPanel = (PluginViewPanel)view;
			editor = null;
		}
		else {
		    editor = null;
		    pluginViewPanel = null;
		}
		
		setDocument( parent.getDocument());
	}

	public void setDocument( DogsBayDocument doc) {
		if ( doc != null && !doc.isDTD()) {
		    setEnabled( (editor != null) || (pluginViewPanel != null));
		} else {
			setEnabled( false);
		}
	}
	
	public void setParsed( boolean parsed) {
		if ( parsed) {
			putValue( SMALL_ICON, parsedIcon);
		} else {
			putValue( SMALL_ICON, unparsedIcon);
		}
	}
	
	/**
	 * The implementation of the parse action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "ParseAction.actionPerformed( "+event+")");
		
		parent.setWait( true);
		parent.setStatus( "Parsing...");

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {

				try {
				    if(editor != null) {
				        editor.parse();
				    }
				    else {
				    	pluginViewPanel.parse();
				    }

				} catch ( final SAXParseException e) {
				} catch ( final IOException e) {
				} catch ( final Throwable t) {
					t.printStackTrace();
				} finally {
					DogsBayDocument doc = parent.getDocument();

					if ( doc != null && !doc.isXML()) {
						parent.getOutputPanel().startCheck( "PARSE", "["+FileUtilities.getXercesVersion()+"] Checking \""+doc.getName()+"\" for Well-formedness ...");
						Exception e = doc.getError();
						
						if ( e instanceof SAXParseException) {
							parent.getOutputPanel().setError( "PARSE", (SAXParseException)e);
							parent.getOutputPanel().selectParseTab();
						} else if ( e instanceof IOException) {
							parent.getOutputPanel().setError( "PARSE", (IOException)e);
							parent.getOutputPanel().selectParseTab();
						}
					}

					parent.setStatus( "Done");
					parent.setWait( false);
					
					if(editor != null) {
					    editor.setFocus();
					}
					else {
						pluginViewPanel.setFocus();
					}
				}
			}
		};
		
		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
}
