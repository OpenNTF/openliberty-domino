/*
 * © Copyright IBM Corp. 2012-2013
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */

package org.openntf.openliberty.domino.util.commons.ibm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Contains methods extracted from IBM Commons StreamUtil.
 */
public class StreamUtil {
    
    /**
     * Read a string from an input stream using the default encoding.
     * @param is the stream to read
     * @return the stream content
	 * @throws IOException if there is an underlying problem
     */
	public static String readString(InputStream is) throws IOException {
		FastStringBuffer sb = new FastStringBuffer();
		sb.load(new InputStreamReader(is, StandardCharsets.UTF_8));
		return sb.toString();
	}
}
