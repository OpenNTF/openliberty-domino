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
package org.openntf.openliberty.domino.httpservice;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.DominoThreadFactory;

import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

public class OpenLibertyService extends HttpService {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;

	public OpenLibertyService(LCDEnvironment env) {
		super(env);
		
		DominoThreadFactory.executor.submit(OpenLibertyRuntime.instance);
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		if(log.isLoggable(Level.INFO)) {
			log.info("Shutting down Open Liberty server");
		}
	}



	@Override
	public void refreshSettings() {
		super.refreshSettings();
		
	}



	@Override
	public Object tellCommand(String command) {
		return super.tellCommand(command);
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
