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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;

import org.apache.xerces.xni.XNIException;
import org.bounce.DefaultFileFilter;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultDocumentType;
import org.dom4j.tree.DefaultNamespace;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.schema.dtd.DTDDocument;
import com.dogsbay.schema.rng.RNGDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLGrammar;
import com.dogsbay.dogsbayaieditor.component.AutomaticProgressMonitor;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarPropertiesDialog;
import com.dogsbay.dogsbayaieditor.grammar.GrammarSelectionDialog;
import com.dogsbay.dogsbayaieditor.grammar.NamespaceProperties;
import com.dogsbay.dogsbayaieditor.grammar.TagCompletionProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.l2fprod.common.swing.JDirectoryChooser;

/**
 * Methods to handle document and schema loading etc...
 *
 * @version	$Revision: 1.17 $, $Date: 2005/08/31 10:27:16 $
 * @author Dogsbay
 */
public class FileUtilities {
 	private static final boolean DEBUG = false;

 	private static JFrame parent = null;
 	private static DogsBayAIEditor editor = null;
 	private static ConfigurationProperties properties = null;

 	private static URLChooserDialog urlChooser = null;

 	private static DefaultFileFilter xmlFilter = null;
	private static DefaultFileFilter xslFilter = null;
 	private static DefaultFileFilter dtdFilter = null;
 	private static DefaultFileFilter pdfFilter = null;
 	private static DefaultFileFilter psFilter = null;
 	private static DefaultFileFilter svgFilter = null;
 	private static DefaultFileFilter txtFilter = null;

 	private static JFileChooser defaultFileChooser = null;
 	private static JFileChooser currentFileChooser = null;
 	private static JFileChooser foFileChooser = null;
 	private static JFileChooser fileChooser = null;
 	private static JFileChooser jarChooser = null;
 	private static JFileChooser catalogChooser = null;
 	private static JFileChooser schemaChooser = null;
 	private static JFileChooser dirChooser = null;
 	private static FileFilter allFilter = null;

 	private static FileView fileView = null;

 	private static GrammarSelectionDialog grammarSelectionDialog = null;	
 	private static Hashtable grammarPropertiesDialogs = new Hashtable();

	private static JDirectoryChooser jDirectoryChooser;	
    
    private static FileView getFileView() {
        if ( fileView == null) {
            fileView = new DefaultFileView();
        }
        
        return fileView;
    }

 	/**
 	 * Creates a new filechooser, setting the FileView.
 	 *
 	 * @return the file chooser
 	 */
 	public static JFileChooser createFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileView( getFileView());

