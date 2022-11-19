/*
 * Copyright © 2018-2022 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.openliberty.domino.util;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.NotesFactory;
import lotus.domino.Session;

public enum OpenLibertyUtil {
	;
	
	public static final boolean IS_WINDOWS;
	public static final boolean IS_LINUX;
	static {
		String os = System.getProperty("os.name"); //$NON-NLS-1$
		IS_WINDOWS = os.toLowerCase().contains("windows"); //$NON-NLS-1$
		IS_LINUX = os.toLowerCase().contains("linux"); //$NON-NLS-1$
	}
	private static Path tempDirectory;
	private static final TrustManager[] TRUST_ALL = new TrustManager[] {
		new X509TrustManager() {
	        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
	            return null;
	        }
	        public void checkClientTrusted(
	            java.security.cert.X509Certificate[] certs, String authType) {
	        }
	        public void checkServerTrusted(
	            java.security.cert.X509Certificate[] certs, String authType) {
	        }
	    }
	};
	
	/**
	 * Returns an appropriate temp directory for the system. On Windows, this is
	 * equivalent to <code>System.getProperty("java.io.tmpdir")</code>. On
	 * Linux, however, since this seems to return the data directory in some
	 * cases, it uses <code>/tmp</code>.
	 *
	 * @return an appropriate temp directory for the system
	 */
	public static synchronized Path getTempDirectory() {
		if(tempDirectory == null) {
			String base;
			if (!IS_WINDOWS) {
				base = "/tmp"; //$NON-NLS-1$
			} else {
				base = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
			}
			try {
				tempDirectory = Files.createTempDirectory(Paths.get(base), OpenLibertyUtil.class.getPackage().getName());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return tempDirectory;
	}
	
	/**
	 * Evaluates the provided task with a context where TLS 1.2 is enabled and SSL
	 * is configured to trust all certificates.
	 * 
	 * @param <T> the type returned by {@code r}
	 * @param r the task to run
	 * @return the return value of {@code r}
	 * @since 4.0.0
	 */
	public static <T> T withHttpsContext(Callable<T> r) {
		// Domino defaults to using old protocols - bump this up for our needs here so the connection succeeds
		// Also set a trusting SSL factory to allow for newer certs than Domino knows about
		String protocols = StringUtil.toString(System.getProperty("https.protocols")); //$NON-NLS-1$
		SSLSocketFactory sslFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
		try {
			System.setProperty("https.protocols", "TLSv1.2"); //$NON-NLS-1$ //$NON-NLS-2$
			
			SSLContext sc = SSLContext.getInstance("SSL"); //$NON-NLS-1$
			sc.init(null, TRUST_ALL, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			
			return r.call();
		} catch(RuntimeException e) {
			throw e;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		} catch(Exception e) {
			throw new RuntimeException(e);
		} finally {
			System.setProperty("https.protocols", protocols); //$NON-NLS-1$
			HttpsURLConnection.setDefaultSSLSocketFactory(sslFactory);
		}
	}
	
	@FunctionalInterface
	public static interface IOFunction<T> {
		T apply(String contentType, InputStream is) throws IOException;
	}
	
	/**
	 * @param <T> the expected return type
	 * @param url the URL to fetch
	 * @param consumer a handler for the download's {@link InputStream}
	 * @return the consumed value
	 * @throws IOException if there is an unexpected problem downloading the file or if the server
	 * 		returns any code other than {@link HttpURLConnection#HTTP_OK}
	 * @since 2.0.0
	 */
	public static <T> T download(URL url, IOFunction<T> consumer) throws IOException {
		return withHttpsContext(() -> {
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			int responseCode = conn.getResponseCode();
			try {
				if(responseCode >= 300 && responseCode < 400) {
					// Passively handle the redirect
					String location = conn.getHeaderField("Location"); //$NON-NLS-1$
					return download(new URL(location), consumer);
				}
				
				if(responseCode != HttpURLConnection.HTTP_OK) {
					throw new IOException(format(Messages.getString("OpenLibertyUtil.unexpectedResponseCodeFromUrl"), responseCode, url)); //$NON-NLS-1$
				}
				try(InputStream is = conn.getInputStream()) {
					String contentType = conn.getHeaderField("Content-Type"); //$NON-NLS-1$
					if(StringUtil.isEmpty(contentType) || "application/octet-stream".equals(contentType)) { //$NON-NLS-1$
						// Check for a Content-Disposition header
						String disp = conn.getHeaderField("Content-Disposition"); //$NON-NLS-1$
						if(StringUtil.isNotEmpty(disp) && disp.toLowerCase().startsWith("attachment; filename=")) { //$NON-NLS-1$
							String fileName = disp.substring("attachment; filename=".length()).toLowerCase(); //$NON-NLS-1$
							if(fileName.endsWith(".zip")) { //$NON-NLS-1$
								contentType = "application/zip"; //$NON-NLS-1$
							} else if(fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) { //$NON-NLS-1$ //$NON-NLS-2$
								contentType = "application/gzip"; //$NON-NLS-1$
							}
						}
					}
					return consumer.apply(contentType, is);
				}
			} finally {
				conn.disconnect();
			}
		});
	}
	
	/**
	 * @return the path of the active Domino program
	 * @since 3.0.0
	 */
	public static String getDominoProgramDirectory() {
		try {
			return DominoThreadFactory.getExecutor().submit(() -> {
				Session s = NotesFactory.createSession();
				try {
					return s.getEnvironmentString("NotesProgram", true); //$NON-NLS-1$
				} finally {
					s.recycle();
				}
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * @return the path of the active Domino data directory
	 * @since 3.0.0
	 */
	public static String getDominoDataDirectory() {
		try {
			return DominoThreadFactory.getExecutor().submit(() -> {
				Session s = NotesFactory.createSession();
				try {
					return s.getEnvironmentString("Directory", true); //$NON-NLS-1$
				} finally {
					s.recycle();
				}
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Loads extensions for the given service class, using the service class's ClassLoader.
	 * 
	 * @param <T> the service type to load
	 * @param extensionClass a {@link Class} object representing {@code <T>}
	 * @return a {@link Stream} of extension implementations
	 * @since 2.0.0
	 */
	public static <T> Stream<T> findExtensions(Class<T> extensionClass) {
		return StreamSupport.stream(ServiceLoader.load(extensionClass, extensionClass.getClassLoader()).spliterator(), false);
	}
	
	/**
	 * Loads a single extension for the given service class, using the service class's ClassLoader.
	 * 
	 * @param <T> the service type to load
	 * @param extensionClass a {@link Class} object representing {@code <T>}
	 * @return an {@link Optional} containing the first implementation, or an empty one if none are provided
	 * @since 2.0.0
	 */
	public static <T> Optional<T> findExtension(Class<T> extensionClass) {
		return findExtensions(extensionClass).findFirst();
	}
	
	/**
	 * Loads a single extension for the given service class, using the service class's ClassLoader and throwing
	 * {@link IllegalStateException} when no implementation is available.
	 * 
	 * @param <T> the service type to load
	 * @param extensionClass a {@link Class} object representing {@code <T>}
	 * @return the first available implementation of {@code <T>}
	 * @throws IllegalStateException if no implementation of {@code <T>} can be found
	 * @since 3.0.0
	 */
	public static <T> T findRequiredExtension(Class<T> extensionClass) {
		return findExtension(extensionClass)
			.orElseThrow(() -> new IllegalStateException(format(Messages.getString("OpenLibertyUtil.unableToFindServiceProviding"), extensionClass.getName()))); //$NON-NLS-1$
	}
	
	/**
	 * Recursively deletes the named file or directory.
	 * 
	 * @param path the file or directory to delete
	 * @throws IOException if there is a problem deleting the target
	 * @since 2.1.0
	 */
	public static void deltree(Path path) throws IOException {
		if(Files.isDirectory(path)) {
			Files.list(path)
			    .forEach(t -> {
					try {
						deltree(t);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
		}
		try {
			Files.deleteIfExists(path);
		} catch(IOException e) {
		}
	}
	
	/**
	 * Performs shutdown cleanup, such as deleting temporary files. This should be called when
	 * the runtime is stopping.
	 * 
	 * @since 2.1.0
	 */
	public static void performShutdownCleanup() {
		DominoThreadFactory.term();
		if(tempDirectory != null) {
			try {
				deltree(tempDirectory);
			} catch (IOException e) {
			}
			tempDirectory = null;
		}
	}
}
