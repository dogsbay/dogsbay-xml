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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ImportFromTextDialog;
import com.dogsbay.dogsbayaieditor.ImportUtilities;
import com.dogsbay.dogsbayaieditor.NonXMLDocumentChooserDialog;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import java.net.URL;

/**
 * An action that can be used to import non XML from a text-based file.
 *
 * @version	$Revision: 1.8 $, $Date: 2004/10/27 10:43:53 $
 */
public class ImportFromTextAction extends AbstractAction {
    
    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private NonXMLDocumentChooserDialog chooser = null;
    private ImportFromTextDialog dialog = null;
    
    /**
     * The constructor for the action which allows importing 
     * of non - XML text-based Documents.
     *
     * @param parent the parent frame.
     */
    public ImportFromTextAction( DogsBayAIEditor parent) {
        super( "From Text File ...");
        
        this.parent = parent;
        //this.properties = props;
        
        putValue( MNEMONIC_KEY, Integer.valueOf( 'T'));
        putValue( SHORT_DESCRIPTION, "Text File ...");
    }
    
    /**
     * The implementation of the validate action, called 
     * after a user action.
     *
     * @param event the action event.
     */
    public void actionPerformed( ActionEvent event) {
        if ( dialog == null) {
            dialog = new ImportFromTextDialog( parent);
        }
        
        if ( chooser == null) {
            chooser = new NonXMLDocumentChooserDialog( parent, "Import",NonXMLDocumentChooserDialog.TYPE_TEXT,null);
        }
        chooser.setTitle("Import","From Text File","Choose the file to import from");
        
        DogsBayView view = parent.getView();
        
        if ( view != null) {
            view.updateModel();
        }
        
        DogsBayDocument document = parent.getDocument();
        
        if ( document != null) {
            chooser.showDialog(NonXMLDocumentChooserDialog.TYPE_TEXT);
        } else{
            chooser.showDialog(NonXMLDocumentChooserDialog.TYPE_TEXT);
        }
        
        if ( !chooser.isCancelled()) {
            
            
            URL url = chooser.getInputLocation();
            String encoding = chooser.getTextEncoding();
            
            dialog.show(url,encoding);
            if ( !dialog.isCancelled()) {
                
                parent.setWait( true);
                parent.setStatus( "Importing From Text File ...");
                
                // Run in Thread!!!
                Runnable runner = new Runnable() {
                    public void run()  {
                        try {
                            
                            
                            DogsBayDocument newDocument = new DogsBayDocument( ImportUtilities.createXMLFile(dialog.table,dialog.docField.getText(),dialog.rowField.getText(),dialog.checkConvertChars.isSelected()));
                            parent.open( newDocument, null);
                            
                            
                            
                            
                        } catch ( Exception e) {
                            // This should never happen, just report and continue
                            MessageHandler.showError( parent, "Cannot Import From Text File", "Import From Text Error");
                        } finally {
                            parent.setStatus( "Done");
                            parent.setWait( false);
                        }
                    }
                };
                
                // Create and start the thread ...
                Thread thread = new Thread( runner);
                thread.start();
//              }
            }
        }
    }
    
    
}
