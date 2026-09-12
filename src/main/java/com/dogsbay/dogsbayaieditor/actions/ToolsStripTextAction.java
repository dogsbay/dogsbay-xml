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
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.dom4j.Attribute;
import org.dom4j.CDATA;
import org.dom4j.Element;
import org.dom4j.Entity;
import org.dom4j.Node;
import org.dom4j.Text;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ToolsStripTextDialog;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to strip a document of its contents
 * 
 * @version $Revision: 1.19 $, $Date: 2005/03/10 10:54:04 $
 */
public class ToolsStripTextAction extends AbstractAction {

    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private ToolsStripTextDialog dialog = null;
    private Editor editor = null;
    public boolean mixed = false;
    private ConfigurationProperties props;
    
    public static boolean TRAVERSE_CHILDREN = true;
    public static boolean DONT_TRAVERSE_CHILDREN = false;
    


    /**
     * The constructor for the action which allows for striping a document of
     * its contents.
     * 
     * @param parent
     *            the parent frame.
     */
    public ToolsStripTextAction(DogsBayAIEditor parent, Editor editor,ConfigurationProperties props) {

        super("Strip Text ...");
        this.parent = parent;
        this.props = props;
        //this.properties = props;
        putValue(MNEMONIC_KEY, Integer.valueOf('T'));
        putValue(SHORT_DESCRIPTION, "Strip Text ...");
    }

    /**
     * Sets the current view.
     * 
     * @param view
     *            the current view.
     */
    public void setView(Object view) {

        if (view instanceof Editor) {
            editor = (Editor) view;
        }
        else {
            editor = null;
        }
        setDocument(parent.getDocument());
    }

    public void setDocument(DogsBayDocument doc) {

        if (doc != null && doc.isXML()) {
            setEnabled(editor != null);
        }
        else {
            setEnabled(false);
        }
    }

    /**
     * The implementation of the validate action, called after a user action.
     * 
     * @param event
     *            the action event.
     */
    public void actionPerformed(ActionEvent event) {

        if (dialog == null) {
            dialog = new ToolsStripTextDialog(parent,props);
        }
        //called to make sure that the model is up to date to
        //prevent any problems found when undo-ing etc.
        parent.getView().updateModel();
        //get the document
        final DogsBayDocument document = parent.getDocument();
        if (document.isError()) {
            MessageHandler.showError(parent,
                    "Please make sure the document is well-formed.",
                    "Parser Error");
            return;
        }
        //create temporary document
        String currentXPath = null;
        Node node = (Node)document.getLastNode( parent.getView().getEditor().getCursorPosition(), true);

        if ( props.isUniqueXPath()) {
            currentXPath = node.getUniquePath();
        } else {
            currentXPath = node.getPath();
        }
        dialog.show(currentXPath);
        if (!dialog.isCancelled()) {
            parent.setWait(true);
            parent.setStatus("Stripping Document Text ...");
            // Run in Thread!!!
            Runnable runner = new Runnable() {

                public void run() {

                    try {
                        
                        if((dialog.elementsCheckBox.isSelected())||(dialog.attributesCheckBox.isSelected())) {
                            
                        
	                        DogsBayDocument tempDoc = new DogsBayDocument(
	                                document.getText());
	                        String newDocument = null;
	                        
	                        
	                        
	                        if(dialog.xpathPanel.xpathBox.isSelected()) {
	                            String xpathPredicate = dialog.xpathPanel.getXpathPredicate();
	                            newDocument = treeWalk(tempDoc, xpathPredicate, dialog.elementsCheckBox
		                                .isSelected(), dialog.attributesCheckBox
		                                .isSelected(), dialog.mixedContentCheckBox
		                                .isSelected(),TRAVERSE_CHILDREN);
	                        }
	                        else {
	                        
		                        newDocument = treeWalk(tempDoc, dialog.elementsCheckBox
		                                .isSelected(), dialog.attributesCheckBox
		                                .isSelected(), dialog.mixedContentCheckBox
		                                .isSelected());
	                        }
	                        if (newDocument != null) {
	                            if (dialog.toNewDocumentRadio.isSelected()) {
	                                //user has selected to create the result as a
	                                // new document
	                                parent.open(new DogsBayDocument(newDocument),
	                                        null);
	                            }
	                            else {
	                                parent.getView().getEditor().setText(
	                                        newDocument);
	                                SwingUtilities.invokeLater(new Runnable() {
	
	                                    public void run() {
	
	                                        parent.switchToEditor();
	                                        parent.getView().updateModel();
	                                    }
	                                });
	                            }
	                        }
                        }
                    }
                    catch (Exception e) {
                        // This should never happen, just report and continue
                        MessageHandler.showError(parent,
                                "Cannot Strip Document Text","Tools Strip Text Error");
                    }
                
                    finally {
                        parent.setStatus("Done");
                        parent.setWait(false);
                    }
                }
            };
            // Create and start the thread ...
            Thread thread = new Thread(runner);
            thread.start();
            //          }
        }
    }

