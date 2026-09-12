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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.net.URL;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.bounce.QIcon;
import org.bounce.image.ImageUtilities;

import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Creates Icons if a specific icon has not been created yet.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/13 18:32:33 $
 * @author Dogsbay
 */
public class IconFactory {
	public static ConfigurationProperties properties = null;

	public static final String XML_STATUS_NORMAL		= "Normal";
	public static final String XML_STATUS_WELLFORMED	= "Wellformed";
	public static final String XML_STATUS_VALID			= "Valid";
	public static final String XML_STATUS_ERROR			= "Error";
	public static final String XML_STATUS_UNKNOWN		= "Unknown";

	public static final String DOCUMENT_TYPE_XML 		= "XML";
	public static final String DOCUMENT_TYPE_DTD 		= "DTD";
	public static final String DOCUMENT_TYPE_UNKNOWN 	= "Unknown";

	public static final String FILE_STATUS_NORMAL		= "Normal";
	public static final String FILE_STATUS_READ_ONLY	= "Read Only";
	public static final String FILE_STATUS_REMOTE		= "Remote";
	
	private static final String DIRECTORY_ICON 		= "com/dogsbay/dogsbayaieditor/icons/filetype-folder.png";

	private static final String DOCUMENT_ICON 		= "com/dogsbay/dogsbayaieditor/icons/filetype-default.png";
	private static final String XML_DOCUMENT_ICON	= "com/dogsbay/dogsbayaieditor/icons/filetype-xml.png";
	private static final String DTD_DOCUMENT_ICON	= "com/dogsbay/dogsbayaieditor/icons/filetype-xml.png";

	// Seti UI file-type icons
	private static final String MARKDOWN_DOCUMENT_ICON	= "com/dogsbay/dogsbayaieditor/icons/filetype-markdown.png";
	private static final String JSON_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-json.png";
	private static final String YAML_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-yml.png";
	private static final String HTML_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-html.png";
	private static final String CSS_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-css.png";
	private static final String JS_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-javascript.png";
	private static final String TS_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-typescript.png";
	private static final String IMAGE_DOCUMENT_ICON		= "com/dogsbay/dogsbayaieditor/icons/filetype-image.png";
	private static final String FOLDER_ICON				= "com/dogsbay/dogsbayaieditor/icons/filetype-folder.png";
	
	private static final String ERROR_OVERLAY_ICON 		= "com/dogsbay/dogsbayaieditor/icons/ErrorOverlayIcon.gif";
	private static final String UNKNOWN_OVERLAY_ICON	= "com/dogsbay/dogsbayaieditor/icons/UnknownOverlayIcon.gif";
	private static final String REMOTE_OVERLAY_ICON		= "com/dogsbay/dogsbayaieditor/icons/RemoteOverlayIcon.gif";
	private static final String READ_ONLY_OVERLAY_ICON	= "com/dogsbay/dogsbayaieditor/icons/ReadOnlyOverlayIcon.gif";
	private static final String VALID_OVERLAY_ICON 		= "com/dogsbay/dogsbayaieditor/icons/ValidOverlayIcon.gif";
	private static final String WELLFORMED_OVERLAY_ICON = "com/dogsbay/dogsbayaieditor/icons/WellformedOverlayIcon.gif";

	private static ImageIcon directoryIcon		= null;

	private static ImageIcon documentIcon		= null;
	private static ImageIcon xmlDocumentIcon	= null;
	private static ImageIcon dtdDocumentIcon 	= null;

	private static ImageIcon markdownDocumentIcon	= null;
	private static ImageIcon jsonDocumentIcon		= null;
	private static ImageIcon yamlDocumentIcon		= null;
	private static ImageIcon htmlDocumentIcon		= null;
	private static ImageIcon cssDocumentIcon			= null;
	private static ImageIcon jsDocumentIcon			= null;
	private static ImageIcon tsDocumentIcon			= null;
	private static ImageIcon imageDocumentIcon		= null;
	private static ImageIcon folderIcon				= null;

	private static ImageIcon errorOverlayIcon		= null;
	private static ImageIcon validOverlayIcon		= null;
	private static ImageIcon wellformedOverlayIcon	= null;
	private static ImageIcon unknownOverlayIcon		= null;
	private static ImageIcon remoteOverlayIcon		= null;
	private static ImageIcon readonlyOverlayIcon	= null;

