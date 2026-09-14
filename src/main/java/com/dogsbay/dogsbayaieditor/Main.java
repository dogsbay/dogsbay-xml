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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.bounce.net.DefaultAuthenticator;
import org.xml.sax.SAXParseException;

import com.dogsbay.util.loader.ExtensionClassLoader;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XDocumentFactory;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.transform.ScenarioUtilities;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
// import com.dogsbay.xslt.debugger.ui.XSLTDebuggerFrame;

/**
 * The main class, used to start the XML+ editor application.
 *
 * @version $Revision: 1.22 $, $Date: 2005/09/05 13:55:11 $
 * @author Dogsbay
 */
public class Main {
	static final int XMLPLUS_PORT = 9601;
	/** How long to look for a running instance before starting a new one. */
	static final int SINGLE_INSTANCE_CONNECT_TIMEOUT_MS = 500;

	private static final boolean DEBUG = true;
	private static DefaultAuthenticator authenticator = null;

	public static final String PLUGINS_LOCATION = "plugins";

	public static final String XMLPLUS_HOME = System.getProperty("user.home") + File.separator + ".dogsbay"
			+ File.separator;

	public static final String DOGSBAY_HOME = System.getProperty("user.home") + File.separator + ".dogsbay"
			+ File.separator;
	/**
	 * The settings document. Named for what it is, with the format's version
	 * inside it — see {@link com.dogsbay.dogsbayaieditor.properties.SettingsFile}.
	 */
	public static final String PROPERTIES_FILE = "settings.xml";

	private ConfigurationProperties properties = null;

	private DogsBayAIEditor editor = null;
	// private XSLTDebuggerFrame debugger = null;

	/**
	 * Constructor for the main XML+ class, constructs the main frame
	 * and shows the Splash screen until the application has been completely loaded.
	 */
	public Main(String file) {
		launch(null, file);
	}

	public Main(ExtensionClassLoader loader, String file) {
		if (DEBUG)
			System.out.println("Main::Main(loader, file): (" + loader + ", " + file + ")");
		launch(loader, file);
		Thread.currentThread().setContextClassLoader(loader);
	}

	private void start(ExtensionClassLoader loader, String file) {
		if (DEBUG)
			System.out.println("Main::start(loader, file): (" + loader + ", " + file + ")");
		if (!isDebug()) {
			redirectOutput();
		} else {
			System.out.println("******************* WARNING *******************");
			System.out.println("***** DogsBay XML Debug Version! *****");
			System.out.println("*****      Do not use for Production.     *****");
			System.out.println("***********************************************\n\n");
		}

		properties = getProperties();

		try {
			// Register FlatLaf themes
			UIManager.installLookAndFeel("FlatLaf Light", "com.formdev.flatlaf.FlatLightLaf");
			UIManager.installLookAndFeel("FlatLaf Dark", "com.formdev.flatlaf.FlatDarkLaf");
			UIManager.installLookAndFeel("FlatLaf IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
			UIManager.installLookAndFeel("FlatLaf Darcula", "com.formdev.flatlaf.FlatDarculaLaf");

			String laf = properties.getLookAndFeel();

			if (laf != null && laf.length() > 0) {
				UIManager.setLookAndFeel(laf);
			} else {
				// Default to FlatLaf Light for new installations
				com.formdev.flatlaf.FlatLightLaf.setup();
			}
		} catch (Exception e) {
			e.printStackTrace();
			// Fall back to system L&F if FlatLaf fails
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				// ignore
			}
		}

		// Compact tabs with dividers
		UIManager.put("TabbedPane.showTabSeparators", true);
		UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(4, 8, 4, 8));

		// Now that L&F is set, update syntax highlighting colors for the current theme
		properties.getTextPreferences().updateColorsForCurrentTheme();

		ClassLoader classLoader = getClass().getClassLoader();

		if (classLoader instanceof ExtensionClassLoader) {
			Vector extensions = properties.getExtensions();

			for (int i = 0; i < extensions.size(); i++) {
				((ExtensionClassLoader) classLoader).addExtension(new File((String) extensions.elementAt(i)));
			}
		}

		Vector catalogs = properties.getCatalogs();
		StringBuffer catalogFiles = new StringBuffer();

		for (int i = 0; i < catalogs.size(); i++) {
			catalogFiles.append((String) catalogs.elementAt(i));
			catalogFiles.append(";");
		}

