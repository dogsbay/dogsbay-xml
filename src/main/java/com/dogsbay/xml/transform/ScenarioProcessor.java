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

package com.dogsbay.xml.transform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Vector;
import java.util.Properties;

import javax.swing.JFrame;
import javax.xml.transform.ErrorListener;
//import javax.xml.transform.OutputKeys;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
//import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.trans.XPathException;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.bounce.util.BrowserLauncher;
import org.xml.sax.DTDHandler;
import org.xml.sax.SAXException;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;

/**
 * Utilities for transforming XML documents.
 *
 * @version $Revision: 1.11 $, $Date: 2005/04/11 14:11:11 $
 * @author Dogsbay
 */
public class ScenarioProcessor {
	private static final boolean DEBUG = false;

	public TransformationErrorListener listener = null;

	// private TransformerFactory factory = null;

	private SAXSource input = null;
	private Source stylesheet = null;
	private StreamResult output = null;

	private DogsBayDocument document = null;
	private ScenarioProperties scenario = null;

	private Receiver messageEmitter = null;

	public ScenarioProcessor(ScenarioProperties scenario, DogsBayDocument current) {
		this.scenario = scenario;
		this.document = current;
		this.listener = new TransformationErrorListener();
	}

	public void init() {
		ScenarioUtilities.setProcessor(scenario);

		// factory = TransformerFactory.newInstance();
		// factory.setErrorListener( listener);
	}

	private TransformerFactory getTransformerFactory() {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setErrorListener(listener);

		// PHASE 3: Line numbering feature changed in Saxon 12.7
		// Try to set line numbering, but don't fail if not supported
		try {
			factory.setAttribute(
					"http://saxon.sf.net/feature/lineNumbering",
					Boolean.TRUE);
		} catch (IllegalArgumentException e) {
			// Saxon 12.7 doesn't support this old feature URI - that's ok
			if (DEBUG) {
				System.out.println("Line numbering feature not supported (Saxon 12.7+): " + e.getMessage());
			}
		}

		return factory;
	}

	public void setMessageReceiver(Receiver me) {
		this.messageEmitter = me;
	}

	public void setErrorListener(ErrorListener errorListener) {
		getTransformerFactory().setErrorListener(errorListener);
	}

	public void cleanup() {
		listener = null;

		input = null;
		stylesheet = null;
		output = null;

		document = null;
		scenario = null;
	}

	public void openInput() throws IOException, SAXException {
		input = ScenarioUtilities.openScenarioInput(document, scenario);

		if (input == null) {
			throw new IOException("No input defined.");
		}
	}

	public void openStylesheet() throws IOException, SAXException, TransformerException {
		if (scenario.isXSLEnabled()) {
			stylesheet = ScenarioUtilities.openScenarioStylesheet(document, getTransformerFactory(), input, scenario);

			if (scenario.getXSLType() == ScenarioProperties.XSL_USE_PROCESSING_INSTRUCTIONS) {
				// reopen the input.
				input = ScenarioUtilities.reopenScenarioInput(document, scenario, input);
			}

			if (stylesheet == null) {
				throw new IOException("No stylesheet defined.");
			}
		} else if (scenario.isXQueryEnabled()) {
			stylesheet = ScenarioUtilities.openScenarioXQuery(document, scenario);
		}
	}

	public void openStylesheet(DogsBayDocument stylesheetDocument)
			throws IOException, SAXException, TransformerException {
		if (scenario.isXSLEnabled()) {
			stylesheet = ScenarioUtilities.openScenarioStylesheet(stylesheetDocument, getTransformerFactory(), input,
					scenario);

			if (scenario.getXSLType() == ScenarioProperties.XSL_USE_PROCESSING_INSTRUCTIONS) {
				// reopen the input.
				input = ScenarioUtilities.reopenScenarioInput(document, scenario, input);
			}

			if (stylesheet == null) {
				throw new IOException("No stylesheet defined.");
			}
		} else if (scenario.isXQueryEnabled()) {
			stylesheet = ScenarioUtilities.openScenarioXQuery(stylesheetDocument, scenario);
		}
	}

	public void execute() throws SAXException, TransformerException, IOException, FOPException {
		if (!scenario.isFOPEnabled()) {
			output = new StreamResult(new ByteArrayOutputStream());

			Vector params = ScenarioUtilities.getParameters(scenario);

			if (scenario.isXQueryEnabled()) {
				query(input, (StreamSource) stylesheet, output);
			} else {
				transform(input, stylesheet, params, output);
			}
		}
	}