	// ED FIX: the darker issue should be fixed using a new ImageIcon 
	// filter that can merge 2 icons...
	private static ImageIcon darkerDocumentIcon		= null;
	private static ImageIcon darkerXMLDocumentIcon	= null;
	private static ImageIcon darkerDTDDocumentIcon 	= null;

	private static ImageIcon darkerErrorOverlayIcon		= null;
	private static ImageIcon darkerValidOverlayIcon		= null;
	private static ImageIcon darkerWellformedOverlayIcon= null;
	private static ImageIcon darkerUnknownOverlayIcon	= null;
	private static ImageIcon darkerRemoteOverlayIcon	= null;
	private static ImageIcon darkerReadonlyOverlayIcon	= null;

	private static Hashtable iconStore = new Hashtable();

	public static void setProperties( ConfigurationProperties props) {
		properties = props;
	}
	
	public static void reset() {
		iconStore = new Hashtable();
	}

	public static Icon getIconForExtension( String extension) {
//		System.out.println( "IconFactory.getIconForExtension( "+extension+")");
	
		if ( extension != null) {
			// Check well-known file types first
			ImageIcon knownIcon = getKnownExtensionIcon( extension);
			if ( knownIcon != null) {
				return knownIcon;
			}

			// Then check grammar-defined types
			Vector types = properties.getGrammarProperties();
			for ( int i = 0; i < types.size(); i++) {
				GrammarProperties type = (GrammarProperties)types.elementAt(i);
				Vector extensions = FileUtilities.getExtensions( type);
				for ( int j = 0; j < extensions.size(); j++) {
					if ( ((String)extensions.elementAt(j)).equalsIgnoreCase( extension)) {
						return getIconForType( type);
					}
				}
			}
		}

		return getDocumentIcon();
	}

	public static Icon getDarkerIconForExtension( String extension) {
		if ( extension != null) {
			ImageIcon knownIcon = getKnownExtensionIcon( extension);
			if ( knownIcon != null) {
				return ImageUtilities.createDarkerImage( knownIcon);
			}

			Vector types = properties.getGrammarProperties();
			for ( int i = 0; i < types.size(); i++) {
				GrammarProperties type = (GrammarProperties)types.elementAt(i);
				Vector extensions = FileUtilities.getExtensions( type);
				for ( int j = 0; j < extensions.size(); j++) {
					if ( ((String)extensions.elementAt(j)).equalsIgnoreCase( extension)) {
						return getDarkerIconForType( type);
					}
				}
			}
		}

		return getDarkerDocumentIcon();
	}

	private static ImageIcon getKnownExtensionIcon( String ext) {
		if ( ext.equalsIgnoreCase( "xml") || ext.equalsIgnoreCase( "xsd") || ext.equalsIgnoreCase( "xsl") || ext.equalsIgnoreCase( "xslt") || ext.equalsIgnoreCase( "dogsbay") || ext.equalsIgnoreCase( "type") || ext.equalsIgnoreCase( "types") || ext.equalsIgnoreCase( "scenario") || ext.equalsIgnoreCase( "scenarios") || ext.equalsIgnoreCase( "template") || ext.equalsIgnoreCase( "templates") || ext.equalsIgnoreCase( "dita") || ext.equalsIgnoreCase( "ditamap") || ext.equalsIgnoreCase( "svg")) {
			return getXMLDocumentIcon();
		} else if ( ext.equalsIgnoreCase( "dtd") || ext.equalsIgnoreCase( "mod") || ext.equalsIgnoreCase( "ent")) {
			return getDTDDocumentIcon();
		} else if ( ext.equalsIgnoreCase( "md") || ext.equalsIgnoreCase( "markdown")) {
			return getIconCached( "markdown", MARKDOWN_DOCUMENT_ICON, markdownDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "json")) {
			return getIconCached( "json", JSON_DOCUMENT_ICON, jsonDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "yml") || ext.equalsIgnoreCase( "yaml")) {
			return getIconCached( "yaml", YAML_DOCUMENT_ICON, yamlDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "html") || ext.equalsIgnoreCase( "htm") || ext.equalsIgnoreCase( "xhtml")) {
			return getIconCached( "html", HTML_DOCUMENT_ICON, htmlDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "css") || ext.equalsIgnoreCase( "scss") || ext.equalsIgnoreCase( "less")) {
			return getIconCached( "css", CSS_DOCUMENT_ICON, cssDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "js") || ext.equalsIgnoreCase( "mjs") || ext.equalsIgnoreCase( "cjs")) {
			return getIconCached( "js", JS_DOCUMENT_ICON, jsDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "ts") || ext.equalsIgnoreCase( "tsx")) {
			return getIconCached( "ts", TS_DOCUMENT_ICON, tsDocumentIcon);
		} else if ( ext.equalsIgnoreCase( "png") || ext.equalsIgnoreCase( "jpg") || ext.equalsIgnoreCase( "jpeg") || ext.equalsIgnoreCase( "gif") || ext.equalsIgnoreCase( "bmp") || ext.equalsIgnoreCase( "svg") || ext.equalsIgnoreCase( "ico")) {
			return getIconCached( "image", IMAGE_DOCUMENT_ICON, imageDocumentIcon);
		}
		return null;
	}

