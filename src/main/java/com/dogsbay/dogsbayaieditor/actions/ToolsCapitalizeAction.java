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
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ToolsCapitalizeDialog;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to capitalize elements or attributes.
 *
 * @version	$Revision: 1.16 $, $Date: 2004/10/27 17:03:51 $
 */
public class ToolsCapitalizeAction extends AbstractAction {
    
    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private ToolsCapitalizeDialog dialog = null;
    private Editor editor = null;
    private ConfigurationProperties props;
    
    /**
     * The constructor for the action which allows capitalizint of elements or attributes
     *
     * @param parent the parent frame.
     */
    public ToolsCapitalizeAction( DogsBayAIEditor parent, Editor editor, ConfigurationProperties props) {
        super( "Capitalize Elements and Attributes ...");
        
        this.parent = parent;
        this.props = props;
        
        putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
        putValue( SHORT_DESCRIPTION, "Capitalize Elements and Attributes ...");
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
    
    
    /**
     * The implementation of the validate action, called 
     * after a user action.
     *
     * @param event the action event.
     */
    public void actionPerformed( ActionEvent event) {
        if ( dialog == null) {
            dialog = new ToolsCapitalizeDialog( parent,props);
        }
        
        //called to make sure that the model is up to date to 
        //prevent any problems found when undo-ing etc.
        parent.getView().updateModel();
        
        //get the document
        final DogsBayDocument document = parent.getDocument();
        
        if ( document.isError()) {
			MessageHandler.showError( parent,"Please make sure the document is well-formed.", "Parser Error");
			return;
		}
        String currentXPath = null;
        Node node = (Node)document.getLastNode( parent.getView().getEditor().getCursorPosition(), true);

        if ( props.isUniqueXPath()) {
            currentXPath = node.getUniquePath();
        } else {
            currentXPath = node.getPath();
        }
        dialog.show(currentXPath);
        
        if(!dialog.isCancelled()) {
            	
            	parent.setWait( true);
        	 	parent.setStatus( "Changing Capitals ...");

    	 		// Run in Thread!!!
    	 		Runnable runner = new Runnable() {
    	 			public void run()  {
    			 		try {
    			
    			           if((dialog.elementsRadio.isSelected())||(dialog.attributeRadio.isSelected())||
    			                   (dialog.elementsAndAttributesRadio.isSelected())) {
	    			            String newDocument = null;
	    	                    DogsBayDocument tempDoc =  new DogsBayDocument(document.getText());
	
	    	                    boolean TRAVERSE_CHILDREN = true;
	    	                    
	    	                    if(dialog.xpathPanel.xpathBox.isSelected()) {
			                        String xpathPredicate = dialog.xpathPanel.getXpathPredicate();
			                        newDocument = ToolsCapitalizeAction.this.capitalize(tempDoc,xpathPredicate,
			                                dialog.elementsRadio.isSelected(),
			                                dialog.attributeRadio.isSelected(),
			                                dialog.elementsAndAttributesRadio.isSelected(),
			                                TRAVERSE_CHILDREN);
			                        
			                    }
			                    else {
			                        
			                        //set the string to the new capitalize document
			                        newDocument = ToolsCapitalizeAction.this.capitalize(tempDoc,
			                                dialog.elementsRadio.isSelected(),
			                                dialog.attributeRadio.isSelected(),
			                                dialog.elementsAndAttributesRadio.isSelected());
			                    }
			                    if(newDocument!=null) {
				                    if(dialog.toNewDocumentRadio.isSelected()) {
				                        //user has selected to create the result as a new document
				                        parent.open( new DogsBayDocument(newDocument), null);
				                    }
				                    else {
				                        parent.getView().getEditor().setText(newDocument);
	
				                        SwingUtilities.invokeLater(new Runnable() {
				                			public void run() {
						                        parent.switchToEditor();
						                        
						                        parent.getView().updateModel();
				                			}
				                		});
				                    }
			                    }
    			           }
    			           
    			 		} catch ( Exception e) {
    			 			// This should never happen, just report and continue
    	                    MessageHandler.showError( parent, "Cannot Capitalize Document","Tools Capitalize Error");
    			 		} finally {
    				 		parent.setStatus( "Done");
    				 		parent.setWait( false);
    			 		}
    	 			}
    	 		};
    	 		
    	 		// Create and start the thread ...
    	 		Thread thread = new Thread( runner);
    	 		thread.start();
//            }
        }
    }
    
    private String capitalize(DogsBayDocument document, boolean capitalizeElements, 
            boolean capitalizeAttributes, boolean capitalizeElementsAndAttributes) {
        
        try {
            XElement root = document.getRoot();
            
            if(((capitalizeAttributes)||(capitalizeElementsAndAttributes))&&(root.attributeCount()>0)) {
                root.setAttributes(this.capitalizeAttributes(root));
                
            }
            //capitalize the element
            if(((capitalizeElements)||(capitalizeElementsAndAttributes))&&(root.getName()!=null)) {
                
                String name = root.getName();
                name = capitalizeString(name);
                Namespace ns = root.getNamespace();
                
                root.setQName(new QName(name,ns));
            }
            
            //then capitalize its children 	  
            iterateTree(root,capitalizeElements,capitalizeAttributes,capitalizeElementsAndAttributes);
            
            document.update();
        }
        catch (NullPointerException e) {
            MessageHandler.showError(parent,"Error - Cannot Capitalize,\nElements or Attributes not found","Tools Capitalize Error");
            return(null);
        }
        catch (Exception e) {
            MessageHandler.showError(parent,"Error - Cannot Capitalize Document","Tools Capitalize Error");
            return(null);
        }
        
        return document.getText();
    }
    
    private String capitalize(DogsBayDocument document, String xpath, boolean capitalizeElements, 
            boolean capitalizeAttributes, boolean capitalizeElementsAndAttributes, boolean traverseChildren) {
        //used for xpath expressions
        try {
            Vector nodes = document.search( xpath);
            if(nodes.size()<1) {
                MessageHandler.showError(parent,"Error - Cannot Resolve XPath","Tools Capitalize Error");
                return(null);
            }
            for(int cnt=0;cnt<nodes.size();++cnt) {
                //for each element
                //Capitalize the attributes
                Node node = (Node)nodes.get(cnt);
                if(node instanceof Element) {
                    XElement root = (XElement)nodes.get(cnt);
                    if((capitalizeAttributes)||(capitalizeElementsAndAttributes)) {
                        
                        if(root.attributeCount()>0) {
                            root.setAttributes(this.capitalizeAttributes(root));
	                    }
	                }
	                //capitalize the element
                    if((capitalizeElements)||(capitalizeElementsAndAttributes)) {
		                if(root.getName()!=null) {
		                    
		                    String name = root.getName();
		                    name = capitalizeString(name);
		                    Namespace ns = root.getNamespace();
		                    
		                    root.setQName(new QName(name,ns));
		                }
                    }
	                if(traverseChildren) {
	                    //then capitalize its children 	       
	                    iterateTree(root,capitalizeElements,capitalizeAttributes,capitalizeElementsAndAttributes);
	                }
                }
                
                else if(((capitalizeAttributes)||(capitalizeElementsAndAttributes))&&(node instanceof Attribute)) {
                    Attribute att = (Attribute)node;
                    node.getParent().setAttributes(capitalizeAttributes((XElement)(node.getParent()),att));
                    
                }
            }
            document.update();
        }catch (NullPointerException e) {
            MessageHandler.showError(parent,"Error - Cannot Capitalize,\nElements or Attributes not found","Tools Capitalize Error");
            return(null);
        }
        catch (Exception e) {
            MessageHandler.showError(parent,"Error - Cannot Capitalize Document","Tools Capitalize Error");
            return(null);
        }
        return document.getText();
    }
    
    private void iterateTree(Element element,boolean capitalizeElements, 
            boolean capitalizeAttributes,boolean capitalizeElementsAndAttributes) throws Exception{
        
        for ( int i = 0, size = element.nodeCount(); i < size; i++ ) {
            Node oldNode = element.node(i);
            if(oldNode instanceof Element) {
                
                XElement oldElement = (XElement)oldNode;
                
                if(((capitalizeAttributes)||(capitalizeElementsAndAttributes))&&(oldElement.attributeCount()>0)) {
                    oldElement.setAttributes(this.capitalizeAttributes(oldElement));
                    
                }
                
                //capitalize the element
                if(((capitalizeElements)||(capitalizeElementsAndAttributes))&&(oldElement.getName()!=null)) {
                    
                    String name = oldElement.getName();
                    name = capitalizeString(name);
                    Namespace ns = oldElement.getNamespace();
                    
                    oldElement.setQName(new QName(name,ns));
                    
                }
                iterateTree(oldElement,capitalizeElements,capitalizeAttributes,capitalizeElementsAndAttributes);
                
            }
        }
    }
    
    private List capitalizeAttributes(XElement root) throws Exception {
        
        int attributeCount = root.attributeCount();
        List attributeList = root.attributes();
        //create an array to hold all the attributes
        Attribute[] attArray = new Attribute[attributeCount];
        
        for(int cnt=0;cnt<attributeCount;++cnt) {
            //add each attribute to the array
            attArray[cnt] = (Attribute)attributeList.get(cnt);
        }
        //work on the array
        for(int cnt=0;cnt<attributeCount;++cnt) {
            String name = attArray[cnt].getName();
            name = capitalizeString( name);
            String value = attArray[cnt].getValue();
            Namespace ns = attArray[cnt].getNamespace();
            
            attArray[cnt] = new XAttribute(new QName( name, ns), value);
        }
        
        //then remove all previous and add all the attributes back into the document
        List newAttributes = Arrays.asList(attArray);
        return(newAttributes);
        
    }
    
    private List capitalizeAttributes(XElement root, Attribute att) throws Exception {
        
        int attributeCount = root.attributeCount();
        List attributeList = root.attributes();
        //create an array to hold all the attributes
        Attribute[] attArray = new Attribute[attributeCount];
        
        for(int cnt=0;cnt<attributeCount;++cnt) {
            //add each attribute to the array
            attArray[cnt] = (Attribute)attributeList.get(cnt);
        }
        //work on the array
        for(int cnt=0;cnt<attributeCount;++cnt) {
            Attribute attOld = attArray[cnt];
            if(attOld==att) {
                String name = attArray[cnt].getName();
                name = capitalizeString( name);
                String value = attArray[cnt].getValue();
                Namespace ns = attArray[cnt].getNamespace();
            
                attArray[cnt] = new XAttribute(new QName( name, ns), value);
            }
        }
        
        //then remove all previous and add all the attributes back into the document
        List newAttributes = Arrays.asList(attArray);
        return(newAttributes);
        
    }
    
    /**
     * Capitalize the first letter found in a string
     * @param value
     * @return the capitalized string
     */
    private String capitalizeString(String value) throws Exception{
        
        String toReturn = null;
        toReturn = value;
        //need to find the first alpha. char and capitalize it
        boolean found = false;
        int charCnt = 0;
        char[] cBuff = value.toCharArray();
        while(!found) {
            char c = cBuff[charCnt];
            //Character cChar = Character.valueOf(c);
            if(Character.isLetter(c)){
                //its a letter
                //found = true
                found = true;
            }
            else
                charCnt++;
        }
        if(found) {
            //capitalize character
            cBuff[charCnt] = Character.toUpperCase(cBuff[charCnt]);
            toReturn = new String(cBuff);
        }
        
        return toReturn;
    }
    
    private static boolean isWhiteSpace( Node node) throws Exception{
        return node.getText().trim().length() == 0;
    }
    
    
}