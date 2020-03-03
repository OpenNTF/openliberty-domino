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
package org.openntf.openliberty.domino.httpservice;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.CLIManagerDelegate;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

public class OpenLibertyService extends HttpService {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	private final CLIManagerDelegate delegate = new CLIManagerDelegate();

	public OpenLibertyService(LCDEnvironment env) {
		super(env);
		
		delegate.start();
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		if(log.isLoggable(Level.INFO)) {
			log.info("Shutting down Open Liberty service");
		}
		delegate.close();
	}

	@Override
	public Object tellCommand(String line) {
		if(StringUtil.isNotEmpty(line) && line.toLowerCase().startsWith("wlp ")) { //$NON-NLS-1$
			delegate.processCommand(line.substring("wlp ".length())); //$NON-NLS-1$
		}
		
		return super.tellCommand(line);
	}



	@Override
	public boolean doService(String contextPath, String path, HttpSessionAdapter httpSession, HttpServletRequestAdapter httpRequest,
			HttpServletResponseAdapter httpResponse) throws ServletException, IOException {
		// NOP
		return false;
	}

	@Override
	public void getModules(List<ComponentModule> modules) {
		// NOP
	}
}
