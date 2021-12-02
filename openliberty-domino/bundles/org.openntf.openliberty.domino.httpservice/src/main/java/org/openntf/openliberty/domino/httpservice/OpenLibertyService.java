/*
 * Copyright © 2018-2021 Jesse Gallagher
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
import org.openntf.openliberty.domino.util.DominoThreadFactory;

import com.darwino.domino.napi.DominoAPI;
import com.darwino.domino.napi.DominoException;
import com.darwino.domino.napi.wrap.NSFMessageQueue;
import com.darwino.domino.napi.wrap.NSFSession;
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
		
		DominoThreadFactory.getExecutor().submit(() -> {
			long hDesc = DominoAPI.get().AddInCreateStatusLine(Messages.getString("OpenLibertyService.taskName")); //$NON-NLS-1$
			DominoAPI.get().AddInSetStatusLine(hDesc, Messages.getString("OpenLibertyService.statusRunning")); //$NON-NLS-1$
			try {
				NSFSession session = new NSFSession(DominoAPI.get());
				try {
					NSFMessageQueue queue = session.getMessageQueue("MQ$WLP", true); //$NON-NLS-1$
					try {
						String message;
						while((message = queue.take()) != null) {
							if(StringUtil.isNotEmpty(message)) {
								OpenLibertyLog.instance.out.println(delegate.processCommand(message));
							}
						}
					} catch (InterruptedException e) {
						// This is expected
					} finally {
						queue.free();
					}
				} catch (DominoException e) {
					e.printStackTrace();
				} finally {
					session.free();
				}
			} finally {
				DominoAPI.get().AddInDeleteStatusLine(hDesc);
			}
		});
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		if(log.isLoggable(Level.INFO)) {
			log.info(Messages.getString("OpenLibertyService.shuttingDown")); //$NON-NLS-1$
		}
		delegate.close();
	}

	@Override
	public Object tellCommand(String line) {
		if(StringUtil.isNotEmpty(line) && line.toLowerCase().startsWith("wlp ")) { //$NON-NLS-1$
			return delegate.processCommand(line.substring("wlp ".length())); //$NON-NLS-1$
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