	public void executeWithTraceListener(TraceListener traceListener)
			throws SAXException, TransformerException, IOException, FOPException {
		if (!scenario.isFOPEnabled()) {
			output = new StreamResult(new ByteArrayOutputStream());

			Vector params = ScenarioUtilities.getParameters(scenario);

			if (scenario.isXQueryEnabled()) {
				query(input, (StreamSource) stylesheet, output);
			} else {
				transform(input, stylesheet, params, output, traceListener);
			}
		}
	}

	public void stop() {
		// System.out.println( "ScenarioProperties.stop()");
		try {
			input.getInputSource().getByteStream().close();
			if (stylesheet instanceof StreamSource) {
				((StreamSource) stylesheet).getInputStream().close();
			}
			output.getOutputStream().close();
		} catch (Exception e) {
			// do nothing with the exceptions.
			// e.printStackTrace();
		}
	}

	public void save(JFrame parent) throws IOException, SAXException, FOPException, TransformerException {
		if (scenario.isFOPEnabled()) {
			output = new StreamResult(new ByteArrayOutputStream());
			Vector params = ScenarioUtilities.getParameters(scenario);

			if (scenario.getFOPOutputType() == ScenarioProperties.FOP_OUTPUT_TO_VIEWER) {
				format(parent, input, stylesheet, params, scenario.getFOPType());
			} else {
				format(input, stylesheet, params, output, mimeTypeFor(scenario.getFOPType()));

				ScenarioUtilities.saveFormatOutput(scenario, (ByteArrayOutputStream) output.getOutputStream());
			}
		} else {
			ScenarioUtilities.saveScenarioOutput(scenario, document, input,
					(ByteArrayOutputStream) output.getOutputStream());
		}
	}

	public void save() throws IOException, SAXException {
		ScenarioUtilities.saveScenarioOutput(scenario, document, input,
				(ByteArrayOutputStream) output.getOutputStream());

	}

	public String getOutputText() {
		String text = null;

		try {
			text = ((ByteArrayOutputStream) output.getOutputStream()).toString("UTF-8");
		} catch (Exception ex) {
		}

		return text;
	}

	public void browse() throws IOException {
		String location = ScenarioUtilities.substituteMacros(input.getSystemId(), scenario.getBrowserURL());

		if (location != null && location.trim().length() > 0) {
			URL url = URLUtilities.toURL(location);

			if (url != null) {
				BrowserLauncher.openURL(URLUtilities.encodeURL(url.toString()));
			}
		} else {
			location = ScenarioUtilities.substituteMacros(input.getSystemId(), scenario.getOutputFile());

			if (scenario.getOutputType() == ScenarioProperties.OUTPUT_TO_FILE) {
				ScenarioUtilities.openInBrowser(location, (ByteArrayOutputStream) output.getOutputStream());
			} else {
				ScenarioUtilities.openInBrowser(null, (ByteArrayOutputStream) output.getOutputStream());
			}
		}
	}

	/**
	 * Transforms the Document using the stylesheet and parameters supplied,
	 * puts the result in the OutputStream.
	 *
	 * @param document   the XML document source.
	 * @param stylesheet the XSL document source,
	 *                   if the stylesheet is null, it will try to get the
	 *                   stylesheet for the PIs in the XML document.
	 *                   if the stylesheet is still not found, the default
	 *                   stylesheet is used!
	 * @param parameters list of parameters.
	 * @param result     the output stream result.
	 */
	public void transform(SAXSource document, Source stylesheet, Vector parameters, Result result)
			throws TransformerException, SAXException, IOException {

		if (stylesheet == null) { // no associated stylesheet in the document, use internal stylesheet!
			stylesheet = ScenarioUtilities.createStylesheetSource();
		}

		// Templates templates = getTransformerFactory().newTransformer();

		Transformer transformer = getTransformerFactory().newTransformer(stylesheet);
		CatalogResolver resolver = XMLUtilities.getCatalogResolver();

		transformer.setURIResolver(resolver);

		if (parameters != null) {
			for (int i = 0; i < parameters.size(); i++) {
				String[] parameter = (String[]) parameters.elementAt(i);
				transformer.setParameter(parameter[0], parameter[1]);
			}
		}
		transformer.transform(document, result);
	}