		System.setProperty("xml.catalog.ignoreMissing", "true");
		System.setProperty("xml.catalog.files", catalogFiles.toString());

		if (properties.isPreferPublicIdentifiers()) {
			System.setProperty("xml.catalog.prefer", "public");
		} else {
			System.setProperty("xml.catalog.prefer", "system");
		}

		String port = properties.getProxyPort();
		String host = properties.getProxyHost();

		if (properties.isUseProxy()) {
			System.setProperty("http.proxyHost", host);
			System.setProperty("http.proxyPort", port);
		}

		String browser = properties.getBrowser();

		if (browser != null && browser.trim().length() > 0) {
			System.setProperty("org.bounce.browser", browser);
		}

		System.setProperty("javax.xml.transform.TransformerFactory", properties.getXSLTProcessor());
		System.setProperty("org.xml.sax.driver", "org.apache.xerces.parsers.SAXParser");
		System.setProperty("http.agent", Identity.getIdentity().getTitle() + "/" + Identity.getIdentity().getVersion());

		XDocumentFactory.getInstance().setXPathNamespaceURIs(properties.getPrefixNamespaceMappings());

		XMLUtilities.setLoadDTDGrammar(properties.isLoadDTDGrammar());
		// XMLUtilities.setResolveEntities( properties.isResolveEntities());

		IconFactory.setProperties(properties);

		editor = new DogsBayAIEditor(loader, properties);
		// PHASE 3: Debugger disabled during Saxon upgrade
		// debugger = new XSLTDebuggerFrame(properties, editor);
		// editor.setDebugger(debugger);
		// editor.setExtensionClassLoader(loader);
		if (DEBUG)
			System.out.println("Main::Start - extensionClassLoader: " + editor.getExtensionClassLoader());
		if (DEBUG)
			System.out.println("Main::Start - editor.getClassLoader(): " + editor.getClassLoader());

		// set network properties...
		Authenticator.setDefault(createDefaultAuthenticator(editor));

		MessageHandler.init(editor);
		FileUtilities.init(editor, editor, properties);
		ScenarioUtilities.init(editor, properties);