 		return chooser;
 	}
 	
 	/**
 	 * Gets the file chooser and sets the current selected-file 
 	 * to the last selected file and the filefilter to the last 
 	 * selected type.
 	 *
 	 * @return the file chooser
 	 */
 	public static JFileChooser getFileChooser() {
 		if ( defaultFileChooser == null) {
 			defaultFileChooser = createFileChooser();
 			defaultFileChooser.setAcceptAllFileFilterUsed( true);
 			defaultFileChooser.addChoosableFileFilter( getXMLFilter());
 			defaultFileChooser.addChoosableFileFilter( getDTDFilter());

 			allFilter = defaultFileChooser.getAcceptAllFileFilter();
 		} else {
 			// remove previous filters...
 			FileFilter[] filters = defaultFileChooser.getChoosableFileFilters();

 			for ( int i = 0; i < filters.length; i++) {
 				if ( filters[i] instanceof DefaultFileFilter 
					 && filters[i] != getXMLFilter()
					 && filters[i] != getDTDFilter()) {
 					defaultFileChooser.removeChoosableFileFilter( filters[i]);
 				}
 			}
 		}

 		FileFilter selectedFilter = allFilter;
 		int lastGrammar = properties.getLastOpenedGrammar();
 		
 		// set the current filters...
 		Vector grammars = properties.getGrammarProperties();
 		
 		for ( int i = 0; i < grammars.size(); i++) {
 			GrammarProperties grammar = (GrammarProperties)grammars.elementAt(i);
 			FileFilter filter = new DefaultFileFilter( grammar.getExtensions(), grammar.getDescription());
 			defaultFileChooser.addChoosableFileFilter( filter);
 			
 			if ( i == lastGrammar) {
 				selectedFilter = filter;
 			}
 		}
 		
 		File file = new File( properties.getLastOpenedDocument());
 		defaultFileChooser.setSelectedFile( null);
 		defaultFileChooser.setFileFilter( selectedFilter);
 		defaultFileChooser.setCurrentDirectory( file);
 		defaultFileChooser.rescanCurrentDirectory();
 		
 		return defaultFileChooser;
 	}

 	/**
 	 * Gets the file chooser and sets the current selected-file 
 	 * to the file provided as the parameter.
 	 *
 	 * @return the file chooser
 	 */
 	public static JFileChooser getFileChooser( File selectedFile) {
 		if ( fileChooser == null) {
 			fileChooser = createFileChooser();
 			fileChooser.setAcceptAllFileFilterUsed( true);
 			fileChooser.addChoosableFileFilter( getXMLFilter());
 			fileChooser.addChoosableFileFilter( getDTDFilter());

 			allFilter = fileChooser.getAcceptAllFileFilter();
 		} else {
 			// remove previous filters...
 			FileFilter[] filters = fileChooser.getChoosableFileFilters();

 			for ( int i = 0; i < filters.length; i++) {
 				if ( filters[i] instanceof DefaultFileFilter 
					 && filters[i] != getXMLFilter()
					 && filters[i] != getDTDFilter()) {
 					fileChooser.removeChoosableFileFilter( filters[i]);
 				}
 			}
 		}

 		FileFilter selectedFilter = allFilter;
 		int lastGrammar = properties.getLastOpenedGrammar();
 		
 		// set the current filters...
 		Vector grammars = properties.getGrammarProperties();
 		
 		for ( int i = 0; i < grammars.size(); i++) {
 			GrammarProperties grammar = (GrammarProperties)grammars.elementAt(i);
 			FileFilter filter = new DefaultFileFilter( grammar.getExtensions(), grammar.getDescription());
 			fileChooser.addChoosableFileFilter( filter);
 			
 			if ( i == lastGrammar) {
 				selectedFilter = filter;
 			}
 		}
		
		if ( selectedFile == null) {
			selectedFile = new File( properties.getLastOpenedDocument());
		}
 		
 		fileChooser.setSelectedFile( null);
 		fileChooser.setFileFilter( selectedFilter);
 		fileChooser.setCurrentDirectory( selectedFile);
 		fileChooser.rescanCurrentDirectory();
 		
 		return fileChooser;
 	}

 	/**
 	 * Gets the file chooser and sets the current selected-file 
 	 * to the current open document file and the filefilter to the 
	 * current selected type.
 	 *
 	 * @return the file chooser
 	 */
 	public static JFileChooser getCurrentFileChooser() {
 		if ( currentFileChooser == null) {
 			currentFileChooser = createFileChooser();
 			currentFileChooser.setAcceptAllFileFilterUsed( true);

 			currentFileChooser.addChoosableFileFilter( getXMLFilter());
 			currentFileChooser.addChoosableFileFilter( getDTDFilter());

 			allFilter = currentFileChooser.getAcceptAllFileFilter();
 		} else {
 			// remove previous filters...
 			FileFilter[] filters = currentFileChooser.getChoosableFileFilters();

 			for ( int i = 0; i < filters.length; i++) {
 				if ( filters[i] instanceof DefaultFileFilter 
					 && filters[i] != getXMLFilter()
					 && filters[i] != getDTDFilter()) {
 					currentFileChooser.removeChoosableFileFilter( filters[i]);
 				}
 			}
 		}

 		FileFilter selectedFilter = allFilter;
		URL url = null;
 		
 		if ( editor != null) {
	 		// set the current filters...
	 		Vector grammars = properties.getGrammarProperties();
	 		GrammarProperties grammar = editor.getGrammar();
	 		
	 		for ( int i = 0; i < grammars.size(); i++) {
	 			GrammarProperties g = (GrammarProperties)grammars.elementAt(i);
	 			FileFilter filter = new DefaultFileFilter( g.getExtensions(), g.getDescription());
	 			currentFileChooser.addChoosableFileFilter( filter);
	 			
	 			if ( grammar != null && grammar.getID().equals( g.getID())) {
	 				selectedFilter = filter;
	 			}
	 		}
			
			DogsBayDocument doc = editor.getDocument();
	
			if ( doc != null) {
				url = doc.getURL();
			}
 		}
		
		File file = null;
		
		if ( url != null && url.getProtocol().equals("file")) {
			file = new File( url.getFile());
		} else {
			file = new File( properties.getLastOpenedDocument());
		}
 		
 		currentFileChooser.setSelectedFile( null);
 		currentFileChooser.setFileFilter( selectedFilter);
 		currentFileChooser.setCurrentDirectory( file);
 		currentFileChooser.rescanCurrentDirectory();
 		
 		return currentFileChooser;
 	}
	
	public static File getLastOpenedFile() {
		return new File( properties.getLastOpenedDocument());
	}

 	public static JFileChooser getSchemaChooser() {
 		if ( schemaChooser == null) {
 			schemaChooser = createFileChooser();

 			schemaChooser.setAcceptAllFileFilterUsed( true);
	 		schemaChooser.addChoosableFileFilter( getXMLFilter());
	 		schemaChooser.addChoosableFileFilter( new DefaultFileFilter( "xsd", "XML Schema"));

	 		File file = new File( properties.getLastOpenedDocument());
	 		schemaChooser.setCurrentDirectory( file);
 		} 
 		
 		URL url = null;

 		if ( editor != null) {
 			DogsBayDocument doc = editor.getDocument();
	
	 		if ( doc != null) {
	 			url = doc.getURL();
	 		}
 		}
 		
 		File file = null;
 		
 		schemaChooser.setSelectedFile( null);

 		if ( url != null && url.getProtocol().equals("file")) {
 			file = new File( url.getFile());
	 		schemaChooser.setCurrentDirectory( file);
 		}

 		schemaChooser.rescanCurrentDirectory();

 		return schemaChooser;
 	}

 	public static JFileChooser getJarChooser() {
 		if ( jarChooser == null) {
 			jarChooser = createFileChooser();

 			jarChooser.setAcceptAllFileFilterUsed( true);
 	 		jarChooser.addChoosableFileFilter( new DefaultFileFilter( "jar", "JAR File"));
 		} 
 		
 		File file = new File( properties.getLastOpenedDocument());
 		jarChooser.setCurrentDirectory( file);

 		return jarChooser;
 	}

 	public static JFileChooser getCatalogChooser() {
 		if ( catalogChooser == null) {
 			catalogChooser = createFileChooser();

 			catalogChooser.setAcceptAllFileFilterUsed( true);
 	 		catalogChooser.addChoosableFileFilter( getXMLFilter());
 	 		catalogChooser.addChoosableFileFilter( new DefaultFileFilter( "xcat,cat", "XML Catalog File"));
 		} 
 		
 		File file = new File( properties.getLastOpenedDocument());
 		catalogChooser.setCurrentDirectory( file);

 		return catalogChooser;
 	}

 	public static FileFilter getDTDFilter() {
 		if ( dtdFilter == null) {
 			dtdFilter = new DefaultFileFilter( "dtd,mod,ent", "Document Type Definition");
 		}
 		
 		return dtdFilter;
 	}

 	public static FileFilter getXMLFilter() {
 		if ( xmlFilter == null) {
 			xmlFilter = new DefaultFileFilter( "xml", "XML Document");
 		}
 		
 		return xmlFilter;
 	}

	public static FileFilter getXSLFilter() {
		if ( xslFilter == null) {
			xslFilter = new DefaultFileFilter( "xsl,xslt", "XSL Stylesheet");
		}
 		
		return xslFilter;
	}

	public static FileFilter getPDFFilter() {
		if ( pdfFilter == null) {
			pdfFilter = new DefaultFileFilter( "pdf", "Adobe Portable Document Format");
		}
		
		return pdfFilter;
 	}

 	public static FileFilter getPSFilter() {
 		if ( psFilter == null) {
 			psFilter = new DefaultFileFilter( "ps", "Post Script");
 		}
 		
 		return psFilter;
 	}

 	public static FileFilter getSVGFilter() {
 		if ( svgFilter == null) {
 			svgFilter = new DefaultFileFilter( "svg", "Scalable Vector Graphics");
 		}
 		
 		return svgFilter;
 	}
 	
 	public static FileFilter getTXTFilter() {
 		if ( txtFilter == null) {
 			txtFilter = new DefaultFileFilter( "txt", "Plain Text");
 		}
 		
 		return txtFilter;
 	}

 	public static FileFilter getAllFilter() {
 		if ( allFilter == null) {
 			allFilter = new DefaultFileFilter( "*", "All Files");
 		} 		
 		return allFilter;
 	}

 	public static JFileChooser getFOFileChooser() {
 		if ( foFileChooser == null) {
 			foFileChooser = createFileChooser();
 			foFileChooser.setDialogTitle( "Select FO Output File");

 			foFileChooser.setAcceptAllFileFilterUsed( true);
 	 		foFileChooser.addChoosableFileFilter( getPDFFilter());
 	 		foFileChooser.addChoosableFileFilter( getPSFilter());
 	 		foFileChooser.addChoosableFileFilter( getSVGFilter());
 	 		foFileChooser.addChoosableFileFilter( getTXTFilter());
 		} 
 		
 		File file = null;

 		if ( editor != null) {
	 		DogsBayDocument doc = editor.getDocument();

			if ( doc != null) {
				file = URLUtilities.toFile( doc.getURL());
			}
		}

		if ( file == null) {
 	 		file = new File( properties.getLastOpenedDocument());
		}

 		foFileChooser.setSelectedFile( null);
 		foFileChooser.setCurrentDirectory( file);
 		foFileChooser.rescanCurrentDirectory();

 		return foFileChooser;
 	}

