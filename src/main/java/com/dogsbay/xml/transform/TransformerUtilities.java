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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Vector;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * Utilities for transforming XML documents.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/10/18 16:08:08 $
 * @author Dogsbay
 */
public class TransformerUtilities {

	public static TransformationErrorListener listener = new TransformationErrorListener();
	/**
	 * Transforms the DogsBayDocument using the default xsl file 
	 *
	 * @param document the XML document.
	 * @param output the output stream which will contain the result.
	 */
	public static void transform( DogsBayDocument document, OutputStream output, boolean pis) throws TransformerException, SAXException, IOException { //, SAXException, IOException	{
		TransformerFactory factory = TransformerFactory.newInstance();
		com.dogsbay.xml.SecureXml.enableSecureProcessing( factory);
		factory.setErrorListener( listener);
		Source input = createSource( document);
		Source stylesheet = null;
		
		if ( pis) {
			stylesheet = factory.getAssociatedStylesheet( input, null, null, null);
		} 
		
		if ( stylesheet == null){
			stylesheet = new StreamSource( factory.getClass().getResourceAsStream( "com/dogsbay/xml/transform/resources/default.xsl"));
		}
		
		transform( input, stylesheet, null, new StreamResult( output));
	}

	private static void transform( Source document, Source stylesheet, Vector parameters, Result result) throws TransformerException {
	
		TransformerFactory factory = TransformerFactory.newInstance();
		com.dogsbay.xml.SecureXml.enableSecureProcessing( factory);
		factory.setErrorListener( listener);
		
		if ( stylesheet == null) {
			stylesheet = factory.getAssociatedStylesheet( document, null, null, null);
		}
		
		if ( stylesheet == null) { // no associated stylesheet in the document, use internal stylesheet!
			stylesheet = new StreamSource( factory.getClass().getResourceAsStream( "com/dogsbay/xml/transform/resources/default.xsl"));
		}
		
		Transformer transformer = factory.newTransformer( stylesheet);
		
		if ( parameters != null) {
			for ( int i = 0; i < parameters.size(); i++) {
				String[] parameter = (String[])parameters.elementAt(i);
				transformer.setParameter( parameter[0],	parameter[1]);
			}
		}
		
		transformer.transform( document, result);
	}
	
	public static String getPIStylesheetLocation( String inputSystemId) throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		com.dogsbay.xml.SecureXml.enableSecureProcessing( factory);
		factory.setErrorListener( listener);
		
		Source source = factory.getAssociatedStylesheet( new SAXSource( new InputSource(  URLUtilities.encodeURL( inputSystemId))), null, null, null);
		
		if ( source != null) {
			return source.getSystemId();
		} else {
			return null;
		}
	}

	private static SAXSource createSource( DogsBayDocument document) throws UnsupportedEncodingException, SAXException {
		// Characters, not bytes: encoding the buffer to the document's charset replaced anything
		// that charset could not represent with '?' before the transform ever ran.
		java.io.Reader reader = new java.io.StringReader( document.getText());

		URL url = document.getURL();
		String systemId = null;

		if ( url != null) {
			systemId = url.toString();
		} 

		return createSource( reader, systemId);
	}

	private static SAXSource createSource( java.io.Reader characters, String systemId) throws UnsupportedEncodingException, SAXException {
		InputSource input = new InputSource( characters);
		SAXSource source = new SAXSource( input);

		input.setSystemId( systemId);
		source.setSystemId( systemId);

		XMLReader reader = XMLReaderFactory.createXMLReader();
		reader.setFeature( "http://xml.org/sax/features/validation", false);
		reader.setEntityResolver( XMLUtilities.getCatalogResolver());
		source.setXMLReader( reader);
		
		return source;
	}

	private static class TransformationErrorListener implements ErrorListener {
		public void warning( TransformerException e) {
			System.err.println( "WARNING: "+e.getMessageAndLocation());
		}

		public void error( TransformerException e) {
			System.err.println( "ERROR: "+e.getMessageAndLocation());
		}

		public void fatalError( TransformerException e) {
			System.err.println( "FATAL ERROR: "+e.getMessageAndLocation());
		}
	}

} 
