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
import java.sql.Connection;

import javax.swing.AbstractAction;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ImportFromDBTableDialog;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.NonXMLDocumentChooserDialog;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.xml.DogsBayDocument;

/**
 * An action that can be used to import non XML from a Database Table.
 *
 * @version	$Revision: 1.11 $, $Date: 2004/10/27 10:43:53 $
 */
public class ImportFromDBTableAction extends AbstractAction {
    
    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private NonXMLDocumentChooserDialog chooser = null;
    private ImportFromDBTableDialog dialog = null;
    private ConfigurationProperties props = null;
    
    /**
     * The constructor for the action which allows importing 
     * of database tables.
     *
     * @param parent the parent frame.
     */
    public ImportFromDBTableAction( DogsBayAIEditor parent, ConfigurationProperties props) {
        super( "From Database Table ...");
        
        this.parent = parent;
        this.props = props;
        
        putValue( MNEMONIC_KEY, Integer.valueOf( 'D'));
        putValue( SHORT_DESCRIPTION, "Database Table ...");
    }
    
    /**
     * The method which is called when the action is invoked
     * @param event the action event.
     */
    public void actionPerformed( ActionEvent event) {
        if ( dialog == null) {
            dialog = new ImportFromDBTableDialog( parent);
            
        }
        if ( chooser == null) {
            chooser = new NonXMLDocumentChooserDialog( parent, "Import",NonXMLDocumentChooserDialog.TYPE_DATABASE,props);
        }
        chooser.setTitle("Import","From Database Table","Connect to the database to import from");
        
        DogsBayView view = parent.getView();
        
        if ( view != null) {
            view.updateModel();
        }
        
        DogsBayDocument document = parent.getDocument();
        
        
        chooser.showDialog(NonXMLDocumentChooserDialog.TYPE_DATABASE);
        Connection con = chooser.getCon();
        
        
        if ( !chooser.isCancelled()) {
            
            
            try {
                dialog.show(con,false,chooser.getDriver(), 
                        chooser.getUrlConnection(), chooser.getUsername(), 
                        chooser.getPassword());
                if ( !dialog.isCancelled()) {
                    parent.setWait( true);
                    parent.setStatus( "Importing From Database Table ...");
                    
                    // Run in Thread!!!
                    Runnable runner = new Runnable() {
                        public void run()  {
                            try {
                                
                                //DogsBayDocument newDocument = new DogsBayDocument( createXMLFile(dialog));
                                DogsBayDocument newDocument = new DogsBayDocument( dialog.getImportedXML() );
                                if(newDocument!=null) {
                                    parent.open( newDocument, null);
                                }
                            } catch ( Exception e) {
                                // This should never happen, just report and continue
                                MessageHandler.showError( parent, "Cannot Import From Database Table","Import From Database Table Error");
                            } finally {
                                parent.setStatus( "Done");
                                parent.setWait( false);
                            }
                        }
                    };
                    
                    // Create and start the thread ...
                    Thread thread = new Thread( runner);
                    thread.start();
//                  }
                }
                
                
                
            } catch ( Exception x) {
                MessageHandler.showError( "Could not create the Document:\n"+chooser.getInputLocation(), "Document Error");
            }
        }
    }
       
}