	public void transform(SAXSource document, Source stylesheet, Vector parameters, Result result,
			TraceListener traceListener) throws TransformerException, SAXException, IOException {

		if (stylesheet == null) { // no associated stylesheet in the document, use internal stylesheet!
			stylesheet = ScenarioUtilities.createStylesheetSource();
		}

		// Templates templates = getTransformerFactory().newTransformer();

		Transformer transformer = getTransformerFactory().newTransformer(stylesheet);

		CatalogResolver resolver = XMLUtilities.getCatalogResolver();

		transformer.setURIResolver(resolver);

		if (parameters != null) {
			for (int i = 0; i < parameters.size(); i++) {
				String[] parameter = (String[]) parameters.elementAt(i);
				transformer.setParameter(parameter[0], parameter[1]);
			}
		}

		// PHASE 3: TraceListener/debugger code disabled during Saxon upgrade
		// if (traceListener != null) {
		// 	transformer.setParameter(FeatureKeys.TRACE_LISTENER, traceListener);
		// 	((Controller)transformer).addTraceListener(traceListener);
		// 	((Controller)transformer).setMessageEmitter(messageEmitter);
		// }

		transformer.transform(document, result);
	}

	public void format(SAXSource document, Source stylesheet, Vector parameters, StreamResult output, String mimeType)
			throws TransformerException, SAXException, IOException, FOPException {
		Fop fop = FopFactory.newInstance(new java.io.File(".").getAbsoluteFile().toURI())
				.newFop(mimeType, output.getOutputStream());

		if (stylesheet != null) {
			transform(document, stylesheet, parameters, new SAXResult(fop.getDefaultHandler()));
		} else { // process fo directly
			TransformerFactory.newInstance().newTransformer()
					.transform(input, new SAXResult(fop.getDefaultHandler()));
		}
	}

	/**
	 * Formats the DogsBayDocument using the fo file and parameters supplied,
	 * stores the result in the output file specfied.
	 *
	 * @param document   the source document.
	 * @param stylesheet the stylesheet source.
	 * @param parameters list of parameters.
	 * @param output     the location of the output file.
	 * @param format     the output format: PDF, SVG or TXT.
	 */

	/**
	 * Formats the DogsBayDocument using the fo file and parameters supplied,
	 * stores the result in the output file specfied.
	 *
	 * @param document   the source document.
	 * @param stylesheet the stylesheet source.
	 * @param parameters list of parameters.
	 * @param output     the location of the output file.
	 * @param format     the output format: PDF, SVG or TXT.
	 */
	/**
	 * Render to a temporary file and open it in whatever the desktop uses for that kind of file.
	 *
	 * <p>FOP used to carry its own Swing viewer, and this put the pages on screen through it. Modern FOP has no
	 * viewer — the AWT renderer went with the 0.20 API — and a PDF the desktop opens is what "preview" meant anyway.
	 */
	public void format(JFrame parent, SAXSource document, Source stylesheet, Vector parameters, int type)
			throws TransformerException, SAXException, IOException, FOPException {
		String mimeType = mimeTypeFor(type);
		java.io.File preview = java.io.File.createTempFile("dogsbay-preview-", extensionFor(mimeType));
		preview.deleteOnExit();

		try (java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(preview))) {
			format(document, stylesheet, parameters, new StreamResult(out), mimeType);
		}

		try {
			BrowserLauncher.openURL(preview.toURI().toString());
		} catch (Exception cannotOpen) {
			javax.swing.JOptionPane.showMessageDialog(parent,
					"The output was written to\n" + preview.getAbsolutePath()
					+ "\n\nbut this desktop could not open it: " + cannotOpen.getMessage(),
					"Preview", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		}
	}

