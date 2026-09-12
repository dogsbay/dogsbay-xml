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

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;

import javax.swing.AbstractAction;

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.tree.FlyweightProcessingInstruction;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.transform.TransformerUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * An action that can be used to open the current document in a browser.
 *
 * @version	$Revision: 1.14 $, $Date: 2004/09/06 14:47:24 $
 * @author Dogsbay
 */
public class OpenBrowserAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
 	
 	private String XML_STYLESHEET = "xml-stylesheet";
	
 	/**
	 * The constructor for the copy action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public OpenBrowserAction( DogsBayAIEditor parent) {
 		super( "Start Browser");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'B'));
		putValue( SHORT_DESCRIPTION, "Start the default internet browser");		
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F9, 0, false));

		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		DogsBayDocument document = parent.getDocument();
		
		parent.getView().updateModel();
		
		if ( !document.isError()) {
			XElement root = document.getRoot();
			
			if ( root.getName().equalsIgnoreCase( "html") || document.getName().toLowerCase().endsWith( "htm") || document.getName().toLowerCase().endsWith( "html")) {
				writeOutputAsHTML( document);
			} else { 
			    //need to check if the XML has a stylesheet PI (css/xslt)
			    if(!checkForStylesheetPI(document)) {
			        
			        // normal XML, use the default stylesheet to convert to html
			        try {
			            File temp = File.createTempFile(
			                "temp" + URLUtilities.getFileNameWithoutExtension( document.getName()),
			                ".htm",
			                new File( System.getProperty( "java.io.tmpdir")));

			            temp.deleteOnExit();

			            FileOutputStream stream = new FileOutputStream( temp);

			            TransformerUtilities.transform( document, stream, false);
			            stream.flush();
			            stream.close();

			            Desktop.getDesktop().browse( temp.toURI());
			        } catch ( Exception x) {
			            x.printStackTrace();
			        }
			    }
			    else {
			        //document has a processing instruction specifying a stylesheet,
			        //just show as is
			        writeOutputAsHTML(document);
//			      
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
		try {
			URL url = document.getURL();

			// If saved local file, open it directly
			if ( url != null && url.getProtocol().equals( "file") && !document.hasChangedOnDisk()) {
				Desktop.getDesktop().browse( new File( url.getFile()).toURI());
				return;
			}

			// Unsaved or remote — write to temp file
			File temp = File.createTempFile(
				"temp" + URLUtilities.getFileNameWithoutExtension( document.getName()),
				".htm",
				new File( System.getProperty( "java.io.tmpdir")));

			temp.deleteOnExit();

			FileOutputStream stream = new FileOutputStream( temp);
			stream.write( document.getText().getBytes( document.getJavaEncoding()));
			stream.flush();
			stream.close();

			Desktop.getDesktop().browse( temp.toURI());
		} catch ( Exception e) {
			e.printStackTrace();
		}
 	}
 	
 	/**
 	 * Method to check if the document contains a processing intruction
 	 * which specifies a stylesheet. If it cannot find one declared at the beginning
 	 * it will walk the tree to see can it find one.
 	 * 
 	 * @param document
 	 * @return boolean true or false
 	 */
 	public boolean checkForStylesheetPI(DogsBayDocument document) {
 	   XDocument doc = document.getDocument();
 	   for(int cnt=0;cnt<doc.nodeCount();++cnt) {
 	       Node n = doc.node(cnt);
 	       if(n.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE) {
 	           FlyweightProcessingInstruction pi = (FlyweightProcessingInstruction)n;
 	           if(pi.getTarget().equalsIgnoreCase(XML_STYLESHEET)) {
 	              return(true); 
 	           }
 	       }
 	       
 	   }
 	  return(treeWalk(document.getRoot()));
 	   
 	}
 	
 	/**
 	 * walks the tree to see if it can find a processing instruction 
 	 * relating to a stylesheet
 	 * @param element
 	 * @return boolean true or false
 	 */
 	public boolean treeWalk(XElement element) {
        for ( int i = 0, size = element.nodeCount(); i < size; i++ ) {
            Node node = element.node(i);
            if ( node instanceof Element ) {
                treeWalk( (XElement) node );
            }
            else {
                
                if(node.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE) {
      	           FlyweightProcessingInstruction pi = (FlyweightProcessingInstruction)node;
      	           if(pi.getTarget().equalsIgnoreCase(XML_STYLESHEET)) {
      	              return(true);
      	           }
      	       }
            }
        }
        return(false);
    }
 	
 	 	 
}
