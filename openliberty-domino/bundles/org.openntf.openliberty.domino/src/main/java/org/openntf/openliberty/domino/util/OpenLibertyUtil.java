/*
 * Copyright © 2018-2020 Jesse Gallagher
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

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
	
	/**
	 * Returns an appropriate temp directory for the system. On Windows, this is
	 * equivalent to <code>System.getProperty("java.io.tmpdir")</code>. On
	 * Linux, however, since this seems to return the data directory in some
	 * cases, it uses <code>/tmp</code>.
	 *
	 * @return an appropriate temp directory for the system
	 */
	public static String getTempDirectory() {
		if (!IS_WINDOWS) {
			return "/tmp"; //$NON-NLS-1$
		} else {
			return System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
		}
	}
	
	@FunctionalInterface
	public static interface IOFunction<T> {
		T apply(InputStream is) throws IOException;
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
		// Domino defaults to using old protocols - bump this up for our needs here so the connection succeeds
		String protocols = StringUtil.toString(System.getProperty("https.protocols")); //$NON-NLS-1$
		try {
			System.setProperty("https.protocols", "TLSv1.2"); //$NON-NLS-1$ //$NON-NLS-2$
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			int responseCode = conn.getResponseCode();
			try {
				if(responseCode != HttpURLConnection.HTTP_OK) {
					throw new IOException(format(Messages.getString("OpenLibertyUtil.unexpectedResponseCodeFromUrl"), responseCode, url)); //$NON-NLS-1$
				}
				try(InputStream is = conn.getInputStream()) {
					return consumer.apply(is);
				}
			} finally {
				conn.disconnect();
			}
		} finally {
			System.setProperty("https.protocols", protocols); //$NON-NLS-1$
		}
	}
	
	/**
	 * @return the path of the active Domino program
	 * @since 3.0.0
	 */
	public static String getDominoProgramDirectory() {
		try {
			return DominoThreadFactory.executor.submit(() -> {
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
}