	public void query(SAXSource input, StreamSource query, StreamResult output)
			throws TransformerException, IOException {
		// System.out.println( "ScenarioProcessor.query( "+input+", "+query+",
		// "+output+")");
		// System.out.println( "query: "+query.getSystemId());

		InputStreamReader queryReader = new InputStreamReader(query.getInputStream());
		boolean wrap = false;

		// >>> start
		Configuration config = new Configuration();
		StaticQueryContext xquery = new StaticQueryContext(config);
		XQueryExpression exp = null;

		try {
			exp = xquery.compileQuery(queryReader);
		} catch (XPathException err) {
			throw new TransformerException(err);
		}

		DynamicQueryContext dynamicContext = new DynamicQueryContext(config);

		if (input != null) {
			try {
				dynamicContext.setContextItem(config.buildDocumentTree(input).getRootNode());
			} catch (XPathException e) {
				throw new TransformerException(e);
			}
		}

		try {
			// The next line actually executes the query
			SequenceIterator results = exp.iterator(dynamicContext);

			Properties props = new Properties();
			// props.setProperty( OutputKeys.OMIT_XML_DECLARATION, "no");
			// props.setProperty( OutputKeys.INDENT, "yes");

			exp.run(dynamicContext, output, props);

			// if (wrap) {
			// DocumentInfo resultDoc = DynamicQueryContext.wrap( results,
			// NamePool.getDefaultNamePool());
			// QueryResult.serialize( resultDoc, output, outputProps);
			// output.getOutputStream().close();
			// } else {
			// PrintWriter writer = new PrintWriter( output.getOutputStream());
			// while ( true) {
			// Item item = results.next();
			//
			// if ( item == null) {
			// break;
			// }
			//
			// if ( item instanceof NodeInfo) {
			// switch ( ((NodeInfo)item).getNodeKind()) {
			// case Type.DOCUMENT:
			// case Type.ELEMENT:
			// // TODO: this is OK for constructed elements, but
			// // if the query retrieves an element from a source doc,
			// // we are outputting the whole document.
			// QueryResult.serialize((NodeInfo)item, new StreamResult( writer),
			// outputProps);
			// writer.println("");
			// break;
			// default:
			// writer.println(item.getStringValue());
			// }
			// }
			// }
			//
			// writer.close();
			// }
		} catch (XPathException err) {
			// Saxon 12.7: just rethrow XPathException as TransformerException
			throw new TransformerException(err);
		} catch (TransformerException err) {
			throw new TransformerException("Run-time errors were reported");
		}
	}

	private static class TransformationErrorListener implements ErrorListener {
		public void warning(TransformerException e) {
			System.err.println("WARNING: " + e.getMessageAndLocation());
		}

		public void error(TransformerException e) {
			System.err.println("ERROR: " + e.getMessageAndLocation());
		}

		public void fatalError(TransformerException e) {
			System.err.println("FATAL ERROR: " + e.getMessageAndLocation());
		}
	}

	/**
	 * The media type FOP renders for one of the scenario's output choices.
	 *
	 * <p>SVG is not one of them. FOP has no SVG renderer and has not had one since the 0.20 series, so asking for it
	 * gets an {@code UnsupportedOperationException} out of the renderer's guts. The dialogs no longer offer it; a
	 * scenario saved before they stopped still can, and this is what it meets instead.
	 */
	private static String mimeTypeFor(int type) throws FOPException {
		if (type == ScenarioProperties.FOP_TYPE_PS) {
			return MimeConstants.MIME_POSTSCRIPT;
		}
		if (type == ScenarioProperties.FOP_TYPE_TXT) {
			return MimeConstants.MIME_PLAIN_TEXT;
		}
		if (type == ScenarioProperties.FOP_TYPE_SVG) {
			throw new FOPException("This scenario asks for SVG, which the formatter cannot produce. "
					+ "Open the scenario and choose PDF, PostScript or text.");
		}
		return MimeConstants.MIME_PDF;
	}

	/** What to call a file of that media type. */
	private static String extensionFor(String mimeType) {
		if (MimeConstants.MIME_POSTSCRIPT.equals(mimeType)) {
			return ".ps";
		}
		if (MimeConstants.MIME_PLAIN_TEXT.equals(mimeType)) {
			return ".txt";
		}
		return ".pdf";
	}

	// public static class DummyEntityResolver implements EntityResolver {
	// public InputSource resolveEntity( String publicId, String systemId) throws
	// SAXException, IOException {
	// InputSource result = null;
	//
	// try {
	// URL url = null;
	//
	// try {
	// url = new URL( systemId);
	// } catch ( MalformedURLException x) {
	// url = new URL( "file", null, systemId);
	// }
	//
	// result = new InputSource( url.openStream());
	// } catch ( IOException e) {
	//// System.out.println( "publicId = "+publicId);
	// result = new InputSource( new StringReader( ""));
	//// System.err.println( "ERROR: The system cannot find the file specified
	// \""+systemId+"\"");
	// }
	//
	// return result;
	// }
	// }
}
