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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Base64;
import java.util.StringTokenizer;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.bounce.net.DefaultAuthenticator;

import com.dogsbay.xml.DogsBayURLUtilities;

/**
 * A URL related utility class.
 *
 * @version $Revision: 1.9 $, $Date: 2004/09/23 10:55:30 $
 * @author Dogsbay
 */
public class URLUtilities {

	/**
	 * Get the password for this URL.
	 * 
	 * @param url the url.
	 * 
	 * @return the password or null if not found.
	 */
	public static String getPassword(URL url) {
		String password = null;

		if (url != null) {
			String user = url.getUserInfo();

			if (user != null && user.trim().length() > 0) {
				int index = user.indexOf(':');

				if (index != -1) {
					password = user.substring(index + 1);
				}
			}
		}

		// System.out.println( "URLUtilities.getPassword( "+url+") ["+password+"]");

		return password;
	}

	/**
	 * Get the username from the user info block of this URL.
	 * 
	 * @param url the url.
	 * 
	 * @return the username or null if not found.
	 */
	public static String getUsername(URL url) {
		String username = null;

		if (url != null) {
			String user = url.getUserInfo();

			if (user != null && user.trim().length() > 0) {
				int index = user.indexOf(':');

				if (index != -1) {
					username = user.substring(0, index);
				}
			}
		}

		// System.out.println( "URLUtilities.getUsername( "+url+") ["+username+"]");

		return username;
	}

	/**
	 * Encrypts the password in this URL.
	 * 
	 * @param url the url.
	 * 
	 * @return the encrypted string.
	 */
	public static String encrypt(URL url) {
		String password = getPassword(url);
		String username = getUsername(url);

		if (password != null) {
			password = StringUtilities.encrypt(password);
		}

		// System.out.println( "URLUtilities.encrypt( "+url+") ["+password+"]");

		return createURL(url.getProtocol(), username, password, url.getHost(), url.getPort(), url.getPath(),
				url.getQuery(), url.getRef()).toString();
	}

	public static String getPath(URL url) {
		String path = url.getPath();

		if (!path.endsWith("/")) {
			int index = path.lastIndexOf('/');

			if (index != -1) {
				path = path.substring(0, index);
			}
		}

		return path;
	}

	public static String toDisplay(URL url) {
		return toDisplay(url.toString());
	}

	public static String toDisplay(String url) {
		StringTokenizer tokenizer = new StringTokenizer(url, "/");
		int tokens = tokenizer.countTokens();

		if (tokens > 5) {
			StringBuffer result = new StringBuffer();

			for (int i = 0; i < tokens; i++) {
				if (i == 0) {
					result.append(tokenizer.nextToken());
					result.append("/");
				} else if (i == 1) {
					result.append(tokenizer.nextToken());
					result.append("/...");
				} else if (i >= tokens - 3) {
					result.append("/");
					result.append(tokenizer.nextToken());
				} else {
					tokenizer.nextToken();
				}
			}

			return result.toString();
		}

		return url;
	}

	/**
	 * get the path to the source/file.
	 * 
	 * @return the path to the source/file.
	 */
	public static String getPath(String location) {
		return getPath(toURL(location));
	}

	public static String getFileName(URL url) {
		if (url != null) {
			return getFileName(url.toString());
		} else {
			return null;
		}
	}

	/**
	 * get the name of the source/file.
	 * 
	 * @return the name of the source/file.
	 */
	public static String getFileName(String location) {
		if (location != null) {
			String name = location;
			int index = location.lastIndexOf('/');

			if (index != -1) {
				name = name.substring(index + 1, name.length());
			}

			return name;
		}

		return null;
	}

	public static String getFileNameWithoutExtension(URL url) {
		if (url != null) {
			return getFileName(url.toString());
		} else {
			return null;
		}
	}

