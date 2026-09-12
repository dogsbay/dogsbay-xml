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

package com.dogsbay.dogsbayaieditor.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.swing.SwingUtilities;

import org.xml.sax.SAXParseException;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.XMLErrorHandler;
import com.dogsbay.xml.XMLErrorReporter;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.TagCompletionProperties;
import com.dogsbay.dogsbayaieditor.services.events.GrammarChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.SchemaLoadedEvent;

/**
 * Manages schema/grammar lifecycle and provides pure (non-UI) validation
 * and parsing operations that can be used by plugins, CLI tools, or
 * scripting agents.
 */
public class SchemaManager {
	private final DogsBayAIEditor editor;

	public SchemaManager(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	// -----------------------------------------------------------------------
	// Methods moved from DogsBayAIEditor (grammar/schema lifecycle)
	// -----------------------------------------------------------------------

	/**
	 * Sets the grammar on the active view and updates UI state.
	 */
	public void setGrammar(GrammarProperties grammar) {
		if (editor.getView() != null) {
			editor.getView().setGrammar(grammar);
		}

		editor.updateStatus();
		editor.updateFragments();
		updateGrammarActions();

		editor.getEventBus().publish(new GrammarChangedEvent(grammar));
	}

	/**
	 * Returns the grammar from the active view.
	 */
	public GrammarProperties getGrammar() {
		if (editor.getView() != null) {
			return editor.getView().getGrammar();
		}
		return null;
	}

	/**
	 * Updates grammar across all open views.
	 */
	public void updateGrammar(GrammarProperties grammar) {
		Vector views = editor.getViews();

		if (views != null) {
			for (int i = 0; i < views.size(); i++) {
				((DogsBayView) views.elementAt(i)).updateGrammar(grammar);
			}

			editor.getNavigator().setDocument(editor.getDocument());
			if (editor.getSidebarViewer() != null) {
				editor.getSidebarViewer().setDocument(editor.getDocument());
			}
			editor.updateStatus();
		}
	}

	/**
	 * Returns the schema from the active view.
	 */
	public SchemaDocument getSchema() {
		if (editor.getView() != null) {
			return editor.getView().getSchema();
		}
		return null;
	}

	/**
	 * Sets the schema internally (helper + view), without checking
	 * if the view accepts it first.
	 */
	public void setSchemaInternal(SchemaDocument schema) {
		editor.getHelper().setSchema(schema);

		if (schema != null) {
			editor.getView().setSchemaInternal(schema);
		}
	}

	/**
	 * Sets the schema on the active view. If the view accepts it,
	 * propagates to helper and triggers GC.
	 */
	public void setSchema(SchemaDocument schema) {
		if (editor.getView() != null && editor.getView().setSchema(schema)) {
			setSchemaInternal(schema);

			// make sure this runs after the gui is updated!
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					System.gc();
				}
			});
		}

		editor.getEventBus().publish(new SchemaLoadedEvent(schema));
	}

	/**
	 * Sets tag completion schemas on the active view.
	 */
	public void setTagCompletionSchemas(Vector schemas) {
		if (editor.getView() != null) {
			editor.getView().setTagCompletionSchemas(schemas);
		}
	}


	/**
	 * Opens tag completion schemas from a list of TagCompletionProperties.
	 * Returns the loaded SchemaDocument vector.
	 */
	public Vector openTagCompletionSchemas(Vector list) {
		Vector result = null;
		setGrammar(null);

		if (list != null && list.size() > 0) {
			result = new Vector();

			for (int i = 0; i < list.size(); i++) {
				URL url = URLUtilities.toURL(((TagCompletionProperties) list.elementAt(i)).getLocation());
				int type = ((TagCompletionProperties) list.elementAt(i)).getType();

				SchemaDocument schema = FileUtilities.createTagCompletionSchema(url, type, true);
				result.addElement(schema);
			}
		}

		return result;
	}

	/**
	 * Returns the grammar properties from the configuration.
	 */
	public Vector getGrammarProperties() {
		return editor.getProperties().getGrammarProperties();
	}

	/**
	 * Enables/disables grammar-related actions based on current state.
	 */
	public void updateGrammarActions() {
		DogsBayDocument document = editor.getDocument();
		Vector grammars = editor.getProperties().getGrammarProperties();
		GrammarProperties grammar = getGrammar();

		if (document != null && document.isXML() && grammars.size() > 0) {
			editor.getOpenGrammarAction().setEnabled(true);
			editor.getNewGrammarAction().setEnabled(true);
			editor.getGrammarPropertiesAction().setEnabled(grammar != null);
		} else {
			editor.getOpenGrammarAction().setEnabled(false);
			editor.getNewGrammarAction().setEnabled(false);
			editor.getGrammarPropertiesAction().setEnabled(false);
		}
	}

	// -----------------------------------------------------------------------
	// Pure operations — no UI side effects, return results directly
	// -----------------------------------------------------------------------

	/**
	 * Parse (check well-formedness) a document by re-loading its text into
	 * the document model. Returns a list of errors found.
	 * <p>
	 * This method does NOT touch any UI components — it is safe to call
	 * from background threads, CLI tools, or scripting agents.
	 *
	 * @param document the document to parse
	 * @return list of XMLError objects; empty list means well-formed
	 */
	public List<XMLError> parse(DogsBayDocument document) {
		List<XMLError> errors = new ArrayList<>();

		if (document == null) {
			return errors;
		}

		try {
			// Re-parse the document text through the document model.
			// setText() will set the exception on the document if parsing fails.
			document.setText(document.getText());
		} catch (SAXParseException e) {
			errors.add(new XMLError(e, XMLError.ERROR));
		} catch (IOException e) {
			errors.add(new XMLError(e));
		}

		// If setText didn't throw but still recorded an error
		if (errors.isEmpty() && document.isError()) {
			Exception ex = document.getError();
			if (ex instanceof SAXParseException) {
				errors.add(new XMLError((SAXParseException) ex, XMLError.ERROR));
			} else if (ex instanceof IOException) {
				errors.add(new XMLError((IOException) ex));
			}
		}

		return errors;
	}

	/**
	 * Validate a document against its schema/grammar. Returns all
	 * validation errors found (up to 100).
	 * <p>
	 * This method does NOT touch any UI components — it is safe to call
	 * from background threads, CLI tools, or scripting agents.
	 *
	 * @param document the document to validate
	 * @return list of XMLError objects; empty list means valid
	 */
	public List<XMLError> validate(DogsBayDocument document) {
		List<XMLError> errors = new ArrayList<>();

		if (document == null) {
			return errors;
		}

		try {
			// First check the schema location is resolvable
			document.checkSchemaLocation();

			// Collect errors without reporting to any UI
			XMLErrorHandler handler = new XMLErrorHandler(null, 100);
			document.validate(handler);

			if (handler.hasErrors()) {
				Vector rawErrors = handler.getErrors();
				for (int i = 0; i < rawErrors.size(); i++) {
					errors.add((XMLError) rawErrors.elementAt(i));
				}
			}
		} catch (SAXParseException e) {
			errors.add(new XMLError(e, XMLError.ERROR));
		} catch (IOException e) {
			errors.add(new XMLError(e));
		} catch (Throwable t) {
			// Wrap unexpected errors
			errors.add(new XMLError(
					new IOException("Unexpected validation error: " + t.getMessage())));
		}

		return errors;
	}

}
