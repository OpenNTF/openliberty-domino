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
package org.openntf.openliberty.domino.reverseproxy;

import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.undertow.UndertowReverseProxy;

public class ReverseProxyService implements RuntimeService {
	private static final Logger log = OpenLibertyLog.getLog();
	
	@Override
	public void run() {
		try {
			ReverseProxyConfig config = OpenLibertyUtil.findExtension(ReverseProxyConfigProvider.class)
				.map(provider -> provider.createConfiguration(this))
				.orElseThrow(() -> new IllegalStateException(MessageFormat.format("Unable to find provider for {0}", ReverseProxyConfigProvider.class.getName())));
			
			if(!config.enabled) {
				if(log.isLoggable(Level.INFO)) {
					OpenLibertyLog.instance.out.println("Reverse proxy disabled");
				}
				return;
			}
			
			UndertowReverseProxy proxy = new UndertowReverseProxy();
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
