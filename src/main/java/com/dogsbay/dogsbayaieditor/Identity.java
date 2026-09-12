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

/**
 * The version and name of the DogsBay XML application.
 *
 * Sets 6 System-properties for general use:<br/>
 * <code>dogsbayaieditor.title</code><br/>
 * <code>dogsbayaieditor.vendor</code><br/>
 * <code>dogsbayaieditor.reference</code><br/>
 * <code>dogsbayaieditor.version</code><br/>
 * <code>dogsbayaieditor.description</code><br/>
 * <code>dogsbayaieditor.copyright</code><br/>
 *
 * @version	$Revision: 1.7 $, $Date: 2005/09/05 13:55:11 $
 * @author Dogsbay
 */
public class Identity {
	private static Identity identity = null;
	
	public static final String XMLPLUS_TITLE_PROPERTY 		= "dogsbayaieditor.title";
	public static final String XMLPLUS_VENDOR_PROPERTY		= "dogsbayaieditor.vendor";
	public static final String XMLPLUS_REFERENCE_PROPERTY	= "dogsbayaieditor.reference";
	public static final String XMLPLUS_VERSION_PROPERTY		= "dogsbayaieditor.version";
	public static final String XMLPLUS_DESCRIPTION_PROPERTY	= "dogsbayaieditor.description";
	public static final String XMLPLUS_COPYRIGHT_PROPERTY	= "dogsbayaieditor.copyright";
	public static final String XMLPLUS_EDITION_PROPERTY	= "dogsbayaieditor.edition";
	
	public static final String XMLPLUS_EDITION_LITE = "Lite";
	public static final String XMLPLUS_EDITION_PROFESSIONAL = "Professional";
	public static final String XMLPLUS_EDITION_ENTERPRISE= "Enterprise";
	
	/**
	 * Gets the one version of the identity.
	 */
	public static Identity getIdentity() {
		if ( identity == null) {
			identity = new Identity();
		}
		
		return identity;
	}
	
	/**
	 * Sets the identity of the application as System properties...
	 */
	private Identity() {
		System.setProperty( XMLPLUS_TITLE_PROPERTY, "DogsBay XML");
		System.setProperty( XMLPLUS_VENDOR_PROPERTY, "DogsBay Ltd.");
		System.setProperty( XMLPLUS_REFERENCE_PROPERTY, "https://dogsbay.ai");
		// Prefer the Implementation-Version baked into the JAR manifest at
		// build time (kept in sync with the released artifact, including the
		// pre-release qualifier). Fall back to a literal so dev runs from an
		// IDE — where the class is loaded from disk, not a versioned JAR —
		// still get a sensible string.
		String mfVersion = Identity.class.getPackage().getImplementationVersion();
		System.setProperty( XMLPLUS_VERSION_PROPERTY, mfVersion != null ? mfVersion : "4.0.0-beta.1");
		System.setProperty( XMLPLUS_DESCRIPTION_PROPERTY, "AI-Powered Document Editor");
		System.setProperty( XMLPLUS_COPYRIGHT_PROPERTY, "Copyright 2002 - 2026 \u00a9 DogsBay Ltd.");
		System.setProperty( XMLPLUS_EDITION_PROPERTY, XMLPLUS_EDITION_PROFESSIONAL );
		//System.setProperty( XMLPLUS_EDITION_PROPERTY, XMLPLUS_EDITION_LITE );
	}

	/**
	 * Gets the product's title.
	 */
	public String getTitle()  {
		return System.getProperty( XMLPLUS_TITLE_PROPERTY);
	}

	/**
	 * Gets the vendor's name.
	 */
	public String getVendor() {
		return System.getProperty( XMLPLUS_VENDOR_PROPERTY);
	}

	/**
	 * Gets a reference, ie. the products home page.
	 */
	public String getReference() {
		return System.getProperty( XMLPLUS_REFERENCE_PROPERTY);
	}
	
	
	/**
	 * Gets the version number for the product.
	 */
	public String getVersion() {
		return System.getProperty( XMLPLUS_VERSION_PROPERTY);
	}



	/**
	 * Gets a description for the product.
	 */
	public String getDescription() {
		return System.getProperty( XMLPLUS_DESCRIPTION_PROPERTY);
	}

	/**
	 * Gets the copyright information for the product.
	 */
	public String getCopyright() {
		return System.getProperty( XMLPLUS_COPYRIGHT_PROPERTY);
	}
	
	/**
	 * Gets the product's edition.
	 */
	public String getEdition()  {
		return System.getProperty( XMLPLUS_EDITION_PROPERTY);
	}


} 