	/**
	 * get the name of the source/file.
	 * 
	 * @return the name of the source/file.
	 */
	public static String getFileNameWithoutExtension(String location) {
		if (location != null) {
			String name = location;
			int index = location.lastIndexOf('/');

			if (index != -1) {
				name = name.substring(index + 1, name.length());
			}

			index = name.lastIndexOf('.');

			if (index != -1) {
				name = name.substring(0, index);
			}

			return name;
		}

		return null;
	}

	public static String getExtension(URL url) {
		if (url != null) {
			return getExtension(url.toString());
		} else {
			return null;
		}
	}

	public static String getExtension(String location) {
		String extension = null;
		int index = location.lastIndexOf('.');

		if (index != -1) {
			extension = location.substring(index + 1, location.length());
		}

		return extension;
	}

	/**
	 * Decrypts the string and creates a URL.
	 * 
	 * @param url the url.
	 * 
	 * @return the decrypted url or null if the url could not be constructed.
	 */
	public static URL decrypt(String string) {
		try {
			URL url = new URL(string);

			String password = getPassword(url);
			String username = getUsername(url);

			if (password != null) {
				password = StringUtilities.decrypt(password);
			}

			// System.out.println( "URLUtilities.decrypt( "+string+") ["+password+"]");

			return createURL(url.getProtocol(), username, password, url.getHost(), url.getPort(), url.getPath(),
					url.getQuery(), url.getRef());
		} catch (MalformedURLException e) {
			// ignore
		}

		return null;
	}

