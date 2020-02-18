/**
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
package org.openntf.openliberty.domino.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import com.ibm.commons.util.StringUtil;

public class DominoConsoleFormatter extends SimpleFormatter {
	
	private String prefix;
	private boolean includeDate;
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); //$NON-NLS-1$

	public DominoConsoleFormatter(String prefix, boolean includeDate) {
		this.prefix = prefix;
		this.includeDate = includeDate;
	}

	@Override
	public synchronized String format(LogRecord record) {
		StringBuffer sb = new StringBuffer() ;
		
		if (includeDate) {
			sb.append(dateFormat.format(new Date()) + " ");  //$NON-NLS-1$
		}
		
		if(StringUtil.isNotEmpty(prefix)) {
			sb.append(prefix + ": "); //$NON-NLS-1$
		}
		sb.append(record.getLevel() + " "); //$NON-NLS-1$
		sb.append(record.getMessage());
		sb.append("\n"); //$NON-NLS-1$
		
		return sb.toString();
	}	
}