// 	public static URLChooserDialog getURLChooser() {
// 		if ( urlChooser == null) {
//			urlChooser = new URLChooserDialog( parent, "Open URL");
//			urlChooser.setLocationRelativeTo( parent);
// 		}
//
// 		return urlChooser;
// 	}

 	public static JDirectoryChooser getDirectoryChooser() {
 		/*if ( dirChooser == null) {
 			dirChooser = createFileChooser();

	 		dirChooser.setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES);
	 		dirChooser.setCurrentDirectory( new File( properties.getLastOpenedDocument()));
 		} 

		dirChooser.setCurrentDirectory( dirChooser.getCurrentDirectory());
 		dirChooser.rescanCurrentDirectory();
 		
 		
 		
 		return dirChooser;*/
 		if(jDirectoryChooser == null) {
 			jDirectoryChooser = new JDirectoryChooser();
 			
 			jDirectoryChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
 			jDirectoryChooser.setCurrentDirectory( new File( properties.getLastOpenedDocument()));
 		}
 		else {

 			jDirectoryChooser.setCurrentDirectory( jDirectoryChooser.getCurrentDirectory());
 			jDirectoryChooser.rescanCurrentDirectory();
 		}
 		
 		
 		
 		return jDirectoryChooser;
 		
 	}

 	
 	
 	public static GrammarSelectionDialog getGrammarSelectionDialog() {
 		if ( grammarSelectionDialog == null) {
 			grammarSelectionDialog = new GrammarSelectionDialog( parent, properties);
 		}
 		
 		return grammarSelectionDialog;
 	}

 	public static GrammarPropertiesDialog getGrammarPropertiesDialog( String key, boolean override) {
	
		GrammarPropertiesDialog dialog = (GrammarPropertiesDialog)grammarPropertiesDialogs.get( key);
	
 		if ( dialog == null) {
 			dialog = new GrammarPropertiesDialog( editor, properties, key, override);
			grammarPropertiesDialogs.put( key, dialog);
 		}
 		
 		return dialog;
 	}

 	/**
	 * Initialise the File Utilitties.
	 *
	 * @param parent the parent frame.
	 * @param props the configuration properties.
	 */
 	public static void init( JFrame _parent, DogsBayAIEditor _editor, ConfigurationProperties props) {
	 	properties = props;
	 	parent = _parent;
	 	editor = _editor;
 	}

	
	/**
	 * Returns a list of possible Grammar objects for the document.
	 *
	 * @param doc the document.
	 *
	 * @return a list of grammar properties.
	 */
	public static Vector getTypes( DogsBayDocument doc) {
		Vector results = new Vector();
		// set the current filters...
		Vector grammars = properties.getGrammarProperties();
		
		for ( int i = 0; i < grammars.size(); i++) {
			GrammarProperties grammar = (GrammarProperties)grammars.elementAt(i);
			
			if ( isDocumentOfType( doc, grammar, false)) {
				results.addElement( grammar);
			}
		}
		
		if ( results.size() > 1) {
			Vector strictResults = new Vector();

			for ( int i = 0; i < results.size(); i++) {
				GrammarProperties grammar = (GrammarProperties)results.elementAt(i);
				
				if ( isDocumentOfType( doc, grammar, true)) {
					strictResults.addElement( grammar);
				}
			}
			
			if ( strictResults.size() > 0) {
				results = strictResults;
			}
		}
	
		return results;
	}

	/**
	 * Checks to see if this document is of the type provided.
	 *
	 * @param doc the document.
	 * @param type the grammar type.
	 *
	 * @return true when the document is of this type.
	 */
	public static boolean isDocumentOfType( DogsBayDocument doc, GrammarProperties type) {
		return isDocumentOfType( doc, type, false);
	}

	/**
	 * Checks to see if this document is of the type provided.
	 *
	 * @param doc the document.
	 * @param type the grammar type.
	 * @param strict make sure the namespace are the same as well.
	 *
	 * @return true when the document is of this type.
	 */
	public static boolean isDocumentOfType( DogsBayDocument doc, GrammarProperties type, boolean strict) {
		boolean result = false;
		
		if ( doc != null && !doc.isError() && doc.isXML() && type != null) {
			XElement root = doc.getRoot();
			String name = root.getName();
			
			if ( name != null && name.equals( type.getRootElementName())) {
				String namespace = root.namespace();
				String targetNamespace = type.getNamespace();
				
				if ( !StringUtilities.isEmpty( namespace) && namespace.equals( targetNamespace)) {
					result = true;
				} else if ( StringUtilities.isEmpty( targetNamespace)) {
					result = true;
				}
			
				if ( strict) {
					String systemId = type.getSystemID();
					String docSystemId = doc.getSystemID();
					String publicId = type.getPublicID();
					String docPublicId = doc.getPublicID();
					
					// if either publicId is not included, do nothing, otherwise compare them.
					if ( !StringUtilities.isEmpty( publicId) && !StringUtilities.isEmpty( docPublicId)) {
						if ( publicId.equals( docPublicId)) {
							if ( !StringUtilities.isEmpty( systemId) && !StringUtilities.isEmpty( docSystemId)) {
								if ( systemId.equals( docSystemId)) {
									result = true;
								} else {
									result = false;
								}
							} else { // no systemid defined.
								result = true;
							}
						} else {
							result = false;
						}
					}
				}
			}
		}
		
		return result;
	}

	/**
	 * Creates a new DTD document.
	 */
	public static void newDTDDocument() throws IOException, SAXParseException {
		DogsBayDocument document = new DogsBayDocument( DogsBayDocument.DTD_DOCUMENT);
		
		editor.open( document, null);
	}

	/**
	 * Creates a new default document.
	 */
	public static void newDocument() throws IOException, SAXParseException {
		DogsBayDocument document = createDocument();
		
		editor.open( document, null);
	}
	
	/**
	 * Creates a new default document.
	 */
	public static void newDocument(String data) throws IOException, SAXParseException {
		DogsBayDocument document = createDocument();
		document.setText(data);
		editor.open( document, null);
	}

	/**
	 * Creates a new document from the template.
	 *
	 * @param template the template for the document.
	 */
	public static void newDocument( URL template) throws IOException {
		DogsBayDocument document = createDocument( template);
			
		editor.open( document, null);
	}

	/**
	 * Creates a new document for the a specific type.
	 *
	 * @param type the grammar-type of the document.
	 */
	public static void newDocument( GrammarProperties type) throws IOException, SAXParseException {
		Vector tagCompletionSchemas = createTagCompletionSchemas( null, type);
		DogsBayDocument document = createDocument( type);
		
		editor.open( document, null, tagCompletionSchemas, type);
	}

	/**
	 * Saves the document as...
	 * and sets the document in the editor.
	 *
	 * @param file the file representation for the document.
	 */
	public static void saveAsDocument( File file) {
		saveAsDocument( file, editor.getDocument());
	}

	/**
	 * Saves the document as...
	 * and sets the document in the editor.
	 *
	 * @param file the file representation for the document.
	 */
	private static void saveAsDocument( final File file, final DogsBayDocument document) {
		editor.setWait( true);
		editor.setStatus( "Saving ...");

		try	{
			final URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);

			// Run in Thread!!!
			Runnable runner = new Runnable() {
				public void run()  {
					URL oldURL = document.getURL();
					AutomaticProgressMonitor monitor = new AutomaticProgressMonitor( parent, null, "Saving \""+URLUtilities.toString( url)+"\".", 250);
	
					try {
						document.setURL( url);
						
						monitor.start();
						document.save();
						monitor.stop();
						
						SwingUtilities.invokeLater( new Runnable() {
						    public void run() {
								editor.setDocument( document); 
								editor.getChangeManager().discardAllEdits();
						    }
						});
					} catch ( IOException ex){
						ex.printStackTrace();
						MessageHandler.showError( "Could not save Document.", ex, "Saving Error");
					} catch ( Exception ex){
						ex.printStackTrace();
					} finally {
				    	if ( !monitor.isCanceled()) {
							properties.setLastOpenedDocument( url.getFile());
				    	} else {
				    		document.setURL( oldURL);
				    	}
	
						editor.setStatus( "Done");
						editor.setWait( false);
					}
				}
			};
	
			// Create and start the thread ...
			Thread thread = new Thread( runner);
			thread.start();
		} catch ( MalformedURLException ex){
			ex.printStackTrace();
			MessageHandler.showError( "Could not save Document.", ex, "Saving Error");
		}
	}

	/**
	 * Saves the document as...
	 * and sets the document in the editor.
	 *
	 * @param url the url for the document.
	 */
	public static void saveAsRemoteDocument( URL url) {
		saveAsRemoteDocument( url, editor.getDocument());
	}

	/**
	 * Saves the document as...
	 * and sets the document in the editor.
	 *
	 * @param url the url for the document.
	 */
	public static void saveAsRemoteDocument( final URL url, final DogsBayDocument document) {
		editor.setWait( true);
		editor.setStatus( "Saving ...");

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {
				URL oldURL = document.getURL();
				AutomaticProgressMonitor monitor = new AutomaticProgressMonitor( parent, null, "Saving \""+URLUtilities.toString( url)+"\".", 250);

				try {
					document.setURL( url);

					monitor.start();
					document.save();
					monitor.stop();
					
					SwingUtilities.invokeLater( new Runnable() {
					    public void run() {
				    		editor.setDocument( document); 
				    		editor.getChangeManager().discardAllEdits();
					    }
					});
				} catch ( IOException ex){
					ex.printStackTrace();
					MessageHandler.showError( "Could not save Remote Document.", ex, "Saving Error");
				} catch ( Exception ex){
					ex.printStackTrace();
				} finally {
			    	if ( !monitor.isCanceled()) {
			    		properties.setLastOpenedURL( url);
			    	} else {
			    		document.setURL( oldURL);
			    	}

					editor.setStatus( "Done");
					editor.setWait( false);
				}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
	}

	/**
	 * Creates a new SchemaDocument object, used for tag completion.
	 *
	 * @param document the document!
	 * @param props the grammar-type.
	 *
	 * @return the schema.
	 */
	public static Vector createTagCompletionSchemas( DogsBayDocument document, GrammarProperties props) {
		boolean report = true;

		Vector result = new Vector();
		
		if ( props != null) {
			Vector list = props.getTagCompletionPropertiesList();

			if ( list.size() > 0) {
				for ( int i = 0; i < list.size(); i++) {
					int templateType = ((TagCompletionProperties)list.elementAt(i)).getType();
					String templateLocation = ((TagCompletionProperties)list.elementAt(i)).getLocation();
					
					SchemaDocument doc = createTagCompletionSchema( document, props, templateType, templateLocation);
					
					if ( doc != null) {
						result.addElement( doc);
					}
				}
			} else {
				SchemaDocument doc = createTagCompletionSchema( document, props, -1, null);

				if ( doc != null) {
					result.addElement( doc);
				}
			}
		} else {
			SchemaDocument doc = createTagCompletionSchema( document, props, -1, null);

			if ( doc != null) {
				result.addElement( doc);
			}
		}

		return result;
	}
	
	private static SchemaDocument createTagCompletionSchema( DogsBayDocument document, GrammarProperties props, int templateType, String templateLocation) {
		boolean report = true;

		SchemaDocument tagCompletionSchema = null;
		URL schemaURL = null;
		int schemaType = XMLGrammar.TYPE_XSD;

		String schemaLocation = null;
		String validationLocation = null;
		int validationType = -1;
		
		if ( props != null) {
			validationType = props.getValidationGrammar();

			if ( validationType == XMLGrammar.TYPE_XSD || validationType == XMLGrammar.TYPE_DTD || validationType == XMLGrammar.TYPE_RNC || validationType == XMLGrammar.TYPE_RNG) {
				validationLocation = props.getValidationLocation();
			}

			schemaLocation	 = props.getSchemaLocation();
		}

		if ( templateLocation != null && templateLocation.trim().length() > 1) {
			URL base = null;
			schemaType = templateType;

			if ( document != null) {
				base = document.getURL();
			}

			try {
				if ( base != null) {
					schemaURL = new URL( base, templateLocation);
				} else {
					schemaURL = URLUtilities.toURL( templateLocation);
				}
			} catch ( MalformedURLException e) {
				if ( base == null) {
					MessageHandler.showError( "Could not resolve relative Location : "+templateLocation, e, "URL Error");
				} else {
					MessageHandler.showUnexpectedError( e);
				}
			}
		} else if ( validationLocation != null && validationLocation.trim().length() > 1) {
			schemaType = validationType;
			URL base = null;

			if ( document != null) {
				base = document.getURL();
			}

			try {
				if ( base != null) {
					schemaURL = new URL( base, validationLocation);
				} else {
					schemaURL = URLUtilities.toURL( validationLocation);
				}
			} catch ( MalformedURLException e) {
				if ( base == null) {
					MessageHandler.showError( "Could not resolve relative Location : "+validationLocation, e, "URL Error");
				} else {
					MessageHandler.showUnexpectedError( e);
				}
			}
		} else if ( schemaLocation != null && schemaLocation.trim().length() > 1) {
			URL base = null;

			if ( document != null) {
				base = document.getURL();
			}

			try {
				if ( base != null) {
					schemaURL = new URL( base, schemaLocation);
				} else {
					schemaURL = URLUtilities.toURL( schemaLocation);
				}
			} catch ( MalformedURLException e) {
				if ( base == null) {
					MessageHandler.showError( "Could not resolve relative Location : "+schemaLocation, e, "URL Error");
				} else {
					MessageHandler.showUnexpectedError( e);
				}
			}
		} else if ( document != null && properties.useInternalSchema()) {
			URL base = document.getURL();
			int type = document.getInternalGrammarType();
			schemaType = type;

			if ( type == XMLGrammar.TYPE_XSD) {
				schemaURL = document.getSchemaURL();
			} else if ( type == XMLGrammar.TYPE_DTD) {
				String systemId = document.getSystemID();
				String publicId = document.getPublicID();

				// Try grammar type resolution first, then fall back to relative path resolution
				String resolvedLocation = com.dogsbay.xml.XMLUtilities.resolveWithGrammarTypes( publicId, systemId, properties);

				try {
					// If grammar type resolved to a different location, use it
					if ( resolvedLocation != null && !resolvedLocation.equals( systemId)) {
						schemaURL = URLUtilities.toURL( resolvedLocation);
					} else if ( base != null) {
						schemaURL = new URL( base, systemId);
					} else {
						schemaURL = new URL( systemId);
					}
				} catch ( MalformedURLException e) {
					//e.printStackTrace();
//					if ( base == null) {
//						MessageHandler.showError( "Could not resolve relative Location : "+publicId, e, "URL Error");
//					} else {
//						MessageHandler.showUnexpectedError( e);
//					}
				}
			}

			report = false;
		}

		if ( schemaURL != null) {
			tagCompletionSchema = createTagCompletionSchema( schemaURL, schemaType, report);
		}

		return tagCompletionSchema;
	}

	public static SchemaDocument createTagCompletionSchema( URL schemaURL, int schemaType, boolean report) {
		SchemaDocument tagCompletionSchema = null;
		
		if ( schemaURL != null) {
			if ( schemaType == XMLGrammar.TYPE_DTD) {
				try {
					tagCompletionSchema = new DTDDocument( schemaURL);
				} catch ( XNIException e) {
					if ( report) {
						MessageHandler.showError( "Could not load DTD "+schemaURL.getFile(), e, "DTD Error");
					}
				} catch ( IOException e) {
					if ( report) {
						MessageHandler.showError( "Could not load DTD "+schemaURL.getFile(), e, "DTD Error");
					}
				}
			} else if ( schemaType == XMLGrammar.TYPE_RNG || schemaType == XMLGrammar.TYPE_RNC) {
				try {
					tagCompletionSchema = new RNGDocument( schemaURL, schemaType == XMLGrammar.TYPE_RNC);
				} catch ( SAXException e) {
					if ( report) {
						MessageHandler.showError( "Could not load RelaxNG "+schemaURL.getFile(), e, "RelaxNG Error");
					}
				} catch ( IOException e) {
					if ( report) {
						MessageHandler.showError( "Could not load RelaxNG "+schemaURL.getFile(), e, "RelaxNG Error");
					}
				}
			}
		}

		return tagCompletionSchema;
	}

	/**
	 * Creates a new DTD object.
	 *
	 * @param base the base url for the DTD, should not need a base!
	 * @param props the grammar-type.
	 *
	 * @return the dtd.
	 */
//	public static DTDDocument createDTD( DogsBayDocument document, GrammarProperties props) {
//		DTDDocument dtd = null;
//		String template = null;
//		URL dtdURL = null;
//		
//		if ( props != null) {
//			Vector list = props.getTagCompletionPropertiesList();
//			
//			if ( list.size() > 0) {
//				template = ((TagCompletionProperties)list.elementAt(0)).getLocation();
//			}
//		}
//		
//		if ( template != null && template.trim().length() > 1) {
//			dtd = createDTD( document, template, true);
//		} else if ( document != null && properties.useInternalSchema()) {
//			dtd = createDTD( document, document.getSystemID(), true);
//		}
//
//		return dtd;
//	}


	/**
	 * Creates a new DTD object.
	 *
	 * @param base the base url for the DTD, should not need a base!
	 * @param props the grammar-type.
	 *
	 * @return the dtd.
	 */
	public static DTDDocument createDTD( DogsBayDocument document, String systemId, boolean report) {
		DTDDocument dtd = null;
		URL dtdURL = null;
		
		if ( systemId != null && systemId.trim().length() > 1) {
			URL base = null;

			if ( document != null) {
				base = document.getURL();
			}

			try {
				if ( base != null) {
					dtdURL = new URL( base, systemId);
				} else {
					dtdURL = URLUtilities.toURL( systemId);
				}
			} catch ( MalformedURLException e) {
				if ( report) {
					if ( base == null) {
						MessageHandler.showError( "Could not resolve relative Location : "+systemId, e, "URL Error");
					} else {
						MessageHandler.showUnexpectedError( e);
					}
				}
			}
		} 
		
		if ( dtdURL != null) {
			try {
				dtd = new DTDDocument( dtdURL);
			} catch ( XNIException e) {
				if (report) {
					MessageHandler.showError( "Could not load DTD "+dtdURL.getFile(), e, "DTD Error");
				}
			} catch ( IOException e) {
				if (report) {
					MessageHandler.showError( "Could not load DTD "+dtdURL.getFile(), e, "DTD Error");
				}
			}
		}
		
		return dtd;
	}


	/**
	 * Creates a new Relax object.
	 *
	 * @param base the base url for the DTD, should not need a base!
	 * @param props the grammar-type.
	 *
	 * @return the dtd.
	 */
//	public static RNGDocument createRelax( DogsBayDocument document, GrammarProperties props) {
//		RNGDocument relax = null;
//		String template = null;
//		URL dtdURL = null;
//		boolean compact = false;
//		
//		if ( props != null) {
//			Vector list = props.getTagCompletionPropertiesList();
//			
//			if ( list.size() > 0) {
//				compact = ((TagCompletionProperties)list.elementAt(0)).getType() == XMLGrammar.TYPE_RNC;
//				template = ((TagCompletionProperties)list.elementAt(0)).getLocation();
//			}
//		}
//
//		if ( template != null && template.trim().length() > 1) {
//			relax = createRelax( document, template, compact);
//		}
//
//		return relax;
//	}


	/**
	 * Creates a new DTD object.
	 *
	 * @param base the base url for the DTD, should not need a base!
	 * @param props the grammar-type.
	 *
	 * @return the dtd.
	 */
	public static RNGDocument createRelax( DogsBayDocument document, String systemId, boolean compact) {
		RNGDocument relax = null;
		URL relaxURL = null;
		
		if ( systemId != null && systemId.trim().length() > 1) {
			URL base = null;

			if ( document != null) {
				base = document.getURL();
			}

			try {
				if ( base != null) {
					relaxURL = new URL( base, systemId);
				} else {
					relaxURL = URLUtilities.toURL( systemId);
				}
			} catch ( MalformedURLException e) {
				if ( base == null) {
					MessageHandler.showError( "Could not resolve relative Location : "+systemId, e, "URL Error");
				} else {
					MessageHandler.showUnexpectedError( e);
				}
			}
		} 
		
		if ( relaxURL != null) {
			try {
				relax = new RNGDocument( relaxURL, compact);
			} catch ( SAXException e) {
				MessageHandler.showError( "Could not load RelaxNG "+relaxURL.getFile(), e, "RelaxNG Error");
			} catch ( IOException e) {
				MessageHandler.showError( "Could not load RelaxNG "+relaxURL.getFile(), e, "RelaxNG Error");
			}
		}
		
		return relax;
	}

	/**
	 * Gets the selected grammar properties for the chooser.
	 *
	 * @param chooser the file choose.
	 *
	 * @return the selected properties.
	 */
	public static GrammarProperties getSelectedGrammar( JFileChooser chooser) {
		FileFilter filter = chooser.getFileFilter();
	
		if ( filter instanceof DefaultFileFilter) { 
			// remove previous filters...
			// Use the current selected type...
			Vector grammars = properties.getGrammarProperties();
			for ( int i = 0; i < grammars.size(); i++) {
				GrammarProperties grammar = (GrammarProperties)grammars.elementAt( i);
				DefaultFileFilter compFilter = new DefaultFileFilter( grammar.getExtensions(), grammar.getDescription());
				
				if ( filter.getDescription().equals( compFilter.getDescription())) {
					properties.setLastOpenedGrammar( i);
					return grammar;
				}
			}

			return null;
			
		} else { 
			properties.setLastOpenedGrammar( -1);
			return null;
		}
	}
	
	/**
	 * Create a new document from the schema, grammar and url.
	 *
	 * @param url the url for the document.
	 * @param schema the schema used as a template.
	 * @param type the type of document.
	 *
	 * @return the new document.
	 */
	public static DogsBayDocument createDocument( URL url, GrammarProperties type) {
		DogsBayDocument document = null;
		XElement root = null;
		
		if ( type != null) {
			root = new XElement( type.getRootElementName(), type.getNamespace());
		} 
		
		if ( root == null) {
			root = new XElement( "dogsbay");
		}

		root.setText( "\n");

		document = new DogsBayDocument( url, (XElement)root);
//		document.setGrammar( type);

		return document;
	}
	
	/**
	 * Create a new document from a Grammar type.
	 *
	 * @param type the type of document.
	 *
	 * @return the new document.
	 */
	public static DogsBayDocument createDocument( GrammarProperties type) throws IOException, SAXParseException {
		XElement root = null;
		DogsBayDocument document = null;
		
		if ( type != null) {
			String prefix = type.getNamespacePrefix();
			
			if ( !StringUtilities.isEmpty( prefix)) {
				root = new XElement( type.getRootElementName(), type.getNamespace(), type.getNamespacePrefix());
			} else {
				root = new XElement( type.getRootElementName(), type.getNamespace());
			}

			Vector namespaces = type.getNamespaces();
			for ( int i = 0; i < namespaces.size(); i++) {
				NamespaceProperties namespace = (NamespaceProperties)namespaces.elementAt(i);
				root.addNamespace( namespace.getPrefix(), namespace.getURI());
			}
			
			document = new DogsBayDocument( (XElement)root);
			root = document.getRoot();
			
			String publicID = type.getPublicID();
			String validationLocation = type.getSystemID();
			
			if ( StringUtilities.isEmpty( validationLocation)) {
				validationLocation = type.getValidationLocation();
			}
			
			if ( type.getValidationGrammar() == XMLGrammar.TYPE_DTD && !StringUtilities.isEmpty( validationLocation)) {
				if ( !StringUtilities.isEmpty( publicID)) {
					document.getDocument().setDocType( new DefaultDocumentType( type.getRootElementName(), publicID, validationLocation));
				} else {
					document.getDocument().setDocType( new DefaultDocumentType( type.getRootElementName(), validationLocation));
				}
			} else if ( type.getValidationGrammar() == XMLGrammar.TYPE_XSD && !StringUtilities.isEmpty( validationLocation)) {
				validationLocation = URLUtilities.encodeURL( validationLocation);

				Namespace xsiNamespace = root.getNamespaceForURI( "http://www.w3.org/2001/XMLSchema-instance");
				if ( xsiNamespace == null) {
					xsiNamespace = new DefaultNamespace( "xsi", "http://www.w3.org/2001/XMLSchema-instance");
					root.add( xsiNamespace);
				}
				
				String namespace = type.getNamespace();
				if ( !StringUtilities.isEmpty( namespace)) {
					root.addAttribute( new QName( "schemaLocation", xsiNamespace), namespace+" "+validationLocation);
				} else {
					root.addAttribute( new QName( "noNamespaceSchemaLocation", xsiNamespace), validationLocation);
				}
			}

		} else {
			root = new XElement( "dogsbay");
		} 
		
		root.setText( "\n");
		
		if ( document == null) {
			document = new DogsBayDocument( (XElement)root);
		}

		document.update();
		return document;
	}
	
	public static String replace( String text, String pattern, String replacement) {
	    StringBuffer buffer = new StringBuffer();
	    int prev = 0;
	    int next = text.indexOf( pattern, prev);

	    while ( next != -1) {
	        buffer.append( text.substring( prev, next));
	        buffer.append( replacement);
	        
	        prev = next + pattern.length();
	        next = text.indexOf( pattern, prev);
	    }
	    
	    buffer.append( text.substring( prev, text.length()));
	    
	    return buffer.toString();
	}

	/**
	 * Create a new document from the template url.
	 *
	 * @param schema the schema used as a template.
	 * @param type the type of document.
	 *
	 * @return the new document.
	 */
	private static DogsBayDocument createDocument( URL template) throws IOException {
		DogsBayDocument document = null;
		XElement root = null;
		
		try {
			if ( template != null) {
				document = new DogsBayDocument( template);
				document.load();
				document.setURL( null);
			} else {
				document = createDocument();
			} 
		} catch (SAXParseException e) {
			// ignore...
		}
		
		return document;
	}

	public static DogsBayDocument createDocument() throws IOException, SAXParseException {
		DogsBayDocument document = null;
		XElement root = new XElement( "dogsbay");
		root.setText( "\n");

		document = new DogsBayDocument( (XElement)root);

		return document;
	}

	/**
	 * Returns the first extension string of the properties.
	 *
	 * @param props the grammar-type.
	 *
	 * @return the extension.
	 */
	public static String getExtension( GrammarProperties props) {
		String extension = null;
		StringTokenizer tokenizer = new StringTokenizer( props.getExtensions(), " \t\n\r\f;,.:");
		
		if ( tokenizer.hasMoreTokens()) {
			extension = tokenizer.nextToken();
		}
		
		return extension;
	}
	
	/**
	 * Returns the list of extensions for the type.
	 *
	 * @param props the grammar-type.
	 *
	 * @return the extensions.
	 */
	public static Vector getExtensions( GrammarProperties props) {
		Vector extensions = new Vector();
		StringTokenizer tokenizer = new StringTokenizer( props.getExtensions(), " \t\n\r\f;,.:");
		
		while ( tokenizer.hasMoreTokens()) {
			extensions.addElement( tokenizer.nextToken());
		}
		
		return extensions;
	}

	/**
	 * Returns the correct type for the document.
	 *
	 * @param document the document.
	 * @param type the grammar-type.
	 *
	 * @return the selected type.
	 */
	public static GrammarProperties getType( DogsBayDocument document, GrammarProperties type) {
//		if ( (document == null || document.isError())) {
//			if ( type == null) {
//				int val = JOptionPane.showOptionDialog( parent,
//								    "The XML Document cannot be Parsed,\n"+
//									"do you want to Select a Type for the document?",
//								    "Document Parsing Error",
//								    JOptionPane.YES_NO_OPTION,
//									JOptionPane.ERROR_MESSAGE,
//									null,
//									new String[] {"Select Type", "Continue Without"},
//									"Continue Without");
//				if ( val == JOptionPane.YES_OPTION) {
//					// Let user select/create a different type.
//					GrammarSelectionDialog dialog = getGrammarSelectionDialog();
//					dialog.setProperties( properties.getGrammarProperties());
//					dialog.setVisible( true);
//
//					if ( !dialog.isCancelled()) {
//						type = dialog.getSelectedType();
//					}
//				}
//			}
//		} else {

		if ( document != null && !document.isError() && document.isXML()) {
			if ( !isDocumentOfType( document, type)) { // check if the document is of the selected type, use the selected type...
				Vector props = getTypes( document);

				if ( props.size() > 0) { // a type exists for this document 

				    if ( props.size() > 1) { 
						// if more than one type exist, allow for selection + creation?
						GrammarSelectionDialog dialog = getGrammarSelectionDialog();
						dialog.setProperties( props);
						//dialog.setVisible( true);
						dialog.setVisible(true);

						if ( !dialog.isCancelled()) {
							type = dialog.getSelectedType();
						}
				    } else {
						// use the type...
					    type = (GrammarProperties)props.elementAt(0);
				    }
			    } else if ( properties.isPromptCreateTypeOnOpening()) { // a type does not exist for the document
					// ask first if the user wants to create a new type...
	//							System.out.println("no type");
		 			int val = JOptionPane.showOptionDialog( parent,
									    "The XML Document is not of a known Type,\n"+
										"do you want to create a new Type?",
									    "XML Type not Found",
									    JOptionPane.YES_NO_OPTION,
										JOptionPane.QUESTION_MESSAGE,
										null,
										new String[] {"Create Type", "Continue Without"},
										"Continue Without");

					if ( val == JOptionPane.YES_OPTION) {
					    // bring up a type creation dialog and let the user 
						// create a type for this document...
						GrammarPropertiesDialog dialog = getGrammarPropertiesDialog( "Create", true);
						GrammarProperties grammar = new GrammarProperties( properties, document);

						Vector gs = properties.getGrammarProperties();
						Vector names = new Vector();
				
						for ( int i = 0; i < gs.size(); i++) {
							String name = ((GrammarProperties)gs.elementAt( i)).getDescription();
							
							names.addElement( name);
						}

						dialog.show( grammar, true, names);

						if ( !dialog.isCancelled()) {
							boolean grammarUpdated = false;
							
							for ( int i = 0; i < gs.size(); i++) {
								String name = ((GrammarProperties)gs.elementAt( i)).getDescription();
				
								if ( name.equals( grammar.getDescription())) {
									GrammarProperties oldGrammar = (GrammarProperties)gs.elementAt( i);
									oldGrammar.update( grammar);
									type = oldGrammar;
									grammarUpdated = true;
									break;
								}
							}
							
							if ( !grammarUpdated) {
								properties.addGrammarProperties( grammar);
								type = grammar;
							}

							properties.save();
						} else {
							type = null;
						}
					}
			    }
			}
		}
		
		return type;
	}

//	public static String selectURL( String title) {
//		URLChooserDialog chooser = getURLChooser();
//		chooser.setTitle( title);
//		chooser.setVisible(true);
//		
//		if ( !chooser.isCancelled()) {
//			URL url = chooser.getURL();
//			return url.toString();
//		}
//		
//		return null;
//	}

	public static File selectOutputFile( File file, String extension) {
		return selectOutputFile( parent, file, extension);
	}

	
	public static File selectOutputFile( JFrame frame, File selectedFile, String extension) {
		JFileChooser chooser = null;
		
		if ( selectedFile != null) {
			chooser = getFileChooser( selectedFile);
		} else {
			chooser = getCurrentFileChooser();
		}
		
		return selectOutputFile( frame, chooser, extension);
	}
		
	/**
	 * Select an output file. Handles overwrite messages and readonly stuff!
	 *
	 * @param selectedFile the File selected in the File chooser.
	 * @param extension the extension for the File if no extension has been chosen.
	 *
	 * @return the file or null if no selection made.
	 */
	public static File selectOutputFile( JFileChooser chooser, String extension) {
		return selectOutputFile( parent, chooser, extension);
	}

	/**
	 * Select an output file. Handles overwrite messages and readonly stuff!
	 *
	 * @param selectedFile the File selected in the File chooser.
	 * @param extension the extension for the File if no extension has been chosen.
	 *
	 * @return the file or null if no selection made.
	 */
	private static File selectOutputFile( JFrame frame, JFileChooser chooser, String extension) {
		File file = null;
		String originalExtension = extension;

		while ( file == null) {
			int value = chooser.showSaveDialog( frame);

			if ( value == JFileChooser.APPROVE_OPTION) {
				file = chooser.getSelectedFile();
				
				if ( originalExtension == null) {
					FileFilter filter = chooser.getFileFilter();

					if ( filter instanceof DefaultFileFilter) {
						Vector extensions = ((DefaultFileFilter)filter).getExtensions();
						
						if ( extensions != null && extensions.size() > 0) {
							extension = (String)extensions.elementAt(0);
						}
					}
				}
				
				String path = file.getPath();

				if ( path.indexOf( ".") == -1 && extension != null) {
					file = new File( path+"."+extension);
				} else {
					file = new File( path);
				}

				if ( file.exists()) {
					int result = JOptionPane.OK_OPTION;

				 	if ( !file.canWrite()) {
						result = MessageHandler.showConfirmOkCancel( frame, 
											"The document \""+file.getName()+"\" already exists and is read-only.\n"+
						 					"Please save the document with another name.");

						if ( result != JOptionPane.OK_OPTION) {
							return null;
						}
					} else {
						// show a dialog asking if it is okay to overwrite the existing file
						result = MessageHandler.showConfirmCancel( frame, 
											"The document \""+file.getName()+"\" already exists,\n"+
						 					"do you want to replace the existing document?");

						if ( result == JOptionPane.YES_OPTION) {
							return file;
						} else if ( result == JOptionPane.CANCEL_OPTION) {
							return null;
						}
					}

					file = null;
				}
			} else {
				return null;
			}
		}
		
		return file;			
	}

	public static String getRelaxNGVersion() {
//		InputStream in = cls.getResourceAsStream("resources/Version.properties");
//		if (in != null) {
//		  Properties props = new Properties();
//		  try {
//				props.load(in);
//				String version = props.getProperty("version");
//
//				if (version != null)
//					return version;
//				}
//		  catch (IOException e) { }
//		}

		return "Jing "+com.thaiopensource.util.Version.getVersion( com.thaiopensource.relaxng.util.ValidationEngine.class);
	}

	public static String getXercesVersion() {
		return org.apache.xerces.impl.Version.getVersion();
	}

	public static String getProcessorVersion() {
		String processor = System.getProperty( "javax.xml.transform.TransformerFactory");
		
		if ( processor.equals( ConfigurationProperties.XSLT_PROCESSOR_SAXON_XSLT2)) {
			String processorVersion = net.sf.saxon.Version.getProductTitle();
				
			try {
				Class versionClass = ((DogsBayAIEditor)editor).getClassLoader().loadClass("net.sf.saxon.Version");
				Method versionProductVersionMethod = versionClass.getMethod("getProductVersion", null);
				if(versionProductVersionMethod != null) {
					Object result = versionProductVersionMethod.invoke(versionClass, new Object[0]);
					//System.out.println("result: "+result);
					if(result != null) {
						processorVersion = (String)result;
					}
				}
				
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
						
			//String name2 = net.sf.saxon.Version.getProductName() + ": "+net.sf.saxon.Version.getProductVersion();
			//System.out.println("XSLTProcessorDialog::versions \n1: "+name1+"\n2: "+name2);
			
			return(processorVersion);
		}
		
		return "";
	}

	public static String getXQueryProcessorVersion() {
		return net.sf.saxon.Version.getProductTitle();
	}

	private static org.apache.fop.apps.Options options = null;

	public static String getFOVersion() {
		if ( options == null) {
			try {
				options = new org.apache.fop.apps.Options();
			} catch ( Exception e) {
			}
		}
		
		return org.apache.fop.apps.Version.getVersion();
	}

//	public static int convertFormat( int format) {
//		int result = TransformerUtilities.FOP_PDF;
//		
//		switch (format) {
//			case ScenarioProperties.FOP_TYPE_PS:
//				result = TransformerUtilities.FOP_PS;
//				break;
//			case ScenarioProperties.FOP_TYPE_SVG:
//				result = TransformerUtilities.FOP_SVG;
//				break;
//			case ScenarioProperties.FOP_TYPE_TXT:
//				result = TransformerUtilities.FOP_TXT;
//				break;
//		}
//		if (DEBUG) System.out.println( "FileUtilities.convertFormat( "+format+") ["+result+"]");
//		return result;
//	}
	
	private static class DefaultFileView extends FileView {
		public Icon getIcon( File file) {
			Icon icon = null;
			
			if ( !file.isDirectory()) {
//		        icon = IconFactory.getDirectoryIcon();
//			} else {
			    String name = file.getName();
			
			    int pos = name.lastIndexOf( ".");

			    if ( pos != -1 && ((pos+1) < name.length())) {
                    String extension = name.substring( pos+1, name.length());
				    icon = IconFactory.getIconForExtension( extension);
			    }
			
			    if ( icon == null) {
				    icon = IconFactory.getDocumentIcon();
			    }
			}
			
			return icon;
		}
	}

	public static String getSchematronVersion() {
		return("1.5");
	}
}