	/**
	 * Creates a new URL.
	 * 
	 * @param protocol the protocol, ie: file, http, https, ftp.
	 * @param username the username for the connection.
	 * @param password the password for the connection.
	 * @param host     the host server.
	 * @param file     the file on the server.
	 * 
	 * @return the url or null if the URL could not be created.
	 */
	public static URL createURL(String protocol, String username, String password, String host, int port, String path,
			String query, String ref) {
		URL url = null;

		try {
			StringBuffer result = new StringBuffer(protocol);
			result.append(':');

			if (host != null && host.length() > 0) {
				result.append("//");

				if (username != null && username.length() > 0) {
					result.append(username);
					result.append(':');

					if (password != null && password.length() > 0) {
						result.append(password);
					}

					result.append('@');
				}

				result.append(host);
			}

			if (port > 0) {
				result.append(':');
				result.append(port);
			}

			if (path != null) {
				result.append(path);
			}

			if (query != null) {
				result.append('?');
				result.append(query);
			}

			if (ref != null) {
				result.append('#');
				result.append(ref);
			}

			url = new URL(result.toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			// just return null
			url = null;
		}

		return url;
	}

	/**
	 * Returns a String representation without the Username and Password.
	 * 
	 * @param url the url to convert to a String...
	 * 
	 * @return the string representation.
	 */
	public static String toString(URL url) {
		if (url != null) {
			StringBuffer result = new StringBuffer(url.getProtocol());
			result.append(":");

			if (url.getHost() != null && url.getHost().length() > 0) {
				result.append("//");
				result.append(url.getHost());
			}

			if (url.getPort() > 0) {
				result.append(":");
				result.append(url.getPort());
			}

			if (url.getPath() != null) {
				result.append(url.getPath());
			}

			if (url.getQuery() != null) {
				result.append('?');
				result.append(url.getQuery());
			}

			if (url.getRef() != null) {
				result.append("#");
				result.append(url.getRef());
			}

			return result.toString();
		}

		return null;
	}

	/**
	 * Convert the given URL to a file.
	 * 
	 * @param url the url to be converted.
	 * 
	 * @return a new file or null if the URL does not describe a file.
	 */
	public static File toFile(URL url) {
		if (url != null && url.getProtocol().equals("file")) {
			try {
				return new File(url.toURI());
			} catch (java.net.URISyntaxException e) {
				return new File(url.getFile());
			} catch (IllegalArgumentException e) {
				return new File(url.getFile());
			}
		}

		return null;
	}

	/**
	 * Convert the given url to a location relative to the current machine, ie:
	 * file: is missing.
	 * 
	 * @param url the url string to be converted.
	 * 
	 * @return a new file or null if the string does not describe a file.
	 */
	public static String toRelativeString(URL url) {
		String location = null;

		if (url != null) {
			location = url.toString();

			if (url.getProtocol().equals("file")) {
				location = location.substring(6, location.length());
			}
		}

		return location;
	}

	/**
	 * Convert the given string to a file.
	 * 
	 * @param url the url string to be converted.
	 * 
	 * @return a new file or null if the string does not describe a file.
	 */
	public static File toFile(String url) {
		return toFile(toURL(url));
	}

	/**
	 * Converts a string to a url.
	 * 
	 * @param url the string version of the url.
	 * 
	 * @return the converted URL.
	 */
	public static URL toURL(String url) {
		URL result = null;

		if (url != null && url.length() > 4) {
			try {
				result = new URL(url);
			} catch (MalformedURLException e1) {
				try {
					result = DogsBayURLUtilities.getURLFromFile(new File(url));
				} catch (MalformedURLException e2) {
					result = null;
				}
			}
		}

		return result;
	}

	/**
	 * Resolve the relative location based on the base URL.
	 * 
	 * @param url the base url.
	 * 
	 * @return a new file or null if the URL does not describe a file.
	 */
	public static String resolveURL(URL base, String location) {
		String result = null;

		if (location != null && location.trim().length() > 0) {
			try {
				URL url = new URL(base, location);
				result = url.toString();
			} catch (MalformedURLException e) {
				URL url = toURL(location);

				if (url != null) {
					result = url.toString();
				}
			}
		}

		return result;
	}

	/**
	 * Return the path for the file relative to the base uri.
	 * 
	 * @param base the base url.
	 * @param file the file location to get the relative path for.
	 * 
	 * @return the relative path.
	 */
	public static String getRelativePath(URL base, String file) {

		if (file.startsWith("file:")) {
			file = file.substring(5);
		} else if (file.startsWith("/")) {
			file = file.substring(1);
		} else {
			return file;
		}

		if ("file".equals(base.getProtocol())) {
			StringTokenizer baseDirs = new StringTokenizer(base.getFile(), "/");
			StringTokenizer fileDirs = new StringTokenizer(file, "/");

			StringBuffer relativeDir = new StringBuffer();
			String fileDir = null;

			boolean first = true;

			while (baseDirs.hasMoreTokens() && fileDirs.hasMoreTokens()) {
				String baseDir = baseDirs.nextToken();
				fileDir = fileDirs.nextToken();

				if (!baseDir.equals(fileDir)) {
					if (first) {
						return file;
					} else {
						break;
					}
				}

				first = false;
			}

			while (baseDirs.hasMoreTokens()) {
				relativeDir.append("../");
				baseDirs.nextToken();
			}

			relativeDir.append(fileDir);

			while (fileDirs.hasMoreTokens()) {
				relativeDir.append("/" + fileDirs.nextToken());
			}

			return relativeDir.toString();
		}

		return file;
	}

	/**
	 * URL encode the text (replaces spaces with %20).
	 * 
	 * @param url the url string to encode.
	 * 
	 * @return the encoded url string.
	 */
	public static String encodeURL(String url) {
		StringBuffer uri = new StringBuffer(url.length());
		int length = url.length();
		for (int i = 0; i < length; i++) {
			char c = url.charAt(i);

			switch (c) {
				case ' ':
					uri.append("%20");
					break;
				case '!':
					uri.append(c);
					break;
				case '"':
					uri.append("%22");
					break;
				case '#':
					uri.append(c);
					break;
				case '$':
					uri.append(c);
					break;
				case '%':
					uri.append(c);
					break;
				case '&':
					uri.append(c);
					break;
				case '\'':
					uri.append(c);
					break;
				case '(':
					uri.append(c);
					break;
				case ')':
					uri.append(c);
					break;
				case '*':
					uri.append(c);
					break;
				case '+':
					uri.append(c);
					break;
				case ',':
					uri.append(c);
					break;
				case '-':
					uri.append(c);
					break;
				case '.':
					uri.append(c);
					break;
				case '/':
					uri.append(c);
					break;
				case '0':
					uri.append(c);
					break;
				case '1':
					uri.append(c);
					break;
				case '2':
					uri.append(c);
					break;
				case '3':
					uri.append(c);
					break;
				case '4':
					uri.append(c);
					break;
				case '5':
					uri.append(c);
					break;
				case '6':
					uri.append(c);
					break;
				case '7':
					uri.append(c);
					break;
				case '8':
					uri.append(c);
					break;
				case '9':
					uri.append(c);
					break;
				case ':':
					uri.append(c);
					break;
				case ';':
					uri.append(c);
					break;
				case '<':
					uri.append("%3C");
					break;
				case '=':
					uri.append(c);
					break;
				case '>':
					uri.append("%3E");
					break;
				case '?':
					uri.append(c);
					break;
				case '@':
					uri.append(c);
					break;
				case 'A':
					uri.append(c);
					break;
				case 'B':
					uri.append(c);
					break;
				case 'C':
					uri.append(c);
					break;
				case 'D':
					uri.append(c);
					break;
				case 'E':
					uri.append(c);
					break;
				case 'F':
					uri.append(c);
					break;
				case 'G':
					uri.append(c);
					break;
				case 'H':
					uri.append(c);
					break;
				case 'I':
					uri.append(c);
					break;
				case 'J':
					uri.append(c);
					break;
				case 'K':
					uri.append(c);
					break;
				case 'L':
					uri.append(c);
					break;
				case 'M':
					uri.append(c);
					break;
				case 'N':
					uri.append(c);
					break;
				case 'O':
					uri.append(c);
					break;
				case 'P':
					uri.append(c);
					break;
				case 'Q':
					uri.append(c);
					break;
				case 'R':
					uri.append(c);
					break;
				case 'S':
					uri.append(c);
					break;
				case 'T':
					uri.append(c);
					break;
				case 'U':
					uri.append(c);
					break;
				case 'V':
					uri.append(c);
					break;
				case 'W':
					uri.append(c);
					break;
				case 'X':
					uri.append(c);
					break;
				case 'Y':
					uri.append(c);
					break;
				case 'Z':
					uri.append(c);
					break;
				case '[':
					uri.append(c);
					break;
				case '\\':
					uri.append("%5C");
					break;
				case ']':
					uri.append(c);
					break;
				case '^':
					uri.append("%5E");
					break;
				case '_':
					uri.append(c);
					break;
				case '`':
					uri.append("%60");
					break;
				case 'a':
					uri.append(c);
					break;
				case 'b':
					uri.append(c);
					break;
				case 'c':
					uri.append(c);
					break;
				case 'd':
					uri.append(c);
					break;
				case 'e':
					uri.append(c);
					break;
				case 'f':
					uri.append(c);
					break;
				case 'g':
					uri.append(c);
					break;
				case 'h':
					uri.append(c);
					break;
				case 'i':
					uri.append(c);
					break;
				case 'j':
					uri.append(c);
					break;
				case 'k':
					uri.append(c);
					break;
				case 'l':
					uri.append(c);
					break;
				case 'm':
					uri.append(c);
					break;
				case 'n':
					uri.append(c);
					break;
				case 'o':
					uri.append(c);
					break;
				case 'p':
					uri.append(c);
					break;
				case 'q':
					uri.append(c);
					break;
				case 'r':
					uri.append(c);
					break;
				case 's':
					uri.append(c);
					break;
				case 't':
					uri.append(c);
					break;
				case 'u':
					uri.append(c);
					break;
				case 'v':
					uri.append(c);
					break;
				case 'w':
					uri.append(c);
					break;
				case 'x':
					uri.append(c);
					break;
				case 'y':
					uri.append(c);
					break;
				case 'z':
					uri.append(c);
					break;
				case '{':
					uri.append("%7B");
					break;
				case '|':
					uri.append("%7C");
					break;
				case '}':
					uri.append("%7D");
					break;
				case '~':
					uri.append(c);
					break;
				default:
					uri.append(percentEscape(c));
			}
		}
		return uri.toString();
	}

	private static String percentEscape(char c) {
		StringBuffer result = new StringBuffer(3);
		String s = String.valueOf(c);

		try {
			byte[] data = s.getBytes("UTF8");
			for (int i = 0; i < data.length; i++) {
				result.append('%');
				String hex = Integer.toHexString(data[i]);
				result.append(hex.substring(hex.length() - 2));
			}
			return result.toString();
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException("Broken VM: does not recognize UTF-8 encoding");
		}

	}

	public static boolean isURLCharacter(int c) {
		if (c >= 'a' && c <= 'z')
			return true;
		if (c >= 'A' && c <= 'Z')
			return true;
		if (c >= '0' && c <= '9')
			return true;
		if (c == '/')
			return true;
		if (c == '-')
			return true;
		if (c == '.')
			return true;
		if (c == '=')
			return true;
		if (c == '&')
			return true;
		if (c == '%')
			return true;
		if (c == '?')
			return true;
		// Since we're really checking for URI references
		if (c == '#')
			return true;
		if (c == ':')
			return true;
		if (c == '@')
			return true;
		if (c == '+')
			return true;
		if (c == '$')
			return true;
		if (c == ',')
			return true;

		if (c == '_')
			return true;
		if (c == '!')
			return true;
		if (c == '~')
			return true;
		if (c == '*')
			return true;
		if (c == '\'')
			return true;
		if (c == '(')
			return true;
		if (c == ')')
			return true;
		// These next two were reallowed by RFC 2732
		// for IPv6 addresses
		if (c == '[')
			return true;
		if (c == ']')
			return true;

		return false;
	}

	public static InputStream open(URL url) throws IOException {
		// System.out.println( "URLUtilities.open( "+url+")");
		InputStream stream = null;

		String password = URLUtilities.getPassword(url);
		String username = URLUtilities.getUsername(url);

		String protocol = url.getProtocol();

		if (protocol.equals("http") || protocol.equals("https")) {
			try {
				DefaultAuthenticator authenticator = Main.getDefaultAuthenticator();

				URL newURL = new URL(URLUtilities.toString(url));

				if (authenticator != null && password != null && username != null) {
					authenticator
							.setPasswordAuthentication(new PasswordAuthentication(username, password.toCharArray()));
				}

				stream = newURL.openStream();

				if (authenticator != null && password != null && username != null) {
					authenticator.setPasswordAuthentication(null);
				}
			} catch (Exception e) {
				// Plain connection failed — retry via an explicit authenticated HTTP GET.
				stream = httpGet(toString(url), username, password);
			}
		} else if (protocol.equals("ftp")) {
			FTPClient client = null;
			String host = url.getHost();

			try {
				// System.out.println( "Connecting to: "+host+" ...");

				client = new FTPClient();
				client.connect(host);

				// System.out.println( "Connected.");

				// After connection attempt, you should check the reply code to verify
				// success.
				int reply = client.getReplyCode();

				if (!FTPReply.isPositiveCompletion(reply)) {
					// System.out.println( "Could not connect.");
					client.disconnect();
					throw new IOException("FTP Server \"" + host + "\" refused connection.");
				}

				// System.out.println( "Logging in using: "+username+", "+password+" ...");

				if (!client.login(username, password)) {
					// System.out.println( "Could not log in.");
					// TODO bring up login dialog?
					client.disconnect();

					throw new IOException("Could not login to FTP Server \"" + host + "\".");
				}

				// System.out.println( "Logged in.");

				client.setFileType(FTP.BINARY_FILE_TYPE);
				client.enterLocalPassiveMode();

				// System.out.println( "Opening file \""+url.getFile()+"\" ...");

				stream = client.retrieveFileStream(url.getFile());
				// System.out.println( "File opened.");

				// System.out.println( "Disconnecting ...");
				client.disconnect();
				// System.out.println( "Disconnected.");

			} catch (IOException e) {
				if (client.isConnected()) {
					try {
						client.disconnect();
					} catch (IOException f) {
						// do nothing
						e.printStackTrace();
					}
				}

				throw new IOException("Could not connect to FTP Server \"" + host + "\".");
			}

		} else if (protocol.equals("file")) {
			stream = url.openStream();
		} else {

			// unknown protocol but try anyways
			try {
				stream = url.openStream();
			} catch (IOException ioe) {

				throw new IOException("The \"" + protocol + "\" protocol is not supported.");
			}
		}

		return stream;
	}

	public static void save(URL url, InputStream input, String encoding) throws IOException {
		// System.out.println( "URLUtilities.save( "+url+", "+encoding+")");

		String password = URLUtilities.getPassword(url);
		String username = URLUtilities.getUsername(url);

		String protocol = url.getProtocol();

		if (protocol.equals("ftp")) {
			FTPClient client = null;
			String host = url.getHost();

			client = new FTPClient();
			// System.out.println( "Connecting ...");
			client.connect(host);
			// System.out.println( "Connected.");

			// After connection attempt, you should check the reply code to verify
			// success.
			int reply = client.getReplyCode();

			if (!FTPReply.isPositiveCompletion(reply)) {
				// System.out.println( "Disconnecting...");

				client.disconnect();
				throw new IOException("FTP Server \"" + host + "\" refused connection.");
			}

			// System.out.println( "Logging in ...");

			if (!client.login(username, password)) {
				// System.out.println( "Could not log in.");
				// TODO bring up login dialog?
				client.disconnect();

				throw new IOException("Could not login to FTP Server \"" + host + "\".");
			}

			// System.out.println( "Logged in.");

			client.setFileType(FTP.BINARY_FILE_TYPE);
			client.enterLocalPassiveMode();

			// System.out.println( "Writing output ...");

			OutputStream output = client.storeFileStream(url.getFile());

			// if( !FTPReply.isPositiveIntermediate( client.getReplyCode())) {
			// output.close();
			// client.disconnect();
			// throw new IOException( "Could not transfer file \""+url.getFile()+"\".");
			// }

			InputStreamReader reader = new InputStreamReader(input, encoding);
			OutputStreamWriter writer = new OutputStreamWriter(output, encoding);

			int ch = reader.read();

			while (ch != -1) {
				writer.write(ch);
				ch = reader.read();
			}

			writer.flush();
			writer.close();
			output.close();

			// Must call completePendingCommand() to finish command.
			if (!client.completePendingCommand()) {
				client.disconnect();
				throw new IOException("Could not transfer file \"" + url.getFile() + "\".");
			} else {
				client.disconnect();
			}

		} else if (protocol.equals("http") || protocol.equals("https")) {
			httpPut(toString(url), input, username, password);
		} else if (protocol.equals("file")) {
			saveFileAtomic(toFile(url), input, encoding);
		} else {
			throw new IOException("The \"" + protocol + "\" protocol is not supported.");
		}
	}

	/**
	 * Saves to a local file atomically (write a sibling temp file, then rename it over
	 * the destination) so a failure mid-write never truncates the previously-good file.
	 * Preserves the destination's existing POSIX permissions (the temp file is created
	 * owner-only, so without this every save would strip group/other access), follows a
	 * symlink so the link target is written rather than replaced, and falls back to a
	 * direct in-place write when the containing directory is not writable (where
	 * temp+rename is impossible).
	 */
	private static void saveFileAtomic(File dest, InputStream input, String encoding) throws IOException {
		Path destPath = dest.toPath();

		// Follow a symlink so we write THROUGH it rather than replacing the link with a file.
		if (Files.isSymbolicLink(destPath)) {
			try {
				destPath = destPath.toRealPath();
			} catch (IOException danglingLink) {
				// Broken link target — write to the link path itself.
			}
		}

		Path dir = destPath.toAbsolutePath().getParent();

		Path tmp;
		try {
			if (dir == null) {
				throw new IOException("No parent directory for " + destPath);
			}
			tmp = Files.createTempFile(dir, dest.getName() + ".", ".tmp");
		} catch (IOException tempFailed) {
			// Directory not writable (or no temp allowed) — temp+rename isn't possible.
			// Fall back to a direct in-place write (the pre-atomic behaviour).
			copyStream(input, destPath, encoding);
			return;
		}

		try {
			copyStream(input, tmp, encoding);

			// Preserve the destination's existing permissions (temp is 0600 by default).
			try {
				if (Files.exists(destPath, LinkOption.NOFOLLOW_LINKS)) {
					java.util.Set<PosixFilePermission> perms = Files.getPosixFilePermissions(destPath);
					Files.setPosixFilePermissions(tmp, perms);
				}
			} catch (UnsupportedOperationException | IOException nonPosix) {
				// non-POSIX filesystem (e.g. Windows) — nothing to preserve
			}

			try {
				Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ioe) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// best effort — the original failure is what matters
			}

			throw ioe;
		}
	}