	private static ImageIcon getIconCached( String key, String path, ImageIcon cached) {
		if ( cached != null) return cached;
		ImageIcon icon = DogsBayImageLoader.get().getImage( path);
		// Store back in static field via iconStore to avoid reloading
		iconStore.put( "seti:" + key, icon);
		return icon;
	}

	public static Icon getIconForType( GrammarProperties type) {
		String iconLocation = type.getIconLocation();

//		System.out.println( "IconFactory.getIconForType( "+type.getDescription()+") ["+iconLocation+"]");
		
		if ( iconLocation != null && iconLocation.length() > 0) {
			ImageIcon icon = null;

			try {
				icon = DogsBayImageLoader.get().getImage( new URL( iconLocation));

				if ( icon.getIconWidth() != 16 || icon.getIconHeight() != 16) {
					icon = new ImageIcon( icon.getImage().getScaledInstance( 16, 16, Image.SCALE_SMOOTH));
				}
			} catch ( Exception e) {
				icon = null;
			}
			
			if ( icon != null) {
				return icon;
			} else {
				System.out.println( "Could not find Icon : "+iconLocation);
			}
		}
		
		return getXMLDocumentIcon();
	}

	public static Icon getDarkerIconForType( GrammarProperties type) {
		String iconLocation = type.getIconLocation();

//		System.out.println( "IconFactory.getDarkerIconForType( "+type.getDescription()+") ["+iconLocation+"]");
		
		if ( iconLocation != null && iconLocation.length() > 0) {
			ImageIcon icon = null;

			try {
				icon = DogsBayImageLoader.get().getImage( new URL( iconLocation));

				if ( icon.getIconWidth() != 16 || icon.getIconHeight() != 16) {
					icon = new ImageIcon( icon.getImage().getScaledInstance( 16, 16, Image.SCALE_SMOOTH));
				}
			} catch ( Exception e) {
				icon = null;
			}
			
			if ( icon != null) {
				icon = ImageUtilities.createDarkerImage( icon);

 				return icon;
			} else {
				System.out.println( "Could not find darker Icon : "+iconLocation);
			}
		}
		
		return getDarkerXMLDocumentIcon();
	}

	public static Icon getIcon( String type, String fileStatus, String xmlStatus) {
		QIcon icon = (QIcon)iconStore.get( type+fileStatus+xmlStatus);
		
		if ( icon == null) {
			// create a new icon.
			if ( type == DOCUMENT_TYPE_XML) {
				icon = new QIcon( getXMLDocumentIcon());
				
				overlayFileStatus( icon, fileStatus);
				overlayXMLStatus( icon, xmlStatus);
			} else if ( type == DOCUMENT_TYPE_DTD) {
				icon = new QIcon( getDTDDocumentIcon());
				
				overlayFileStatus( icon, fileStatus);
			} else {
				icon = new QIcon( getDocumentIcon());

				overlayFileStatus( icon, fileStatus);
			}
			
			iconStore.put( type+fileStatus+xmlStatus, icon);
		}
		
		return icon;
	}

	public static Icon getDarkerIcon( String type, String fileStatus, String xmlStatus) {
		QIcon icon = (QIcon)iconStore.get( "dark:"+type+fileStatus+xmlStatus);
		
		if ( icon == null) {
			// create a new icon.
			if ( type == DOCUMENT_TYPE_XML) {
				icon = new QIcon( getDarkerXMLDocumentIcon());
				
				overlayDarkerFileStatus( icon, fileStatus);
				overlayDarkerXMLStatus( icon, xmlStatus);
			} else if ( type == DOCUMENT_TYPE_DTD) {
				icon = new QIcon( getDarkerDTDDocumentIcon());
				
				overlayDarkerFileStatus( icon, fileStatus);
			} else {
				icon = new QIcon( getDarkerDocumentIcon());

				overlayDarkerFileStatus( icon, fileStatus);
			}
			
			iconStore.put( "dark:"+type+fileStatus+xmlStatus, icon);
		}
		
		return icon;
	}

