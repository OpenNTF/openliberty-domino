/**
 * Copyright © 2018-2019 Jesse Gallagher
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

public enum OpenLibertyUtil {
	;
	
	public static final boolean IS_WINDOWS;
	static {
		String os = System.getProperty("os.name");
		IS_WINDOWS = os.toLowerCase().contains("windows");
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
}
