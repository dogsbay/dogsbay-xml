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
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to Move Namespace To Where First Used
 *
 * @version	$Revision: 1.9 $, $Date: 2004/10/26 10:00:34 $
 */
public class ToolsMoveNSToFirstUsedAction extends AbstractAction {
    
    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private Editor editor = null;
    private ConfigurationProperties props;
    
    /**
     * The constructor for the action which allows Move Namespace To Where First Used
     *
     * @param parent the parent frame.
     */
    public ToolsMoveNSToFirstUsedAction( DogsBayAIEditor parent, Editor editor, ConfigurationProperties props) {
        super( "Move Namespaces to Where First Used");
        
        this.parent = parent;
        this.props = props;
        
        //this.properties = props;
        
        putValue( MNEMONIC_KEY, Integer.valueOf( 'F'));
        putValue( SHORT_DESCRIPTION, "Move Namespaces to Where First Used");
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
        
        //called to make sure that the model is up to date to 
        //prevent any problems found when undo-ing etc.
        parent.getView().updateModel();
        
        //get the document
        final DogsBayDocument document = parent.getDocument();
        
        if ( document.isError()) {
            MessageHandler.showError( parent,"Please make sure the document is well-formed.", "Parser Error");
            return;
        }
        
        parent.setWait( true);
        parent.setStatus( "Moving Namespaces To Where First Used ...");
        
        // Run in Thread!!!
        Runnable runner = new Runnable() {
            public void run()  {
                try {
                    //create temporary document
                    DogsBayDocument tempDoc =  new DogsBayDocument(document.getText());
                    
                    
                    
                    tempDoc = document;
                    String newDocument = moveNSToFirstUsed(tempDoc);
                    if(newDocument!=null) {
                        parent.getView().getEditor().setText(newDocument);
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                parent.switchToEditor();
                                
                                parent.getView().updateModel();
                            }
                        });
                    }
                    
                } catch ( Exception e) {
                    // This should never happen, just report and continue
                    MessageHandler.showError( parent, "Cannot Move Namespaces To Where First Used","Tools Move Namespaces To Where First Used Error");
                } finally {
                    parent.setStatus( "Done");
                    parent.setWait( false);
                }
            }
        };
        
        // Create and start the thread ...
        Thread thread = new Thread( runner);
        thread.start();
//      }
    }
    
    
    private String moveNSToFirstUsed(DogsBayDocument document) {
        
        try {
            XElement root = document.getRoot();
            
            //get all the namespaces in the document
            Vector allNamespaces = document.getDeclaredNamespaces();
            
            if(allNamespaces.size()>0) {
                
                this.removeNamespacesFromElement(root);
                Vector oldNamespaces = iterateTree(root, root,allNamespaces);
                
               
                //now all namespaces have been deleted so dom4j should put all those needed back in the document.
                //now need to check if there are any not in the final document and add these to the root
                document.update();
                Vector newNamespaces = document.getDeclaredNamespaces();
                if(newNamespaces.size()!=allNamespaces.size()) {
                    int difference = allNamespaces.size()-newNamespaces.size();
                    
                    int messageAnswer = MessageHandler.showConfirm(parent,"The document contains "+difference + " unused namespace(s) which will be removed.\n" +
                    		"Do you wish to continue?");
                    
                    if(messageAnswer==MessageHandler.CONFIRM_NO_OPTION) {
                        return(null);
                    }
                    
                    
                }
                
                
                
                document.update();
            }
            else {
//              there are no namespaces declared
                MessageHandler.showError( parent, "There are no namespaces declared in this document", "Tools Move Namespaces To Where First Used Error");
                return(null);
            }
            
        }
        catch (NullPointerException e) {
            MessageHandler.showError(parent,"Error - Cannot Move Namespace To Where First Used","Tools Move Namespaces To Where First Used Error");
            return(null);
        }
        catch (Exception e) {
            MessageHandler.showError(parent,"Error - Cannot Move Namespace To Where First Used","Tools Move Namespaces To Where First Used Error");
            return(null);
        }
        return document.getText();
    }
    
    private void removeNamespacesFromElement(Element element) throws Exception {
        List allNamespaces = element.declaredNamespaces();
        for(int cnt=0;cnt<allNamespaces.size();++cnt) {
            element.remove((Namespace)allNamespaces.get(cnt));
        }
    }
    
    private Vector iterateTree(XElement root, Element element, Vector allNamespaces) throws Exception{
        
        for ( int i = 0, size = element.nodeCount(); i < size; i++ ) {
            Node oldNode = element.node(i);
            if(oldNode instanceof Element) {
                
                XElement oldElement = (XElement)oldNode;
                this.removeNamespacesFromElement(oldElement);
                
                iterateTree(root, oldElement, allNamespaces);
                
            }
        }
        //return the vector of namespaces
        return(allNamespaces);
    }
    
    private String moveNamespaces(DogsBayDocument document) throws Exception {
        
        try {
            XElement root = document.getRoot();
            
            Vector namespaces = document.getDeclaredNamespaces();
            for(int cnt=0;cnt<namespaces.size();++cnt) {
                Namespace ns1 = (Namespace)namespaces.get(cnt);
                root.add(ns1);
            }
            document.update();
        }
        catch (NullPointerException e) {
            MessageHandler.showError(parent,"Error - Cannot Move Namespaces","Tools Move Namespaces To Where First Used Error");            
        }
        return document.getText();
    }           
}