    public String treeWalk(DogsBayDocument document, boolean stripElements,
            boolean stripAttributes, boolean stripMixedContent) {

        try {
            XElement e = document.getRoot();
            //clear all attributes in the root
            if (stripAttributes) {
                if (e.attributeCount() > 0) {
                    int attCount = e.attributeCount();
                    for (int cnt = 0; cnt < attCount; ++cnt) {
                        Attribute att = e.attribute(cnt);
                        att.setText("");
                    }
                }
            }
            //check if it is mixed
            if ((stripMixedContent) && (isMixed(e))) {
                e.removeAllChildren();
            }
            else {
                treeWalk(e, stripElements, stripAttributes,
                        stripMixedContent,true);
            }
            treeWalk(e, stripElements, stripAttributes, stripMixedContent,true);
            document.update();
        }
        catch (Exception e) {
            MessageHandler.showError(parent,
                    "Cannot Strip The Text From This Document",
                    "Tools Strip Text Error");
            return (null);
        }
        return (document.getText());
    }
    
    public String treeWalk(DogsBayDocument document, String xpathPredicate, boolean stripElements,
            boolean stripAttributes, boolean stripMixedContent, boolean traverseChildren) {
        
        try {
            Vector nodes = document.search( xpathPredicate);
            //System.out.println(nodes.size());
            if(nodes.size()<1) {
                //throw new Exception("Cannot resolve xpath");
                MessageHandler.showError(parent,"Could Not Resolve XPath:\n"+xpathPredicate,"Tools Strip Text Error");
                return(null);
            }
            for(int cnt=0;cnt<nodes.size();++cnt) {
                Node node = (Node)nodes.get(cnt);
                if(node instanceof Element) {
	                XElement e = (XElement)nodes.get(cnt);
	                if (stripAttributes) {
	                    if (e.attributeCount() > 0) {
	                        int attCount = e.attributeCount();
	                        for (int icnt = 0; icnt < attCount; ++icnt) {
	                            Attribute att = e.attribute(icnt);
	                            att.setText("");
	                        }
	                    }
	                }
                
	                //check if it is mixed
	                if ((stripMixedContent) && (traverseChildren) && (isMixed(e))) {
	                    e.removeAllChildren();
	                }
	                else {
	                    treeWalk(e, stripElements, stripAttributes,
	                               stripMixedContent,traverseChildren);
	                    
	                }
	                treeWalk(e, stripElements, stripAttributes, stripMixedContent,traverseChildren);
	                
                }
                else if(node instanceof Attribute) {
                    Attribute att = (Attribute)node;
                    att.setText("");
                }
            }
            document.update();
        }
        catch (Exception e) {
            MessageHandler.showError(parent,
                    "Cannot Strip The Text From This Document",
                    "Tools Strip Text Error");
            //e.printStackTrace();
            return (null);
        }
        return (document.getText());
    }

    private void treeWalk(XElement element, boolean stripElements,
            boolean stripAttributes, boolean stripMixedContent,boolean traverseChildren)
            throws Exception {

        for (int i = 0, size = element.nodeCount(); i < size; i++) {
            Node node = element.node(i);
            if ((node instanceof Element)&&(traverseChildren)) {
                //remove the attributes first
                XElement e = (XElement) node;
                if (stripAttributes) {
                    if (e.attributeCount() > 0) {
                        int attCount = e.attributeCount();
                        for (int cnt = 0; cnt < attCount; ++cnt) {
                            Attribute att = e.attribute(cnt);
                            att.setText("");
                        }
                    }
                }
                //check if it is mixed
                if ((stripMixedContent) && (isMixed((XElement) node))) {
                    e.removeAllChildren();
                }
                else {
                    treeWalk(e, stripElements, stripAttributes,
                            stripMixedContent,traverseChildren);
                }
            }
            else if ((stripElements) && (node instanceof Text)) {
                //these are all text nodes
                // blank but need to preserve formatting
                String value = node.getText();
                //if it does not contain a new line char then can blank
                // straight away
                if (value.indexOf("\n") == -1) {
                    node.setText("");
                }
                else {
                    //id does contain a newline
                    if (value.charAt(0) == '\n') {
                        //if the first char is new line
                        //the new value should have all the spaces, \n and \t
                        // but no letters or digits
                        String newValue = "";
                        for (int cnt = 0; cnt < value.length(); ++cnt) {
                            //look for the first occurence of a real character
                            if (Character.isLetterOrDigit(value.charAt(cnt))) {
                                //cnt = value.length();
                            }
                            else if ((value.charAt(cnt) == '\n')
                                    || (value.charAt(cnt) == '\t')
                                    || (value.charAt(cnt) == ' ')) {
                                newValue += value.charAt(cnt);
                            }
                        }
                        node.setText(newValue);
                    }
                    else {
                        String newValue = "";
                        for (int cnt = 0; cnt < value.length(); ++cnt) {
                            //look for the first occurence of a real character
                            if (Character.isLetterOrDigit(value.charAt(cnt))) {
                                //cnt = value.length();
                            }
                            else if ((value.charAt(cnt) == '\n')
                                    || (value.charAt(cnt) == '\t')
                                    || (value.charAt(cnt) == ' ')) {
                                newValue += value.charAt(cnt);
                            }
                        }
                        node.setText(newValue);
                    }
                }
            }
        }
    }

    private static boolean isMixed(XElement element) throws Exception {

        if (element.hasMixedContent()) {
            boolean elementFound = false;
            boolean textFound = false;
            int count = element.nodeCount();
            for (int i = 0; i < count; i++) {
                Node node = element.node(i);
                if (node instanceof XElement) {
                    elementFound = true;
                }
                else if ((node instanceof Text) || (node instanceof CDATA)
                        || (node instanceof Entity)) {
                    if (!isWhiteSpace(node)) {
                        textFound = true;
                    }
                }
                if (textFound && elementFound) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWhiteSpace(Node node) throws Exception {

        return node.getText().trim().length() == 0;
    }
}