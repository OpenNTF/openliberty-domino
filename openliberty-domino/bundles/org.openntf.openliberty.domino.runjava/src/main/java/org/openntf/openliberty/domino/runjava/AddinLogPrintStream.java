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
package org.openntf.openliberty.domino.runjava;

import org.openntf.openliberty.domino.config.RuntimeProperties;
import org.openntf.openliberty.domino.ext.LoggerPrintStream;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

public class AddinLogPrintStream extends LoggerPrintStream {
	private static AddInLogBridge context;
	public static void setBridge(AddInLogBridge bridge) {
		context = bridge;
	}
	
	private final String prefix;
	
	
	public AddinLogPrintStream() {
		super(System.out);
		this.prefix = RuntimeProperties.instance.getPrefix();
	}
	
	@Override
	protected void _line(String message) {
		if(context != null) {
			if(StringUtil.isEmpty(prefix)) {
				context.AddInLogMessageText(StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				context.AddInLogMessageText(prefix + ": " + StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		} else {
			if(StringUtil.isEmpty(prefix)) {
				System.out.println(StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				System.out.println(prefix + ": " + StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}
}
