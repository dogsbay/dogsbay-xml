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
import java.io.IOException;
import java.sql.Connection;

import javax.swing.AbstractAction;

import org.xml.sax.SAXParseException;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ImportFromSQLXMLDialog;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.NonXMLDocumentChooserDialog;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayOutputFormat;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.EditorProperties;

/**
 * An action that can be used to import non XML from a SQL/XML query.
 *
 * @version $Revision: 1.11 $, $Date: 2004/11/02 14:58:04 $
 */
public class ImportFromSQLXMLAction extends AbstractAction {
    
    private static final boolean DEBUG = false;
    private DogsBayAIEditor parent = null;
    private NonXMLDocumentChooserDialog chooser = null;
    private ImportFromSQLXMLDialog dialog = null;
    private ConfigurationProperties props;
    
    /**
     * The constructor for the action which allows importing 
     * of database tables.
     *
     * @param parent the parent frame.
     */
    public ImportFromSQLXMLAction( DogsBayAIEditor parent, ConfigurationProperties props) {
        super( "From SQL/XML Query ...");
        
        this.parent = parent;
        this.props = props;
        
        putValue( MNEMONIC_KEY, Integer.valueOf( 'S'));
        putValue( SHORT_DESCRIPTION, "SQL/XML Query ...");
    }
    
    /**
     * The method which is called when the action is invoked
     * @param event the action event.
     */
    public void actionPerformed( ActionEvent event) {
        if ( dialog == null) {
            dialog = new ImportFromSQLXMLDialog( parent,props);
            
        }
        if ( chooser == null) {
            chooser = new NonXMLDocumentChooserDialog( parent, "Import",NonXMLDocumentChooserDialog.TYPE_DATABASE,props);
        }
        chooser.setTitle("Import","From SQL/XML Query","Connect to the database to import from");
        
        DogsBayView view = parent.getView();
        
        if ( view != null) {
            view.updateModel();
        }
        
        final DogsBayDocument document = parent.getDocument();
        
        chooser.showDialog(NonXMLDocumentChooserDialog.TYPE_DATABASE);
        Connection con = chooser.getCon();
        //Connection con = null;
        
        
        if ( !chooser.isCancelled()) {
            
            
            
            
            dialog.show(con,false,chooser.getDriver(), 
                    chooser.getUrlConnection(), chooser.getUsername(), 
                    chooser.getPassword());
            if ( !dialog.isCancelled()) {
                parent.setWait( true);
                parent.setStatus( "Importing SQL/XML ...");
                
                // Run in Thread!!!
                Runnable runner = new Runnable() {
                    public void run()  {
                        try {
                            String xml = dialog.getImportedXML();
                            //System.out.println(xml);
                            DogsBayDocument newDocument = new DogsBayDocument( xml);
                            try {
                                String encoding = newDocument.getEncoding();
                                String result = format( xml, encoding, null);
                                newDocument = new DogsBayDocument( result);
                            } catch (SAXParseException e1) {
                                
                                MessageHandler.showError( parent, "Error - Result XML Is Not Well-Formed", e1, "Import Error");
                            } finally {
                            
                                parent.open( newDocument, null);
                            }
                        } catch ( Exception e) {
                            // This should never happen, just report and continue
                            MessageHandler.showError( parent, "Cannot Import SQL/XML", "Import From SQL/XML Error");
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
    
    public String format( String text, String encoding, String systemId) throws IOException, SAXParseException {
        DogsBayOutputFormat format = new DogsBayOutputFormat();
        return format( text, encoding, systemId, format);
    }
    
    public String format( String text, String encoding, String systemId, DogsBayOutputFormat format) throws IOException, SAXParseException {
        EditorProperties properties = this.props.getEditorProperties();
        
        String indent = "\t";
        boolean newLines = true;
        boolean padText = false;
        boolean preserveMixed = false;
        boolean trim = false;
        int lineLength = -1;
        
        switch ( properties.getFormatType()) {
            case EditorProperties.FORMAT_CUSTOM:
                if ( !properties.isCustomIndent()) {
                    indent = "";
                }
                
            newLines = properties.isCustomNewline();
            padText = properties.isCustomPadText();
            
            if ( properties.isWrapText()) {
                lineLength = properties.getWrappingColumn();
            }
            
            trim = properties.isCustomStrip();
            preserveMixed = properties.isCustomPreserveMixedContent();
            break;
            
            case EditorProperties.FORMAT_COMPACT:
                if ( properties.isWrapText()) {
                    lineLength = properties.getWrappingColumn();
                }
                
            indent = "";
            newLines = false;
            padText = false;
            trim = true;
            preserveMixed = false;
            break;
            case EditorProperties.FORMAT_STANDARD:
                if ( properties.isWrapText()) {
                    lineLength = properties.getWrappingColumn();
                }
                
            newLines = true;
            padText = false;
            
            trim = true;
            preserveMixed = true;
            break;
        }
        
        return XMLUtilities.format( text, systemId, encoding, indent, newLines, padText, lineLength, trim, preserveMixed, format);
    }
}
