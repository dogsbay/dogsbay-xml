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

import javax.swing.AbstractAction;
import com.dogsbay.xml.transform.ScenarioUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;
import com.dogsbay.dogsbayaieditor.XMLDocumentChooserDialog;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.transform.XSLTProcessorDialog;
/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/06/01 15:15:34 $
 * @author Dogsbay
 */
public class ExecuteSimpleXSLTAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private DogsBayAIEditor parent = null;
	//private ExecuteXSLTDialog dialog = null;
	private XMLDocumentChooserDialog chooserXSL = null;
	private XMLDocumentChooserDialog chooserXML = null;
	private XSLTProcessorDialog processorDialog = null;
	
 	/**
	 * The constructor for the action which allows opening 
	 * of XML Documents.
	 *
	 * @param parent the parent frame.
	 */
 	public ExecuteSimpleXSLTAction( DogsBayAIEditor parent) {
 		super( "Execute Simple XSLT ...");

		this.parent = parent;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'S'));
		putValue( SHORT_DESCRIPTION, "Execute a Simple XSLT Transformation");
 	}
 	
 	/**
 	 * The implementation of the execute XSLT action.
 	 *
 	 * @param the action event.
 	 */
 	public void actionPerformed( ActionEvent e) {
		
		DogsBayView view = parent.getView();
		String xslUrl = null;
		String xmlUrl = null;
		
		if ( view != null) {
			view.updateModel();
		}

		DogsBayDocument document = parent.getDocument();
		DogsBayDocument xslDocument = parent.getDocument();

		if ( chooserXML == null) {
		  chooserXML = new XMLDocumentChooserDialog( parent,  "Select XML Input", "Specify XML Input Document", parent, false);
		}

		if ( document != null) {
		  chooserXML.show( document.isXML());
		} else{
		  chooserXML.show( false);
		}
		
		if ( !chooserXML.isCancelled()) {
			try {
				if ( chooserXML.isOpenDocument()) {				  
				  document = chooserXML.getOpenDocument();	
				  xmlUrl = document.getURL().toString();
				}  
				else if ( !chooserXML.isCurrentDocument()) {
					xmlUrl = chooserXML.getInputLocation();

				}

				
			} 
			catch (Exception ex) {}
//			catch ( IOException x) {
//				MessageHandler.showError( "Could not create the Document:\n"+chooserXML.getInputLocation(), "Document Error");
//			} 
//			catch ( SAXParseException x) {
//				MessageHandler.showError( "Could not parse the Document.", x, "Document Error");
//			}
			
			if ( chooserXSL == null) {
			  chooserXSL = new XMLDocumentChooserDialog( parent,  "Select XSL Input", "Specify XSL Stylesheet", parent, false);
			}

			if ( xslDocument != null) {
			  chooserXSL.show( xslDocument.isXSL());
			} else{
			  chooserXSL.show( false);
			}
			
			if ( !chooserXSL.isCancelled()) {
				try {
					if ( chooserXSL.isOpenDocument()) {				  
					  xslDocument = chooserXSL.getOpenDocument();	
					  xslUrl = xslDocument.getURL().toString();
					}  
					else if ( !chooserXSL.isCurrentDocument()) {
						xslUrl =  chooserXSL.getInputLocation();

					}

					
				} 
				catch (Exception ex) {}
//					catch ( IOException x) {
//					MessageHandler.showError( "Could not create the Document:\n"+chooserXSL.getInputLocation(), "Document Error");
//				} 
//				catch ( SAXParseException x) {
//					MessageHandler.showError( "Could not parse the Document.", x, "Document Error");
//				}
				
	
				if ( processorDialog == null) {
				  processorDialog = new XSLTProcessorDialog( parent);
				}
				
				processorDialog.setProcessor(ScenarioProperties.PROCESSOR_DEFAULT);
				processorDialog.setVisible(true);
				
				if (processorDialog.isCancelled())
					return;
				
				ScenarioProperties scenario = new ScenarioProperties();
				  if (chooserXML.isCurrentDocument())
				    scenario.setInputType(ScenarioProperties.INPUT_CURRENT_DOCUMENT);
				  else
				  {
				    scenario.setInputType(ScenarioProperties.INPUT_FROM_URL);
				    scenario.setInputFile(xmlUrl);
				  }

				  if (chooserXSL.isCurrentDocument())
				    scenario.setXSLType(ScenarioProperties.XSL_CURRENT_DOCUMENT);
				  else
				  {
				    scenario.setXSLType(ScenarioProperties.XSL_FROM_URL);
				    scenario.setXSLURL(xslUrl);
				  }

				  scenario.setOutputType(ScenarioProperties.OUTPUT_TO_NEW_DOCUMENT);

				  
				  scenario.setXSLEnabled(true);

				  scenario.setProcessor(processorDialog.getProcessor());
				
				parent.getExecutePreviousXSLTAction().setScenario( scenario);
				ScenarioUtilities.execute( parent.getDocument(), scenario);
				
				
				
			}
			
			
	
			
		}
		
	}
}
