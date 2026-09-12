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
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayOutputFormat;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.editor.EditorProperties;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Formats the editors text.
 *
 * @version	$Revision: 1.6 $, $Date: 2004/06/01 10:17:46 $
 * @author Dogsbay
 */
 public class FormatAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;

 	/**
	 * The constructor for the action which formats the 
	 * editors text.
	 *
	 * @param editor the editor pane.
	 */
 	public FormatAction( DogsBayAIEditor parent, ConfigurationProperties properties) {
		super( "Format");
		
		this.parent = parent;
		this.properties = properties;
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'o'));
		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Format16.gif"));
		putValue( SHORT_DESCRIPTION, "Format");
		
		super.setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		if ( view instanceof Editor) {
			editor = (Editor)view;
		} else {
			editor = null;
		}
		
		setDocument( parent.getDocument());
	}

	public void setDocument( DogsBayDocument doc) {
		if ( doc != null && doc.isXML()) {
			setEnabled( editor != null);
		} else {
			setEnabled( false);
		}
	}
	
	
//	public void setEnabled( boolean enabled) {
//		if ( editor != null) {
//			super.setEnabled( enabled);
//		}
//	}

	/**
	 * The implementation of the format action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "FormatAction.actionPerformed( "+event+")");
		
		parent.setWait( true);
		parent.setStatus( "Formatting ...");

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {
				try {
					DogsBayDocument document = parent.getDocument();
					
					parent.getView().updateModel();

					String text = document.getText();
					
					String encoding = document.getEncoding();
					URL url = document.getURL();
					String systemId = null;
					
					if ( url != null) {
						systemId = url.toString();
					}
					
					DogsBayOutputFormat format = new DogsBayOutputFormat();
					format.setEncoding( encoding);
					
					if ( document.hasDeclaration()) {
						if ( document.getStandalone() != DogsBayDocument.STANDALONE_NONE) {
							format.setStandalone( document.getStandalone());
							format.setOmitStandalone( false);
						}
						
						format.setVersion( document.getVersion());
						format.setOmitEncoding( !document.hasEncoding());
						format.setSuppressDeclaration( false);
					} else {
						format.setSuppressDeclaration( true);
					}

					final String result = format( text, encoding, systemId, format);

			 		SwingUtilities.invokeLater( new Runnable(){
						public void run() {
							editor.setText( result);
							parent.getView().updateModel();
						}
					});
				} catch ( Exception e) {
					MessageHandler.showError( parent, "Error Formatting the document.\nPlease make sure the document is well-formed.", "Format Error");
					e.printStackTrace();
				} finally {
					parent.setStatus( "Done");
					parent.setWait( false);
					editor.setFocus();
				}
			}
		};
				
		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
 	
 	public String format( String text, String encoding, String systemId) throws IOException, SAXParseException {
		DogsBayOutputFormat format = new DogsBayOutputFormat();
		return format( text, encoding, systemId, format);
 	}

	/**
	 * Format the active text editor's buffer in place so the view matches the bytes
	 * format-on-save writes to disk. No-op unless the text editor is the active view
	 * ({@code editor != null}) and formatting actually changes the content (so an
	 * already-canonical document saves without disturbing the caret). Safe off the EDT.
	 */
	public void formatInPlace() {
		if ( editor == null) {
			return;   // text editor not active → leave the model for the disk-bytes hook
		}
		try {
			DogsBayDocument document = parent.getDocument();
			if ( document == null) {
				return;
			}
			parent.getView().updateModel();
			String text = document.getText();
			String encoding = document.getEncoding();
			URL url = document.getURL();
			String systemId = ( url != null) ? url.toString() : null;
			java.nio.file.Path file = fileFromSystemId( systemId);
			// The buffer is LF internally; save() restores the file's own line ending.
			// Same fallback as the disk-bytes hook (user default, not built-in) so the
			// view and disk never diverge for untitled / non-file documents.
			com.dogsbay.xml.format.FormatStyle style = ( file != null
					? com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.forFile( file)
					: com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.userDefault())
					.withNewline( com.dogsbay.xml.format.FormatStyle.NewlineStyle.LF);
			final String result = com.dogsbay.xml.format.FormatEngine.format( text, systemId, encoding, style);
			if ( result == null || result.equals( text)) {
				return;   // already canonical → don't touch the buffer/caret
			}
			Runnable apply = () -> {
				// Preserve the caret across the reformat (Editor.setText resets it to 0).
				javax.swing.text.JTextComponent pane = editor.getEditor();
				int caret = ( pane != null) ? pane.getCaretPosition() : 0;
				editor.setText( result);
				parent.getView().updateModel();
				if ( pane != null) {
					try {
						pane.setCaretPosition( Math.min( caret, pane.getDocument().getLength()));
					} catch ( Exception ignore) {
						// caret out of range after reflow → leave at 0
					}
				}
			};
			if ( SwingUtilities.isEventDispatchThread()) {
				apply.run();
			} else {
				SwingUtilities.invokeAndWait( apply);
			}
		} catch ( Exception e) {
			// never let formatting break a save
		}
	}

 	public String format( String text, String encoding, String systemId, DogsBayOutputFormat format) throws IOException, SAXParseException {
		// One canonical form everywhere: F4 uses the SAME resolved house style (the
		// project's .dogsbay/config.xml, else the built-in default) as the CLI/MCP and
		// format-on-save, via the idempotent FormatEngine. It never hard-wraps prose and
		// preserves DITA verbatim blocks (codeblock/pre/…). The editor buffer is LF
		// internally; save() restores the file's own line ending.
		java.nio.file.Path file = fileFromSystemId( systemId);
		com.dogsbay.xml.format.FormatStyle style = ( file != null)
				? com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.forFile( file)
				: com.dogsbay.xml.format.FormatStyle.defaults();
		return com.dogsbay.xml.format.FormatEngine.format( text, systemId, encoding, style);
 	}

	/** Resolve a local file path from a {@code file:} systemId, or null. */
	private static java.nio.file.Path fileFromSystemId( String systemId) {
		if ( systemId == null || !systemId.startsWith( "file:")) {
			return null;
		}
		try {
			return java.nio.file.Path.of( new java.net.URI( systemId));
		} catch ( Exception e) {
			return null;
		}
	}
}