	/** Copies the input stream to the target path using the given encoding. */
	private static void copyStream(InputStream input, Path target, String encoding) throws IOException {
		try (InputStreamReader reader = new InputStreamReader(input, encoding);
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(Files.newOutputStream(target), encoding))) {
			char[] buffer = new char[8192];
			int read;

			while ((read = reader.read(buffer)) != -1) {
				writer.write(buffer, 0, read);
			}
		}
	}

	/**
	 * Lazily-initialised shared HTTP client (thread-safe, reusable). Uses the JDK
	 * {@code java.net.http} stack, which verifies TLS certificate hostnames by default
	 * (unlike the retired commons-httpclient 3.x, CVE-2012-5783). Built on first use so
	 * the client's selector/thread pool isn't created for file-only operations.
	 */
	private static final class HttpClientHolder {
		static final HttpClient INSTANCE = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	private static HttpClient httpClient() {
		return HttpClientHolder.INSTANCE;
	}

	/**
	 * Builds a request for the given (credential-free) URI, adding an HTTP Basic
	 * {@code Authorization} header when a username and password are supplied.
	 */
	private static HttpRequest.Builder baseRequest(String uri, String username, String password) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
				.timeout(Duration.ofSeconds(60));

		if (username != null && !username.isEmpty() && password != null) {
			String token = Base64.getEncoder().encodeToString(
					(username + ":" + password).getBytes(StandardCharsets.UTF_8));
			builder.header("Authorization", "Basic " + token);
		}

		return builder;
	}

	/**
	 * HTTP GET returning the response body as a stream. Throws {@link IOException}
	 * on any non-2xx status. Package-private for testing.
	 */
	static InputStream httpGet(String uri, String username, String password) throws IOException {
		try {
			HttpResponse<InputStream> response = httpClient().send(
					baseRequest(uri, username, password).GET().build(),
					HttpResponse.BodyHandlers.ofInputStream());

			if (response.statusCode() / 100 != 2) {
				response.body().close();
				throw new IOException("HTTP GET \"" + uri + "\" failed: " + response.statusCode());
			}

			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("HTTP GET interrupted", e);
		}
	}

	/**
	 * HTTP PUT of the given input stream's bytes. Throws {@link IOException} on any
	 * non-2xx status. Package-private for testing.
	 */
	static void httpPut(String uri, InputStream input, String username, String password) throws IOException {
		try {
			byte[] body = input.readAllBytes();

			HttpResponse<String> response = httpClient().send(
					baseRequest(uri, username, password)
							.PUT(HttpRequest.BodyPublishers.ofByteArray(body))
							.build(),
					HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() / 100 != 2) {
				throw new IOException("HTTP PUT \"" + uri + "\" failed: " + response.statusCode());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("HTTP PUT interrupted", e);
		}
	}
}
