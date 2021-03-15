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
package org.openntf.openliberty.domino.reverseproxy.standalone;

import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;

/**
 * Reverse proxy implementation that opens a proxy on a configured port and supports
 * HTTP/2.
 * 
 * @author Jesse Gallagher
 * @since 2.1.0
 */
public class StandaloneReverseProxyService implements RuntimeService, ReverseProxyService {
	private static final Logger log = OpenLibertyLog.getLog();
	
	public static final String TYPE = "Standalone";
	
	@Override
	public String getProxyType() {
		return TYPE;
	}
	
	@Override
	public void run() {
		try {
			ReverseProxyConfigProvider configProvider = OpenLibertyUtil.findRequiredExtension(ReverseProxyConfigProvider.class);
			ReverseProxyConfig config = configProvider.createConfiguration();
			
			if(!config.isEnabled(this)) {
				return;
			}
			
			ReverseProxyImpl proxy = new ReverseProxyImpl();
			proxy.setLogger(log);
			
			proxy.setProxyHostName(config.proxyHostName);
			proxy.setProxyHttpPort(config.proxyHttpPort);
			proxy.setProxyHttpsPort(config.proxyHttpsPort);
			proxy.setProxyHttpsContext(config.proxyHttpsContext);
			proxy.setMaxEntitySize(config.maxEntitySize);
			
			proxy.setDominoHostName(config.dominoHostName);
			proxy.setDominoHttpPort(config.dominoHttpPort);
			proxy.setDominoHttps(config.dominoHttps);
			if(config.useDominoConnectorHeaders) {
				proxy.setUseDominoConnectorHeaders(true);
				proxy.setDominoConnectorHeadersSecret(config.dominoConnectorHeadersSecret);
			}
			proxy.setTargets(config.getTargets());
			
			DominoThreadFactory.executor.submit(proxy);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