	public static Icon getDarkerIconForExtension( String extension, String fileStatus) {
		QIcon icon = (QIcon)iconStore.get( "dark:"+extension+fileStatus);
		
		if ( icon == null) {
			icon = new QIcon( getDarkerIconForExtension( extension));

			// create a new icon.
			overlayDarkerFileStatus( icon, fileStatus);
//			overlayDarkerXMLStatus( icon, xmlStatus);

			iconStore.put( "dark:"+extension+fileStatus, icon);
		}
		
		return icon;
	}

	public static Icon getDarkerIconForExtension( String extension, String fileStatus, String xmlStatus) {
		QIcon icon = (QIcon)iconStore.get( "dark:"+extension+fileStatus+xmlStatus);
		
		if ( icon == null) {
			icon = new QIcon( getDarkerIconForExtension( extension));

			// create a new icon.
			overlayDarkerFileStatus( icon, fileStatus);
			overlayDarkerXMLStatus( icon, xmlStatus);

			iconStore.put( "dark:"+extension+fileStatus+xmlStatus, icon);
		}
		
		return icon;
	}

	public static Icon getDarkerIconForType( String typeId, String fileStatus, String xmlStatus) {
//		System.out.println( "getDarkerIconForType( "+typeId+", "+fileStatus+", "+xmlStatus+")");
		Vector types = properties.getGrammarProperties();
		GrammarProperties type = null;
		
		for ( int i = 0; i < types.size(); i++) {
			GrammarProperties temp = (GrammarProperties)types.elementAt(i);
			
			if ( temp.getID().equals( typeId)) {
				type = temp;
				break;
			}
		}
		
		return getDarkerIconForType( type, fileStatus, xmlStatus);
	}

	public static Icon getDarkerIconForType( GrammarProperties type, String fileStatus, String xmlStatus) {
//		System.out.println( "getDarkerIconForType( "+type+", "+fileStatus+", "+xmlStatus+")");
		QIcon icon = null;

		if ( type != null) {
			icon = (QIcon)iconStore.get( "dark:"+type.getID()+fileStatus+xmlStatus);

			if ( icon == null) {
				icon = new QIcon( getDarkerIconForType( type));

				// create a new icon.
				overlayDarkerFileStatus( icon, fileStatus);
				overlayDarkerXMLStatus( icon, xmlStatus);

				iconStore.put( "dark:"+type.getID()+fileStatus+xmlStatus, icon);
			}
		} else {
			icon = new QIcon( getDarkerXMLDocumentIcon());
		}
		
		return icon;
	}

	public static Icon getIconForExtension( String extension, String fileStatus) {
//		System.out.println( "getIconForExtension( "+extension+", "+fileStatus+")");
		QIcon icon = (QIcon)iconStore.get( extension+fileStatus);
		
		if ( icon == null) {
			icon = new QIcon( getIconForExtension( extension));

			// create a new icon.
			overlayFileStatus( icon, fileStatus);
//			overlayXMLStatus( icon, xmlStatus);

			iconStore.put( extension+fileStatus, icon);
		}
		
		return icon;
	}

	public static Icon getIconForExtension( String extension, String fileStatus, String xmlStatus) {
//		System.out.println( "getIconForExtension( "+extension+", "+fileStatus+")");
		QIcon icon = (QIcon)iconStore.get( extension+fileStatus+xmlStatus);
		
		if ( icon == null) {
			icon = new QIcon( getIconForExtension( extension));

			// create a new icon.
			overlayFileStatus( icon, fileStatus);
			overlayXMLStatus( icon, xmlStatus);

			iconStore.put( extension+fileStatus+xmlStatus, icon);
		}
		
		return icon;
	}

	public static Icon getIconForType( String typeId, String fileStatus, String xmlStatus) {
//		System.out.println( "getIconForType( "+typeId+", "+fileStatus+", "+xmlStatus+")");

		Vector types = properties.getGrammarProperties();
		GrammarProperties type = null;
		
		for ( int i = 0; i < types.size(); i++) {
			GrammarProperties temp = (GrammarProperties)types.elementAt(i);
			
			if ( temp.getID().equals( typeId)) {
				type = temp;
				break;
			}
		}
		
		return getIconForType( type, fileStatus, xmlStatus);
	}

