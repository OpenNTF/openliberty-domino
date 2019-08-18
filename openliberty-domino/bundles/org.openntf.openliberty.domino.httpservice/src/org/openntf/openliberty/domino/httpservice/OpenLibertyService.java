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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.DominoThreadFactory;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

public class OpenLibertyService extends HttpService {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	
	private Future<?> runner;

	public OpenLibertyService(LCDEnvironment env) {
		super(env);
		
		start();
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		if(this.runner != null) {
			if(log.isLoggable(Level.INFO)) {
				log.info("Shutting down Open Liberty server");
			}
			stop();
		}
	}

	@Override
	public Object tellCommand(String command) {
		if(StringUtil.isNotEmpty(command) && command.toLowerCase().startsWith("wlp")) {
			if("wlp status".equalsIgnoreCase(command)) {
				OpenLibertyRuntime.instance.showStatus();
				return "Status of running server(s):";
			} else if("wlp stop".equalsIgnoreCase(command)) {
				if(this.runner == null) {
					return "Open Liberty server is not running";
				} else {
					stop();
					return "Stopped Open Liberty server";
				}
			} else if("wlp start".equalsIgnoreCase(command)) {
				if(this.runner != null) {
					return "Open Liberty server is already running";
				} else {
					start();
					return "Starting Open Libery server";
				}
			} else if("wlp restart".equalsIgnoreCase(command)) {
				if(this.runner != null) {
					stop();
					try {
						TimeUnit.SECONDS.sleep(3);
					} catch (InterruptedException e) {
					}
					start();
					return "Restarted Open Libery server";
				} else {
					start();
					return "Starting Open Liberty server";
				}
			}
		}
		
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

	// *******************************************************************************
	// * Internal methods
	// *******************************************************************************
	
	/**
	 * @since 1.2.0
	 */
	private void start() {
		if(this.runner == null) {
			DominoThreadFactory.init();
			this.runner = DominoThreadFactory.executor.submit(OpenLibertyRuntime.instance);
		}
	}
	/**
	 * @since 1.2.0
	 */
	private void stop() {
		if(this.runner != null) {
			DominoThreadFactory.term();
			this.runner = null;
		}
	}
}
