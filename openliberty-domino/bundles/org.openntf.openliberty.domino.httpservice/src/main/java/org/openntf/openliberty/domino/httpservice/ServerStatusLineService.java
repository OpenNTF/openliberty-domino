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

import java.text.MessageFormat;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openntf.openliberty.domino.event.ServerDeployEvent;
import org.openntf.openliberty.domino.event.ServerStartEvent;
import org.openntf.openliberty.domino.event.ServerStopEvent;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.server.ServerInstance;

import com.darwino.domino.napi.DominoAPI;
import com.ibm.commons.util.StringUtil;

public class ServerStatusLineService implements RuntimeService {
	
	private final Map<String, Long> statusLines = new HashMap<>();
	private final Object deleteSync = new Object();

	@Override
	public void run() {
		// Run until canceled
		try {
			while(true) {
				TimeUnit.DAYS.sleep(1);
			}
		} catch(InterruptedException e) {
			// Good
		} finally {
			synchronized(deleteSync) {
				statusLines.forEach((path, hDesc) -> DominoAPI.get().AddInDeleteStatusLine(hDesc));
			}
		}
	}
	
	@Override
	public void close() {
		// NOP - will be handled above
	}
	
	@Override
	public void notifyMessage(EventObject event) {
		if(event instanceof ServerStartEvent) {
			synchronized(deleteSync) {
				ServerInstance<?> instance = ((ServerStartEvent)event).getSource();
				statusLines.computeIfAbsent(instance.getServerName(), serverName -> {
					long result = DominoAPI.get().AddInCreateStatusLine(Messages.getString("ServerStatusLineService.serverTaskName")); //$NON-NLS-1$
					DominoAPI.get().AddInSetStatusLine(result, MessageFormat.format(Messages.getString("ServerStatusLineService.serverRunning"), serverName)); //$NON-NLS-1$
					return result;
				});
				
				updateStatusLine(instance);
			}
		} else if(event instanceof ServerStopEvent) {
			synchronized(deleteSync) {
				ServerInstance<?> instance = ((ServerStopEvent)event).getSource();
				Long hDesc = statusLines.get(instance.getServerName());
				if(hDesc != null) {
					DominoAPI.get().AddInDeleteStatusLine(hDesc);
				}
			}
		} else if(event instanceof ServerDeployEvent) {
			synchronized(deleteSync) {
				ServerInstance<?> instance = ((ServerStopEvent)event).getSource();
				updateStatusLine(instance);
			}
		}
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private void updateStatusLine(ServerInstance<?> instance) {
		Long hDesc = statusLines.get(instance.getServerName());
		if(hDesc != null) {
			String host = instance.getListeningHost();
			String ports = instance.getListeningPorts()
				.stream()
				.map(String::valueOf)
				.collect(Collectors.joining(", ")); //$NON-NLS-1$
			
			if(StringUtil.isNotEmpty(ports)) {
				String status = MessageFormat.format(Messages.getString("ServerStatusLineService.serverListeningOn"), instance.getServerName(), host, ports); //$NON-NLS-1$
				DominoAPI.get().AddInSetStatusLine(hDesc, status);
			}
		}
	}
}