	public static Icon getIconForType( GrammarProperties type, String fileStatus, String xmlStatus) {
		QIcon icon = null;

		if ( type != null) {
//			System.out.println( "getIconForType( "+type.getDescription()+", "+fileStatus+", "+xmlStatus+")");
			icon = (QIcon)iconStore.get( type.getID()+fileStatus+xmlStatus);

			if ( icon == null) {
				icon = new QIcon( getIconForType( type));

				// create a new icon.
				overlayFileStatus( icon, fileStatus);
				overlayXMLStatus( icon, xmlStatus);

				iconStore.put( type.getID()+fileStatus+xmlStatus, icon);
			}
		} else {
			icon = (QIcon)iconStore.get( fileStatus+xmlStatus);

			if ( icon == null) {
				icon = new QIcon( getXMLDocumentIcon());

				overlayFileStatus( icon, fileStatus);
				overlayXMLStatus( icon, xmlStatus);
	
				iconStore.put( fileStatus+xmlStatus, icon);
			}
		}
		
		return icon;
	}

//	public static Icon getIcon( String fileStatus, Grammar type, String xmlStatus) {
//	
//	}


	private static void overlayFileStatus( QIcon icon, String fileStatus) {
		if ( fileStatus == FILE_STATUS_READ_ONLY) {
			icon.addOverlayIcon( getReadOnlyOverlayIcon(), QIcon.SOUTH_WEST);
		} else if ( fileStatus == FILE_STATUS_REMOTE) {
			icon.addOverlayIcon( getRemoteOverlayIcon(), QIcon.SOUTH_WEST);
		}
	}

