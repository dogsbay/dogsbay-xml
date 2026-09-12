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

package com.dogsbay.xml.browser;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

//import org.jdesktop.jdic.browser.WebBrowser;
//import org.jdesktop.jdic.browser.WebBrowserEvent;
//import org.jdesktop.jdic.browser.WebBrowserListener;


import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.transform.TransformerUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * Shows a tree view of a XML document.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/08/04 18:12:43 $
 * @author Dogsbay
 */
public class Browser extends ViewPanel {
	private static final boolean DEBUG = false;
	private static final ImageIcon ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/xml/browser/icons/BrowserIcon.gif");
	
//	private DogsBayWebBrowser browser = null;
	private JPanel browserPanel = null;
	
	private DogsBayDocument document = null;

	/**
	 * Constructs an explorer view with the ExplorerProperties supplied.
	 *
	 * @param properties the explorer properties.
	 */
	public Browser( DogsBayAIEditor parent, BrowserProperties props) {
		super( new BorderLayout());
	}
	
	public void updateHelper() {}
	
//	public DogsBayWebBrowser getBrowser() {
//		if ( browser == null) {
//			try	{
//				browser = new DogsBayWebBrowser();
//				browser.addWebBrowserListener( new WebBrowserListener() {
//					public void downloadCompleted( WebBrowserEvent e) {
//						System.out.println( "downloadCompleted( "+toString( e)+")");
//					}
//					public void downloadError( WebBrowserEvent e) {
//						System.out.println( "downloadError( "+toString( e)+")");
//					}
//	
//					public void downloadProgress( WebBrowserEvent e) {
//						System.out.println( "downloadProgress( "+toString( e)+")");
//					}
//	
//					public void downloadStarted( WebBrowserEvent e) {
//						System.out.println( "downloadStarted( "+toString( e)+")");
//					}
//					
//					private String toString( WebBrowserEvent e) {
//						return e.toString();
//					}
//				});
//				
//				
//				DogsBayWebBrowser.setDebug( true);
//			
//				System.out.println( "Browser initialised = "+browser.getStatus().isInitialized());
//			} catch ( Exception e) {
//				e.printStackTrace();
//			}
//		}
//		
//		return browser;
//	}
	
	public void initBrowserPanel() {
		if ( browserPanel == null) {
			browserPanel = new JPanel( new BorderLayout());
//			browserPanel.add( getBrowser(), BorderLayout.CENTER);

			add( browserPanel, BorderLayout.CENTER);
		}
	}

	public void setFocus() {
//		browser.requestFocusInWindow();
	}

	/**
	 * Update the preferences.
	 */
	public void updatePreferences() {
	}

	/**
	 * Returns the icon
	 */
	public ImageIcon getIcon() {
		return ICON;
	}

	public boolean hasLatestInformation() {
		return false;
	}

	public void setProperties() {}
	
	public void setDocument( DogsBayDocument document) {
		this.document = document;
	}

	public void update() {
//		remove( getBrowserPanel());
//		add( getBrowserPanel(), BorderLayout.CENTER);
		
		initBrowserPanel();
		updateBrowser();
	}

	public void cleanup() {
//		removeAll();
	}
	
//	public static class DogsBayWebBrowser extends WebBrowser {
//		public DogsBayWebBrowser() {
//			super();
//		}
//
//		public DogsBayWebBrowser( URL url) {
//			super( url);
//		}
//	}
	
	private void updateBrowser() {
		if ( !document.isError()) {
			XElement root = document.getRoot();
			
			if ( root.getName().equalsIgnoreCase( "html") || document.getName().toLowerCase().endsWith( "htm") || document.getName().toLowerCase().endsWith( "html")) {
				writeOutputAsHTML( document);
			} else { // normal XML, use the default stylesheet to convert to html
				try {
					URL url = document.getURL();
					File file = new File( System.getProperty( "java.io.tmpdir"));

					if ( url != null && url.getProtocol().equals( "file")) {
						file = new File( url.getFile());
					}

					if ( !file.isDirectory()) {
						file = file.getParentFile();
					}

					File temp = null;

					try {
						temp = File.createTempFile( "temp"+URLUtilities.getFileNameWithoutExtension( document.getName()), ".htm", file);
					} catch ( IOException x) { 
						// could not create file, try in temp dir...
						temp = File.createTempFile( "temp"+URLUtilities.getFileNameWithoutExtension( document.getName()), ".htm", new File( System.getProperty( "java.io.tmpdir")));
					}

					temp.deleteOnExit();

					FileOutputStream stream = new FileOutputStream( temp);
					
					TransformerUtilities.transform( document, stream, false);
					stream.flush();
					stream.close();

					url = DogsBayURLUtilities.getURLFromFile(temp);
					URL newUrl = new URL( url.getProtocol(), "localhost", url.getFile());

//					getBrowser().setURL( new URL( URLUtilities.encodeURL( newUrl.toString())));
				} catch ( Exception x) {
					x.printStackTrace();
				}
			}
		} else { // error
			// always write the document as html
//			if ( document.getName().endsWith( "htm") || document.getName().endsWith( "html")) {
			writeOutputAsHTML( document);
//			}
			
		}
 	}

	private void writeOutputAsHTML( DogsBayDocument document) {
		URL url = document.getURL();
		File file = new File( System.getProperty( "java.io.tmpdir"));
		
		if ( url != null && url.getProtocol().equals( "file")) {
			file = new File( url.getFile());
		}

		if ( !file.isDirectory()) {
			file = file.getParentFile();
		}
 		
		try {
			File temp = null;

			try {
				temp = File.createTempFile( "temp"+URLUtilities.getFileNameWithoutExtension( document.getName()), ".htm", file);
			} catch ( IOException e) { 
				// could not create file, try in temp dir...
				temp = File.createTempFile( "temp"+URLUtilities.getFileNameWithoutExtension( document.getName()), ".htm", new File( System.getProperty( "java.io.tmpdir")));
			}
	
			temp.deleteOnExit();

			FileOutputStream stream = new FileOutputStream( temp);
			stream.write( document.getText().getBytes( document.getJavaEncoding()));
			stream.flush();
			stream.close();
	
			url = DogsBayURLUtilities.getURLFromFile(temp);

			URL newUrl = new URL( url.getProtocol(), "localhost", url.getFile());

//			getBrowser().setURL( new URL( URLUtilities.encodeURL( newUrl.toString())));
		} catch ( IOException e) {
			e.printStackTrace();
		}
	}
} 
