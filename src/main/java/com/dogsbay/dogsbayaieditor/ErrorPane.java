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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import org.bounce.event.DoubleClickListener;
import org.bounce.event.PopupListener;

import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.editor.Editor;
//import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.component.GUIUtilities;
import com.dogsbay.dogsbayaieditor.component.ScrollableListPanel;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;

/**
 * The panel that shows parsing error information.
 *
 * @version	$Revision: 1.8 $, $Date: 2005/06/23 15:14:05 $
 * @author Dogsbay
 */
 public class ErrorPane extends JPanel {
 	private static final boolean DEBUG = false;
	private static final ImageIcon ERROR_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Error8.gif");
	private static final ImageIcon WARNING_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Warning8.gif");

	private static final EmptyBorder NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
	private static final CompoundBorder TOP_BORDER = new CompoundBorder( new MatteBorder( 1, 0, 0, 0, UIManager.getColor("controlShadow")), new EmptyBorder( 2, 2, 2, 2));
	private static final MatteBorder BOTTOM_BORDER = new MatteBorder( 0, 0, 1, 0, UIManager.getColor("controlShadow"));

	private JList list = null;
	private DogsBayAIEditor parent = null;
 	private Editor editor = null;
 	//private Grid grid = null; 	
 	protected ErrorList errorList = null;
 	protected ErrorListModel model = null;
 	
 	public JPopupMenu errorPanePopup = null;
	private AbstractAction copyAction = null;
	private AbstractAction fixWithAiAction = null;
 	
 	public ErrorPane( DogsBayAIEditor editor) {
 		super( new BorderLayout());
		this.parent = editor;
		
		model = new ErrorListModel();
		list = new JList( model);
		list.setCellRenderer( new ErrorCellRenderer());
		list.setSelectionMode( ListSelectionModel.SINGLE_SELECTION);
		JScrollPane scroller = new JScrollPane( new ScrollableListPanel( list));
		
		add( scroller, BorderLayout.CENTER);
		scroller.getViewport().setBackground( list.getBackground());
		
		if ( openOnSingleClick()) {
			list.addMouseListener( new java.awt.event.MouseAdapter() {
				public void mouseClicked( MouseEvent e) {
					if ( e.getClickCount() == 1 && list.getSelectedIndex() != -1) {
						errorSelected();
					}
				}
			});
		} else {
			list.addMouseListener( new DoubleClickListener() {
				public void doubleClicked( MouseEvent e) {
					int index = list.getSelectedIndex();

					if ( index != -1) { // A row is selected...
						// perform the selection.
						errorSelected();
					}
				}
			});
		}
		
		GotoAction gotoAction = new GotoAction();
		list.getActionMap().put( "gotoAction", gotoAction);
		list.getInputMap( JComponent.WHEN_FOCUSED).put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0, false), "gotoAction");

		updatePreferences();

		buildErrorPanePopupMenu();
		list.addMouseListener ( new PopupListener() {
            public void popupTriggered( MouseEvent e) {
                copyAction.setEnabled( model.getSize() > 0);
                if ( fixWithAiAction != null) {
                    fixWithAiAction.setEnabled( list.getSelectedValue() instanceof XMLError);
                }
                ErrorPane.this.errorPanePopup.show( list, e.getX(), e.getY());
                
            }
		});
 	}
 	
 	private void buildErrorPanePopupMenu() {

        errorPanePopup = new JPopupMenu();
		
        // Copy THIS pane's errors — not a shared action bound to the main Errors
        // pane (which would copy the wrong list from e.g. Project Validation).
        copyAction = new AbstractAction( "Copy To Clipboard") {
            public void actionPerformed( ActionEvent e) {
                copyErrorsToClipboard();
            }
        };
        copyAction.putValue( AbstractAction.SMALL_ICON,
                DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Copy16.gif"));
        errorPanePopup.add( copyAction);

        // Hand the selected validation problem to the AI agent as a focused fix
        // (plans/agent-quickfix-on-markers.md).
        fixWithAiAction = new AbstractAction( "Fix with AI") {
            public void actionPerformed( ActionEvent e) {
                fixSelectedErrorWithAi();
            }
        };
        errorPanePopup.add( fixWithAiAction);

		GUIUtilities.alignMenu( errorPanePopup);

    }

	/** Hand the selected validation error to the AI agent as a focused fix. */
	private void fixSelectedErrorWithAi() {
		Object item = list.getSelectedValue();
		if ( !(item instanceof XMLError error)) {
			return;
		}
		com.dogsbay.agent.FixRequest request = new com.dogsbay.agent.FixRequest(
				error.getSystemId(), error.getLineNumber(), error.getMessage(), null);
		boolean routed = com.dogsbay.dogsbayaieditor.plugin.agent.AgentPlugin.fixWithAI( request);
		if ( !routed) {
			javax.swing.JOptionPane.showMessageDialog( ErrorPane.this,
					"The AI Agent isn't available. Open the AI Agent panel and configure a provider first.",
					"Fix with AI", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/** Copy every error currently shown in <em>this</em> pane to the clipboard. */
	private void copyErrorsToClipboard() {
		try {
			StringBuilder buffer = new StringBuilder();
			for ( int i = 0; i < model.getSize(); ++i) {
				Object item = model.getElementAt( i);
				if ( item instanceof XMLError error) {
					String systemId = error.getSystemId();
					boolean withFile = systemId != null && showsFileName( systemId);
					buffer.append( clipboardLine( error, withFile));
				} else {
					buffer.append( item);
				}
				buffer.append( "\n");
			}
			java.awt.datatransfer.StringSelection ss =
					new java.awt.datatransfer.StringSelection( buffer.toString());
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents( ss, null);
		} catch ( Exception ex) {
			MessageHandler.showError( parent,
					"Error copying contents to clipboard", "Copy To Clipboard Error");
		}
	}
	
 	protected void errorSelected() {
 		Object item = list.getSelectedValue();
 		if ( parent.getView() == null) {
 			return;   // no document/view open — nothing to navigate to
 		}

 		if((parent.getView().getCurrentView() instanceof Editor) && (getEditor() != null) && (item != null) && (item instanceof XMLError)) {
 			XMLError error = (XMLError)item;
 			String systemId = error.getSystemId();

 			if ( systemId != null) {
 				// getDocument() can be null even when a view exists (a view whose document
 				// failed to load, or is still opening), and this runs from a click handler
 				// where an NPE just vanishes into the EDT. A null name means "not the
 				// active document", so open it.
 				String name = parent.getDocument() != null ? parent.getDocument().getName() : null;
 				
 				if ( name == null || !systemId.endsWith( name)) {
 					// need to do this in a thread.
 					parent.open( URLUtilities.toURL( systemId), null, false);

 					DogsBayView view = parent.getView();
 					if ( view != null) {
 						view.getEditor().selectError( (XMLError)item);
 						view.getEditor().setFocus();
 					}
 					return;
 				}
 			}
 			getEditor().selectError( error);
			getEditor().setFocus();
 		}
 		else if((parent.getView().getCurrentView() instanceof PluginViewPanel) && (item != null) && (item instanceof XMLError)) {
 			XMLError error = (XMLError)item;
  		   
			String systemId = error.getSystemId();

			if ( systemId != null) {
				// getDocument() can be null even when a view exists (a view whose document
				// failed to load, or is still opening), and this runs from a click handler
				// where an NPE just vanishes into the EDT. A null name means "not the
				// active document", so open it.
				String name = parent.getDocument() != null ? parent.getDocument().getName() : null;
				
				if ( name == null || !systemId.endsWith( name)) {
					// need to do this in a thread.
					parent.open( URLUtilities.toURL( systemId), null, false);

					DogsBayView view = parent.getView();
					if ( view != null) {
						parent.getView().getCurrentView().setFocus();
						((PluginViewPanel)parent.getView().getCurrentView()).selectError( (XMLError)item);
						//view.getEditor().setFocus();
						
						//DogsBayDocument doc = parent.getDocument();
						//doc.getElement(1);
					}
					return;
				}
			}
			
			//parent.switchToEditor();
			//editor = (Editor) parent.getView().getCurrentView();
			//editor.selectError( error);
			((PluginViewPanel)parent.getView().getCurrentView()).selectError( (XMLError)item);
			//editor.setFocus();
 		}
 		/*else if((parent.getView().getCurrentView() instanceof Grid) && (grid != null) && (item != null) && (item instanceof XMLError)) {
 		   XMLError error = (XMLError)item;
 		   
			String systemId = error.getSystemId();

			if ( systemId != null) {
				// getDocument() can be null even when a view exists (a view whose document
				// failed to load, or is still opening), and this runs from a click handler
				// where an NPE just vanishes into the EDT. A null name means "not the
				// active document", so open it.
				String name = parent.getDocument() != null ? parent.getDocument().getName() : null;
				
				if ( name == null || !systemId.endsWith( name)) {
					// need to do this in a thread.
					parent.open( URLUtilities.toURL( systemId), null, false);

					DogsBayView view = parent.getView();
					if ( view != null) {
						grid.setFocus();
						view.getGrid().selectError( (XMLError)item);
						//view.getEditor().setFocus();
						
						//DogsBayDocument doc = parent.getDocument();
						//doc.getElement(1);
					}
					return;
				}
			}
			
			//parent.switchToEditor();
			//editor = (Editor) parent.getView().getCurrentView();
			//editor.selectError( error);
			grid.selectError(error);
			//editor.setFocus();
			
			
 		} */
 	}

 	public void setCurrent( Object view) {
		if (DEBUG) System.out.println( "ErrorPane.setCurrent( "+view+")");
		if ( view instanceof Editor) {
			setEditor((Editor)view);
			
		} /*if ( view instanceof Grid) {
			grid = (Grid) view;
			*/
		else if(view instanceof PluginViewPanel) {
			
		} /*else {
			editor = null;
			grid = null;
		}*/
	}

	public void setErrorList( ErrorList errors) {
 		if (DEBUG) System.out.println( "ErrorPane.setErrorList( "+errors+")");
		this.errorList = errors;
		
		list.setSelectedIndex(-1);
		model.clear();
		
		if ( errors != null) {
			model.addText( errors.getHeader());
			model.setList( errors.getErrors());
			model.addText( errors.getFooter());
		}
	}

	public void startCheck( String text) {
 		if (DEBUG) System.out.println( "ErrorPane.startCheck( "+text+")");

		list.setSelectedIndex(-1);
		model.clear();
		if ( errorList == null) {
			errorList = new ErrorList();
		}
 		errorList.reset();
 		errorList.setHeader( text);
		model.addText( text);
 	}

 	public void endCheck( String text) {
 		if (DEBUG) System.out.println( "ErrorPane.endCheck( "+text+")");

		if ( errorList == null) {
			errorList = new ErrorList();
		}
 		errorList.setFooter( text);
		model.addText( text);
 	}

 	public void clear() {
 		if (DEBUG) System.out.println( "ErrorPane.clear()");

 		errorList = null;
		list.setSelectedIndex(-1);
		model.clear();
 	}

 	public void select( XMLError error) {
 		if (DEBUG) System.out.println( "ErrorPane.select( "+error+")");
 		
 		int index = model.indexOf( error);
 		list.setSelectedIndex( index);
		list.ensureIndexIsVisible( index);
 	}

 	public void updatePreferences() {
 		if (DEBUG) System.out.println( "ErrorPane.updatePreferences()");
		// Use the system UI font — do not override with monospaced.
		// The JList inherits the correct L&F font automatically.
 	}

 	public void addError( XMLError error) {
		if (DEBUG) System.out.println( "ErrorPane.addError( "+error+")");
		if ( errorList == null) {  // defensive: addError before startCheck/setErrorList
			errorList = new ErrorList();
		}
		errorList.addError( error);
		model.addError( error);
 	}

 	public void addErrorSortedByLineNumber( XMLError error) {
		if (DEBUG) System.out.println( "ErrorPane.addErrorSortedByLineNumber( "+error+")");
		if ( errorList == null) {  // defensive: addError before startCheck/setErrorList
			errorList = new ErrorList();
		}
		errorList.addErrorSortedByLineNumber( error);
		model.addErrorSortedByLineNumber( error);
 	}
 	
 	public void sortErrorsByLineNumber() {
 		errorList.sortErrorsByLineNumber();
 		setErrorList(errorList);
 	}
 	
	class ErrorListModel extends AbstractListModel {
		Vector errors = null;
		
		public ErrorListModel() {
			errors = new Vector();
		}
		
		public void setList( Vector list) {
			if ( list != null) {
				for ( int i = 0; i < list.size(); i++) {
					errors.addElement( list.elementAt(i));
				}
			}

			fireContentsChanged( this, 0, errors.size()-1);
		}

		public void clear() {
			int size = errors.size();
			errors.removeAllElements();

			fireIntervalRemoved( this, 0, 0);
		}

		public int getSize() {
			if ( errors != null) {
				return errors.size();
			}
			
			return 0;
		}

		public void addErrorSortedByLineNumber( XMLError error) {
			if(error != null) {
				if(errors != null) {
					boolean greaterThanFound = false;
					int cnt = 0;
					while((greaterThanFound == false) && (cnt < errors.size())) {
						
						Object tempObj = errors.get(cnt);
						if(tempObj instanceof XMLError) {
							XMLError tempError = (XMLError) tempObj;
							if(tempError != null) {
								if(error.getLineNumber() > tempError.getLineNumber()) {
									//add after
								}
								else if(error.getLineNumber() < tempError.getLineNumber()) {
									//add before now
									greaterThanFound = true;
								}
								else {
									
									if(error.getColumnNumber() > tempError.getColumnNumber()) {
										//add after
									}
									else if(error.getColumnNumber() < tempError.getColumnNumber()) {
										greaterThanFound = true;
									}
									else {
										//prob wont happen
									}
								}
							}							
						}
						if(greaterThanFound == false) {
							cnt++;
						}
					}
					

					if(errors.size() == 0) {
						errors.add(error);
					}
					else {
						//add before cnt
						errors.add(cnt, error);
						fireIntervalAdded( this, errors.size()-1, errors.size()-1);
					}
				}				
			}
		}
		
		public void addError( XMLError error) {
//			System.out.println( "addError( "+error+")");
			if ( error != null) {
				errors.addElement( error);
	
				fireIntervalAdded( this, errors.size()-1, errors.size()-1);
			}
		}

		public void addText( String text) {
//			System.out.println( "addText( "+text+")");
			if ( text != null) {
				// Find out where to insert the bookmark...
				errors.addElement( text);
	
				try {
					fireIntervalAdded( this, errors.size()-1, errors.size()-1);
				} catch(ArrayIndexOutOfBoundsException e) {
					e.printStackTrace();
				}
			}
		}

		public Object getElementAt( int i) {
			return errors.elementAt( i);
		}

		public Object getElement( int i) {
			return errors.elementAt( i);
		}
		
		public int indexOf( XMLError error) {
			for ( int i = 0; i < errors.size(); i++) {
				if ( errors.elementAt(i) == error) {
					return i;
				}
			}
			
			return -1;
		}
	}

	/**
	 * Whether a result row should show the file it came from.
	 *
	 * <p>Every row in this pane belongs to the active document, so repeating its
	 * name on each line is noise — it is suppressed. Panes whose results span
	 * several files override this: see {@link ProjectErrorPane}, where hiding the
	 * name of whichever file happens to be open makes rows appear to lose their
	 * filename as you click through them.
	 *
	 * @param systemId the errored file's system id, never null
	 * @return true to show {@code [systemId]} beside the line/column
	 */
	protected boolean showsFileName( String systemId) {
		String name = parent.getDocument() != null ? parent.getDocument().getName() : null;
		return showsFileName( systemId, name);
	}

	/**
	 * The suppression rule, separated from the pane so it can be tested without a
	 * running editor.
	 *
	 * <p>Comparison is by basename. That can suppress the name of a same-named
	 * file in another directory — common in DITA projects — which is tolerable
	 * only because this pane lists a single document; {@link ProjectErrorPane}
	 * spans many and overrides the rule entirely rather than inheriting the flaw.
	 *
	 * @param systemId the errored file's system id, never null
	 * @param activeDocumentName the active document's file name, or null if none
	 * @return true to show the filename on the row
	 */
	static boolean showsFileName( String systemId, String activeDocumentName) {
		return activeDocumentName == null || !systemId.endsWith( activeDocumentName);
	}

	/**
	 * One clipboard line for an error, matching what the row shows: the
	 * position without a {@code Col -1} sentinel, the file when the pane shows
	 * it, then the message.
	 */
	static String clipboardLine( XMLError error, boolean withFile) {
		StringBuilder line = new StringBuilder( "Ln ").append( error.getLineNumber());
		if ( error.getColumnNumber() > 0) {
			line.append( " Col ").append( error.getColumnNumber());
		}
		if ( withFile && error.getSystemId() != null) {
			line.append( " [").append( error.getSystemId()).append( ']');
		}
		return line.append( " - ").append( error.getMessage()).toString();
	}

	public class ErrorCellRenderer extends JPanel implements ListCellRenderer {
		private JLabel text = null;
		private JLabel position = null;
		private JTextArea message = null;
		private JLabel file = null;

		/**
		 * The constructor for the renderer, sets the font type etc...
		 */
		public ErrorCellRenderer() {
			super( new BorderLayout());
			
//			icon = new JLabel();

			position = new JLabel();
			position.setBorder( new EmptyBorder( 0, 2, 0, 2));
			position.setOpaque( false);
			position.setFont( position.getFont().deriveFont( Font.BOLD));
			position.setForeground( Color.black);
			
			text = new JLabel();
			text.setBorder( new EmptyBorder( 2, 2, 2, 2));
			text.setFont( text.getFont().deriveFont( Font.PLAIN));
			text.setForeground( Color.black);
			text.setOpaque( true);
			text.setHorizontalAlignment( JLabel.LEFT);
			

			message = new JTextArea();
			message.setBorder( new EmptyBorder( 0, 2, 0, 2));
			message.setOpaque( false);
			message.setFont( message.getFont().deriveFont( Font.PLAIN));
			message.setForeground( Color.black);
			message.setLineWrap( true);
			message.setWrapStyleWord( true);
			message.setEditable( false);
			message.setFocusable( false);

			file = new JLabel();
			file.setOpaque( false);
			file.setFont( file.getFont().deriveFont( Font.PLAIN));
			file.setForeground( Color.black);
			file.setBorder( new EmptyBorder( 0, 2, 0, 2));
			
			JPanel northPanel = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0));
			northPanel.add( position);
			northPanel.add( file);
			northPanel.setOpaque( false);
			
			this.add( northPanel, BorderLayout.NORTH);
			this.add( message, BorderLayout.CENTER);
			this.setBorder( TOP_BORDER);
		}
		
		public void setPreferredFont( Font font) {
			position.setFont( font.deriveFont( Font.BOLD));
			message.setFont( font.deriveFont( Font.PLAIN));
			file.setFont( font.deriveFont( Font.PLAIN));
			text.setFont( font.deriveFont( Font.PLAIN));
		}
		
		public Component getListCellRendererComponent( JList list, Object node, int index, boolean selected, boolean focus) {
			if ( node instanceof XMLError) {
	 			XMLError error = (XMLError)node;
	 			String systemId = error.getSystemId();

				file.setText( null);
// 				file.setIcon( null);
 				file.setVisible( false);
//
 				if ( systemId != null && showsFileName( systemId)) {
					file.setText( "["+systemId+"]");
					file.setVisible( true);
	 			}

				if ( error.getType() == XMLError.WARNING) {
					position.setIcon( WARNING_ICON);
				} else { 
					position.setIcon( ERROR_ICON);
				}

				// Schematron and the DITA project rules report a line but no column,
				// using -1 as the sentinel. Showing "Col -1" surfaces an internal
				// marker as if it were a position.
				position.setText( error.getColumnNumber() > 0
						? "Ln "+error.getLineNumber()+" Col "+error.getColumnNumber()
						: "Ln "+error.getLineNumber());

				message.setText( error.getMessage());

			} else {
				text.setText( node.toString());

				if ( selected) {
					text.setForeground( list.getSelectionForeground());
					text.setBackground( list.getSelectionBackground());
				} else {
					text.setForeground( list.getForeground());
					text.setBackground( list.getBackground());
				}

				text.setEnabled( list.isEnabled());
				
				int size = list.getModel().getSize();
				if ( size > 2 && size == index+1) {
					text.setBorder( TOP_BORDER);
				} else {
					text.setBorder( new EmptyBorder( 2, 2, 2, 2));
				}

				return text;
			}

			if ( selected) {
				message.setForeground( list.getSelectionForeground());
				position.setForeground( list.getSelectionForeground());
				file.setForeground( list.getSelectionForeground());
				this.setBackground( list.getSelectionBackground());
			} else {
				message.setForeground( list.getForeground());
				position.setForeground( list.getForeground());
				file.setForeground( list.getForeground());
				this.setBackground( list.getBackground());
			}

			setEnabled( list.isEnabled());
			setPreferredFont( list.getFont());

			return this;
		}
	} 

	public class GotoAction extends AbstractAction {
		public GotoAction() {
			super( "Goto Error");
		}

		public void actionPerformed( ActionEvent e) {
			errorSelected();
		}
	};
    /**
     * @return Returns the list.
     */
    public JList getList() {

        return list;
    }
    /**
     * @param list The list to set.
     */
    public void setList(JList list) {

        this.list = list;
    }

	public void setEditor(Editor editor) {
		this.editor = editor;
	}

	public Editor getEditor() {
		return editor;
	}

	/** Snapshot of the XML errors currently shown in this pane. */
	public java.util.List<XMLError> currentErrors() {
		java.util.List<XMLError> out = new java.util.ArrayList<>();
		if ( errorList != null) {
			for ( Object o : errorList.getErrors()) {
				if ( o instanceof XMLError) {
					out.add( (XMLError) o);
				}
			}
		}
		return out;
	}

	/** The currently selected error, or null. Exposed for subclass navigation. */
	protected XMLError selectedError() {
		Object v = list.getSelectedValue();
		return (v instanceof XMLError) ? (XMLError) v : null;
	}

	/** The owning editor window. Exposed for subclass navigation. */
	protected DogsBayAIEditor owner() {
		return parent;
	}

	/**
	 * When true a single click navigates to the error; otherwise a double click.
	 * Single click by default (app-wide convention); subclasses may override.
	 */
	protected boolean openOnSingleClick() {
		return true;
	}
}