	private static void overlayXMLStatus( QIcon icon, String xmlStatus) {
		if ( xmlStatus == XML_STATUS_ERROR) {
			icon.addOverlayIcon( getErrorOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_UNKNOWN) {
			icon.addOverlayIcon( getUnknownOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_WELLFORMED) {
			icon.addOverlayIcon( getWellformedOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_VALID) {
			icon.addOverlayIcon( getValidOverlayIcon(), QIcon.SOUTH_EAST);
		}
	}

	private static void overlayDarkerFileStatus( QIcon icon, String fileStatus) {
		if ( fileStatus == FILE_STATUS_READ_ONLY) {
			icon.addOverlayIcon( getDarkerReadOnlyOverlayIcon(), QIcon.SOUTH_WEST);
		} else if ( fileStatus == FILE_STATUS_REMOTE) {
			icon.addOverlayIcon( getDarkerRemoteOverlayIcon(), QIcon.SOUTH_WEST);
		}
	}

	private static void overlayDarkerXMLStatus( QIcon icon, String xmlStatus) {
		if ( xmlStatus == XML_STATUS_ERROR) {
			icon.addOverlayIcon( getDarkerErrorOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_UNKNOWN) {
			icon.addOverlayIcon( getDarkerUnknownOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_WELLFORMED) {
			icon.addOverlayIcon( getDarkerWellformedOverlayIcon(), QIcon.SOUTH_EAST);
		} else if ( xmlStatus == XML_STATUS_VALID) {
			icon.addOverlayIcon( getDarkerValidOverlayIcon(), QIcon.SOUTH_EAST);
		}
	}

	public static ImageIcon getDocumentIcon() {
		if ( documentIcon == null) {
			documentIcon = DogsBayImageLoader.get().getImage( DOCUMENT_ICON);
		}

		return documentIcon;
	}

	public static ImageIcon getDirectoryIcon() {
		if ( directoryIcon == null) {
			directoryIcon = DogsBayImageLoader.get().getImage( DIRECTORY_ICON);
		}

		return directoryIcon;
	}

	public static ImageIcon getXMLDocumentIcon() {
		if ( xmlDocumentIcon == null) {
			xmlDocumentIcon = DogsBayImageLoader.get().getImage( XML_DOCUMENT_ICON);
		}

		return xmlDocumentIcon;
	}

	public static ImageIcon getDTDDocumentIcon() {
		if ( dtdDocumentIcon == null) {
			dtdDocumentIcon = DogsBayImageLoader.get().getImage( DTD_DOCUMENT_ICON);
		}

		return dtdDocumentIcon;
	}

	private static ImageIcon getErrorOverlayIcon() {
		if ( errorOverlayIcon == null) {
			errorOverlayIcon = DogsBayImageLoader.get().getImage( ERROR_OVERLAY_ICON);
		}

		return errorOverlayIcon;
	}

	private static ImageIcon getWellformedOverlayIcon() {
		if ( wellformedOverlayIcon == null) {
			wellformedOverlayIcon = DogsBayImageLoader.get().getImage( WELLFORMED_OVERLAY_ICON);
		}

		return wellformedOverlayIcon;
	}

	private static ImageIcon getValidOverlayIcon() {
		if ( validOverlayIcon == null) {
			validOverlayIcon = DogsBayImageLoader.get().getImage( VALID_OVERLAY_ICON);
		}

		return validOverlayIcon;
	}

	private static ImageIcon getUnknownOverlayIcon() {
		if ( unknownOverlayIcon == null) {
			unknownOverlayIcon = DogsBayImageLoader.get().getImage( UNKNOWN_OVERLAY_ICON);
		}

		return unknownOverlayIcon;
	}

	private static ImageIcon getRemoteOverlayIcon() {
		if ( remoteOverlayIcon == null) {
			remoteOverlayIcon = DogsBayImageLoader.get().getImage( REMOTE_OVERLAY_ICON);
		}

		return remoteOverlayIcon;
	}

	private static ImageIcon getReadOnlyOverlayIcon() {
		if ( readonlyOverlayIcon == null) {
			readonlyOverlayIcon = DogsBayImageLoader.get().getImage( READ_ONLY_OVERLAY_ICON);
		}

		return readonlyOverlayIcon;
	}

	private static ImageIcon getDarkerDocumentIcon() {
		if ( darkerDocumentIcon == null) {
			darkerDocumentIcon = ImageUtilities.createDarkerImage( getDocumentIcon());
		}

		return darkerDocumentIcon;
	}

	private static ImageIcon getDarkerXMLDocumentIcon() {
		if ( darkerXMLDocumentIcon == null) {
			darkerXMLDocumentIcon = ImageUtilities.createDarkerImage( getXMLDocumentIcon());
		}

		return darkerXMLDocumentIcon;
	}

	private static ImageIcon getDarkerDTDDocumentIcon() {
		if ( darkerDTDDocumentIcon == null) {
			darkerDTDDocumentIcon = ImageUtilities.createDarkerImage( getDTDDocumentIcon());
		}

		return darkerDTDDocumentIcon;
	}

	private static ImageIcon getDarkerErrorOverlayIcon() {
		if ( darkerErrorOverlayIcon == null) {
			darkerErrorOverlayIcon = ImageUtilities.createDarkerImage( getErrorOverlayIcon());
		}

		return darkerErrorOverlayIcon;
	}

	private static ImageIcon getDarkerWellformedOverlayIcon() {
		if ( darkerWellformedOverlayIcon == null) {
			darkerWellformedOverlayIcon = ImageUtilities.createDarkerImage( getWellformedOverlayIcon());
		}

		return darkerWellformedOverlayIcon;
	}

	private static ImageIcon getDarkerValidOverlayIcon() {
		if ( darkerValidOverlayIcon == null) {
			darkerValidOverlayIcon = ImageUtilities.createDarkerImage( getValidOverlayIcon());
		}

		return darkerValidOverlayIcon;
	}

	private static ImageIcon getDarkerUnknownOverlayIcon() {
		if ( darkerUnknownOverlayIcon == null) {
			darkerUnknownOverlayIcon = ImageUtilities.createDarkerImage( getUnknownOverlayIcon());
		}

		return darkerUnknownOverlayIcon;
	}

	private static ImageIcon getDarkerRemoteOverlayIcon() {
		if ( darkerRemoteOverlayIcon == null) {
			darkerRemoteOverlayIcon = ImageUtilities.createDarkerImage( getRemoteOverlayIcon());
		}

		return darkerRemoteOverlayIcon;
	}

	private static ImageIcon getDarkerReadOnlyOverlayIcon() {
		if ( darkerReadonlyOverlayIcon == null) {
			darkerReadonlyOverlayIcon = ImageUtilities.createDarkerImage( getReadOnlyOverlayIcon());
		}

		return darkerReadonlyOverlayIcon;
	}
	
	public static Icon getEmptyIcon( int width, int height) {
		return new EmptyIcon( width, height);
	}

	public static class EmptyIcon extends Object implements Icon {        
		private int height;    
		private int width;        
		
		public EmptyIcon( Dimension size) {        
			this.height = size.height;        
			this.width = size.width;    
		}       
		
		public EmptyIcon( int width, int height) {        
			this.height = height;        
			this.width = width;    
		}        
		
		public int getIconHeight() {
			return height;    
		}        
		
		public int getIconWidth() {
			return width;    
		}        
		
		public void paintIcon(Component c, Graphics g, int x, int y) {    
		}
	}	
} 