		// >>>
		// checkLicense( file, true);
		startApplication(file);
	}

	public static DefaultAuthenticator createDefaultAuthenticator(DogsBayAIEditor editor) {
		authenticator = new DefaultAuthenticator(editor);

		return authenticator;
	}

	public static DefaultAuthenticator getDefaultAuthenticator() {
		return authenticator;
	}

	/*
	 * public void checkLicense( String file, boolean first) {
	 * if (DEBUG) System.out.println( "Main.checkLicense( "+file+")");
	 * Exception exception = null;
	 * LicenseManager licenseManager = null;
	 * 
	 * try {
	 * licenseManager = LicenseManager.getInstance();
	 * licenseManager.isValid( KeyGenerator.generate(2), "DogsBay XML");
	 * 
	 * } catch( Exception ex) {
	 * exception = ex;
	 * }
	 * 
	 * 
	 * if (first && Identity.getIdentity().getEdition().equals(
	 * Identity.XMLPLUS_EDITION_LITE)) {
	 * //showLicenseInformation( file);
	 * CommunityLicenseInformationDialog dialog = new
	 * CommunityLicenseInformationDialog( editor, this, file, licenseManager,
	 * exception);
	 * 
	 * while ( !dialog.isVisible()) {
	 * //dialog.setVisible(true);
	 * dialog.setVisible(true);
	 * }
	 * }
	 * else if (first && licenseManager.getLicenseType().equals(
	 * LicenseType.LICENSE_TEMPORARY)) {
	 * LicenseInformationDialog dialog = new LicenseInformationDialog( editor, this,
	 * file, licenseManager, exception);
	 * 
	 * while ( !dialog.isVisible()) {
	 * //dialog.setVisible(true);
	 * dialog.setVisible(true);
	 * }
	 * } else if ( exception != null && first) {
	 * LicenseInformationDialog dialog = new LicenseInformationDialog( editor, this,
	 * file, licenseManager, exception);
	 * 
	 * while ( !dialog.isVisible()) {
	 * //dialog.setVisible(true);
	 * dialog.setVisible(true);
	 * }
	 * } else if ( exception != null) {
	 * System.exit(1);
	 * return;
	 * } else {
	 * // >>>
	 * showLicenseInformation( file);
	 * }
	 * }
	 */

	/*
	 * public void showLicenseInformation( String file) {
	 * if (DEBUG) System.out.println( "Main.showLicenseInformation( "+file+")");
	 * LicenseManager licenseManager = null;
	 * 
	 * try {
	 * licenseManager = LicenseManager.getInstance();
	 * licenseManager.isValid( KeyGenerator.generate(2), "DogsBay XML");
	 * } catch (Exception e) {
	 * e.printStackTrace();
	 * System.exit(0);
	 * return;
	 * }
	 * 
	 * String licenseType = licenseManager.getLicenseType();
	 * if ( !properties.isLicenseAccepted( licenseType)) {
	 * int type = LicenseDialog.TEMPORARY;
	 * 
	 * if ( licenseType.equals( LicenseType.LICENSE_PERMANENT)) {
	 * type = LicenseDialog.PERMANENT;
	 * } else if ( licenseType.equals( LicenseType.LICENSE_COMPLIMENTARY)) {
	 * type = LicenseDialog.COMPLIMENTARY;
	 * } else if ( licenseType.equals( LicenseType.LICENSE_ACADEMIC)) {
	 * type = LicenseDialog.ACADEMIC;
	 * }
	 * else if ( licenseType.equals( LicenseType.LICENSE_LITE)) {
	 * type = LicenseDialog.LITE;
	 * }
	 * 
	 * LicenseDialog dialog = new LicenseDialog( editor, this, file, type);
	 * while ( !dialog.isVisible()) {
	 * dialog.setVisible(true);
	 * }
	 * } else {
	 * // >>>
	 * startApplication( file);
	 * }
	 * }
	 * 
	 * 
	 * public void acceptLicense( String file) {
	 * if (DEBUG) System.out.println( "Main.acceptLicense( "+file+")");
	 * LicenseManager licenseManager = null;
	 * 
	 * try {
	 * licenseManager = LicenseManager.getInstance();
	 * licenseManager.isValid( KeyGenerator.generate(2), "DogsBay XML");
	 * } catch (Exception e) {
	 * e.printStackTrace();
	 * System.exit(0);
	 * return;
	 * }
	 * 
	 * properties.setLicenseAccepted( licenseManager.getLicenseType());
	 * 
	 * // >>>
	 * startApplication( file);
	 * }
	 */

	private void startApplication(final String file) {
		if (DEBUG)
			System.out.println("Main.startApplication( " + file + ")");
		// Start the splash screen...
		// Splash splash = new Splash( editor, Identity.getIdentity());

		try {
			// splash.start();

			if (file.equals("-debugger")) {
				// debugger.setVisible(true);
				// debugger.setVisible(true);
			} else {
				editor.setVisible(true);

				if (file != null && !file.equals("-editor")) {
					// Command-line file takes precedence
					Runnable runner = new Runnable() {
						public void run() {
							try {
								open(file);
							} catch (Exception e) {

								e.printStackTrace();
							}
						}
					};

					Thread thread = new Thread(runner);
					thread.start();
				} else if (properties.isReopenSessionFiles()) {
					// Reopen session files if no command-line file specified
					Runnable runner = new Runnable() {
						public void run() {
							try {
								reopenSessionFiles();
							} catch (Exception e) {
								if (DEBUG) {
									System.err.println("Error reopening session files:");
									e.printStackTrace();
								}
							}
						}
					};

					Thread thread = new Thread(runner);
					thread.start();
				}
			}
		} finally {
			// splash.stop( 2000);
		}
	}

	private ConfigurationProperties getProperties() {
		DogsBayDocument document = null;
		boolean firstTime = false;

		File dir = new File(DOGSBAY_HOME);

		if (!dir.exists()) {
			dir.mkdir();
		}

		File file = com.dogsbay.dogsbayaieditor.properties.SettingsFile
				.locate(dir.toPath(), com.dogsbay.dogsbayaieditor.properties.SettingsFile.MAIN)
				.toFile();
		URL url = null;

		try {
			url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file); // MalformedURLException
		} catch (Exception e) {
			// Should never happen, am not sure what to do in this case...
			e.printStackTrace();
		}

		if (file.exists()) {
			try {
				document = new DogsBayDocument(url);
				document.loadWithoutSubstitution();
			} catch (SAXParseException e) {
				MessageHandler.showError(
						"DogsBay XML Error\nThe configuration file seems to be corrupt, \nplease delete this file:\n\t"
								+ url.toExternalForm() + "\nand try to start DogsBay XML again",
						"Configuration File Error");
				return (null);
			} catch (Exception e) {
				// should not happen, document should always be valid...
				e.printStackTrace();
				return null;
			}
		} else {
			// A first run. 4.0 reads no 3.x settings: the chain that tried went
			// back five file names and covered one of the four files.
			firstTime = true;
			XElement root = new XElement("dogsbay");
			root.setText("\n");
			document = new DogsBayDocument(url, root);
		}

		XElement root = document.getRoot();
		boolean writable =
				com.dogsbay.dogsbayaieditor.properties.SettingsFile.adopt(file.toPath(), root);

		ConfigurationProperties properties = new ConfigurationProperties(document);

		if (!writable) {
			// A newer build wrote this file. Run on it, but never write it back:
			// saving would drop every setting this build does not know about and
			// leave the file still stamped with the newer version.
			properties.setReadOnly(true);
			MessageHandler.showMessage(
					"These settings were written by a newer version of DogsBay XML.\n\n"
							+ "DogsBay XML will run with them, but will not save any changes to them,\n"
							+ "so nothing the newer version stored is lost.");
			return properties;
		}

		properties.saveToDisk();

		return properties;
	}

	public static String getInstallationPath(String file) {
		String path = null;
		String dir = System.getProperty("lax.dir");

		if (dir != null && dir.trim().length() > 0) {
			path = dir + file;
		} else {
			path = file;
		}

		return path;
	}


	public void redirectOutput() {
		File dir = new File(DOGSBAY_HOME);

		if (!dir.exists()) {
			dir.mkdir();
		}

		File outFile = new File(dir, ".dogsbay.out");
		File errFile = new File(dir, ".dogsbay.err");

		try {
			System.setErr(new PrintStream(new FileOutputStream(errFile), true));
			System.setOut(new PrintStream(new FileOutputStream(outFile), true));
		} catch (Exception e) {
			// Should never happen, am not sure what to do in this case...
			e.printStackTrace();
		}
	}

	public static String getLaxFilePath() {
		String executable = System.getProperty("lax.application.name");
		String laxPath = null;

		if (executable != null) {
			String laxName = null;
			int dotIndex = executable.lastIndexOf('.');

			if (dotIndex != -1) {
				laxName = executable.substring(0, dotIndex) + ".lax";
			} else {
				laxName = executable + ".lax";
			}

			laxPath = System.getProperty("lax.dir") + laxName;
		}

		return laxPath;
	}

	// private static Properties getLAXProperties() {
	// Properties lax = null;
	// String laxFile = getLaxFilePath();
	//
	// if ( laxFile != null) {
	// File file = new File( laxFile);
	//
	// try {
	// if ( file.exists()) {
	// lax = new LaxProperties();
	// lax.load( new FileInputStream( file));
	//
	// // Enumeration names = lax.propertyNames();
	// //
	// // System.out.println( ">>> LAX");
	// //
	// // while ( names.hasMoreElements()) {
	// // String key = (String)names.nextElement();
	// // System.out.println( key+"="+lax.getProperty( key));
	// // }
	// //
	// // System.out.println( "<<< LAX");
	// }
	//
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }
	//
	// return lax;
	// }

	/**
	 * Attempts to load DogsBayAIEditor in a single JVM instance only.
	 */
	protected void open(String file) {
		if (DEBUG)
			System.out.println("Main.open( " + file + ")");
		URL url = null;

		if (file.equals("-debugger")) {
			// debugger.setVisible(true);
			//debugger.setVisible(true);
		} else if (file.equals("-editor")) {
			editor.setVisible(true);
		} else {
			try {
				url = new URL(file);
				editor.open(url, null, false);
				editor.setVisible(true);
			} catch (MalformedURLException e) {
				try {
					// url = new File( file).toURL();
					url = DogsBayURLUtilities.getURLFromFile(new File(file));
					editor.open(url, null, true);
					editor.setVisible(true);
				} catch (MalformedURLException m) {
					MessageHandler.showError("Could not resolve URL for file \"" + file + "\"", m, "URL Error");
				}
			}
		}
	}

	/**
	 * Reopens files from the previous session.
	 * Silently skips files that no longer exist or cannot be opened.
	 */
	protected void reopenSessionFiles() {
		if (DEBUG)
			System.out.println("Main.reopenSessionFiles()");

		Vector sessionDocs = properties.getSessionDocuments();

		if (sessionDocs == null || sessionDocs.size() == 0) {
			if (DEBUG)
				System.out.println("No session documents to reopen.");
			return;
		}

		if (DEBUG)
			System.out.println("Reopening " + sessionDocs.size() + " session documents...");

		// Suppress per-file explorer updates during batch restore
		editor.setRestoringSession(true);

		// PropertyList stores in LIFO order, so reverse to maintain original tab order
		int successCount = 0;
		try {
			for (int i = sessionDocs.size() - 1; i >= 0; i--) {
				String path = (String) sessionDocs.elementAt(i);

				try {
					File file = new File(path);

					// Only attempt to open if file still exists
					if (file.exists() && file.isFile()) {
						URL url = DogsBayURLUtilities.getURLFromFile(file);
						editor.open(url, null, false);
						successCount++;

						if (DEBUG)
							System.out.println("  Reopened: " + path);
					} else {
						if (DEBUG)
							System.out.println("  Skipped (not found): " + path);
					}
				} catch (MalformedURLException e) {
					if (DEBUG)
						System.err.println("  Failed to create URL for: " + path);
				} catch (Exception e) {
					if (DEBUG) {
						System.err.println("  Failed to open: " + path);
						e.printStackTrace();
					}
				}
			}
		} finally {
			// Post to EDT to ensure all pending invokeLater tab additions
			// have completed before re-enabling the change listener
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					editor.setRestoringSession(false);
					editor.finalizeSessionRestore();
				}
			});
		}

		if (DEBUG)
			System.out.println("Session restore complete: " + successCount + "/" + sessionDocs.size() + " files reopened.");
	}

	/**
	 * Attempts to load DogsBayAIEditor in a single JVM instance only.
	 */
	protected Socket findDogsBayAIEditorSocket() {
		// Loopback with a short timeout: the host's network address can take a name
		// lookup or a firewall drop to fail, which on Windows hung startup silently.
		Socket s = new Socket();
		try {
			s.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), XMLPLUS_PORT),
					SINGLE_INSTANCE_CONNECT_TIMEOUT_MS);
			return s;
		} catch (IOException e) {
			try {
				s.close();
			} catch (IOException ignore) {
				// nothing to release
			}
			// e.printStackTrace();
			return null;
		}
	}

	/**
	 * Attempts to load DogsBayAIEditor in a single JVM instance only.
	 */
	public void launch(ExtensionClassLoader loader, String path) {
		if (DEBUG)
			System.out.println("Main::launch(loader, path): (" + loader + ", " + path + ")");
		boolean launched = false;
		File file = null;

		if (path != null && path.equals("-noserver")) {
			// force the editor to start without a server.
			start(loader, "-editor");
			return;
		}

		if (path != null && !path.equals("-debugger")) {
			file = new File(path);
			path = file.getPath();
		} else if (path == null) {
			path = "-editor";
		}

		while (!launched) {
			Socket socket = findDogsBayAIEditorSocket();

			if (socket != null) { // already an editor or debugger active.

				// check for a license, do not continue if no license found ...
				/*
				 * try {
				 * LicenseManager licenseManager = LicenseManager.getInstance();
				 * licenseManager.isValid( KeyGenerator.generate(2), "DogsBay XML");
				 * } catch( Exception ex) {
				 */
				// System.err.println( "Server Socket started but could not find a license!");
				// System.exit( 0);
				// }

				try {
					if (path != null) {
						OutputStream stream = socket.getOutputStream();
						byte[] bytes = path.getBytes();
						stream.write(bytes.length);
						stream.write(bytes);
						stream.close();
					}
					launched = true;
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("ERROR: Could not connect to socket!");
				}
			} else {
				try {
					// Start-up server-socket!
					// Loopback only: another instance on this machine is the only client.
					ServerSocket server = new ServerSocket(XMLPLUS_PORT, 50, InetAddress.getLoopbackAddress());

					start(loader, path);

					Thread listener = new ListenerThread(server);
					listener.start();

					launched = true;
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("ERROR: Could not create server!");
				}
			}
		}

	}

	protected boolean isEmptyString(String string) {
		if (string != null && string.trim().length() > 0) {
			return false;
		}

		return true;
	}

	public static void main(ExtensionClassLoader loader, String[] args) throws Exception {

		if (DEBUG)
			System.out.println("Main::ExtensionClassLoader - main loader: " + loader + " - args: " + args);
		setupSystemUIProperties();
		registerDocumentFormats();
		checkJavaVersion();

		String file = null;
		if (args.length > 0) {
			file = args[0];
		}

		try {
			new Main(loader, file);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		if (DEBUG)
			System.out.println("Main::ExtensionClassLoader - main args: " + args);
		setupSystemUIProperties();
		registerDocumentFormats();
		checkJavaVersion();

		String file = null;
		if (args.length > 0) {
			file = args[0];
		}

		try {
			// Standalone launch (packaged app / `java -jar`, no legacy Loader):
			// build a delegating ExtensionClassLoader over the app classloader so
			// components that resolve resources/classes through the editor's
			// loader (icons, plugin discovery) work — the uber-jar classpath is
			// the parent, so everything resolves there. Avoids the null-loader
			// NPEs of the old `new Main(file)` path.
			ExtensionClassLoader loader = new ExtensionClassLoader(Main.class.getClassLoader());
			new Main(loader, file);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Registers all built-in document formats with the DocumentFormatRegistry.
	 * New format support (AsciiDoc, JSON, YAML, etc.) should be added here.
	 */
	private static void registerDocumentFormats() {
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.XmlDocumentFormat());
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.DtdDocumentFormat());
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.HtmlDocumentFormat());
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.MarkdownDocumentFormat());
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.AsciiDocDocumentFormat());

		// PlainText is both a registered format (for .txt, .log, etc.)
		// and the default fallback for unrecognized extensions
		com.dogsbay.xml.editor.PlainTextDocumentFormat plainText = new com.dogsbay.xml.editor.PlainTextDocumentFormat();
		com.dogsbay.xml.editor.DocumentFormatRegistry.register(plainText);
		com.dogsbay.xml.editor.DocumentFormatRegistry.setDefault(plainText);
	}

	public static void setupSystemUIProperties() {
		// Use the OS font rendering settings (LCD subpixel AA on modern Linux/Mac/Windows)
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");

		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("com.apple.macos.useScreenMenuBar", "true");
		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "DogsBay XML");
		System.setProperty("com.apple.mrj.application.live-resize", "true");
		System.setProperty("com.apple.macos.smallTabs", "true");
		System.setProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory");

		// UIManager.setLookAndFeel(
		// "com.incors.plaf.kunststoff.KunststoffLookAndFeel");
		// UIManager.setLookAndFeel( "javax.swing.plaf.metal.MetalLookAndFeel");
		// UIManager.setLookAndFeel( "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
		// UIManager.setLookAndFeel(
		// "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		// UIManager.setLookAndFeel( "com.sun.java.swing.plaf.motif.MotifLookAndFeel");

	}

	public static void checkJavaVersion() {
		String v = System.getProperty("java.class.version", "44.0");

		if ("48.0".compareTo(v) > 0) { // not jdk 1.4...
			JOptionPane.showMessageDialog(null,
					"Cannot Start The DogsBay XML,\n" +
							"Could not find a valid JDK \"" + System.getProperty("java.version") + "\"\n" +
							"Need at least JDK 1.4 to run.",
					"Invalid JDK",
					JOptionPane.ERROR_MESSAGE);

			System.exit(-1);
			return;
		}
	}

	public class ListenerThread extends Thread {
		ServerSocket server = null;

		public ListenerThread(ServerSocket socket) {
			server = socket;
		}

		public void run() {
			try {
				while (true) {
					Socket socket = server.accept();
					InputStream stream = socket.getInputStream();
					int length = stream.read();
					byte[] bytes = new byte[length];
					stream.read(bytes);

					String file = new String(bytes);
					open(file);
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("ERROR: Could not connect to server!");
			}
		}
	}

	public static boolean isDebug() {
		ClassLoader loader = Main.class.getClassLoader();

		try {
			Class invokedClass = loader.loadClass("com.dogsbay.dogsbayaieditor.DogsBayAIEditor");

			return true;
		} catch (Exception e) {
			// e.printStackTrace();
		}

		return false;
	}
}
