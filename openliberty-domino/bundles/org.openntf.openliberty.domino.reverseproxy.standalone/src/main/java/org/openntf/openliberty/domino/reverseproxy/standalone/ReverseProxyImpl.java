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

import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.LocalPortAttribute;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.attribute.RemoteHostAttribute;
import io.undertow.attribute.RemoteIPAttribute;
import io.undertow.attribute.RequestProtocolAttribute;
import io.undertow.attribute.RequestSchemeAttribute;
import io.undertow.attribute.SecureExchangeAttribute;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;

public class ReverseProxyImpl implements Runnable {
	private Logger log = Logger.getGlobal();
	
	private String proxyHostName = "0.0.0.0";
	private int proxyHttpPort = -1;
	private int proxyHttpsPort = -1;
	private SSLContext proxyHttpsContext;
	private long maxEntitySize = 10000;
	
	private int dominoHttpPort = 80;
	private String dominoHostName = "localhost";
	private boolean dominoHttps = false;
	private boolean useDominoConnectorHeaders = false;
	private String dominoConnectorHeadersSecret;

	private Map<String, URI> targets = new HashMap<>();
	
	private Undertow server;
	
	public ReverseProxyImpl() {
	}
	
	public void setLogger(Logger log) {
		this.log = log;
	}
	
	public void setProxyHostName(String proxyHostName) {
		this.proxyHostName = proxyHostName;
	}
	public void setProxyHttpPort(int proxyHttpPort) {
		this.proxyHttpPort = proxyHttpPort;
	}
	public void setProxyHttpsPort(int proxyHttpsPort) {
		this.proxyHttpsPort = proxyHttpsPort;
	}
	public void setProxyHttpsContext(SSLContext proxyHttpsContext) {
		this.proxyHttpsContext = proxyHttpsContext;
	}
	public void setMaxEntitySize(long maxEntitySize) {
		this.maxEntitySize = maxEntitySize;
	}
	
	public void setDominoHostName(String dominoHostName) {
		this.dominoHostName = dominoHostName;
	}
	public void setDominoHttpPort(int dominoHttpPort) {
		this.dominoHttpPort = dominoHttpPort;
	}
	public void setDominoHttps(boolean dominoHttps) {
		this.dominoHttps = dominoHttps;
	}
	public void setUseDominoConnectorHeaders(boolean useDominoConnectorHeaders) {
		this.useDominoConnectorHeaders = useDominoConnectorHeaders;
	}
	public void setDominoConnectorHeadersSecret(String dominoConnectorHeadersSecret) {
		this.dominoConnectorHeadersSecret = dominoConnectorHeadersSecret;
	}
	public void setTargets(Map<String, URI> targets) {
		this.targets = targets;
	}

	@Override
	public void run() {
		try {
			this.server = startServer();
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}

	public void stop() {
		this.server.stop();
	}
	
	private Undertow startServer() {
		PathHandler pathHandler = new PathHandler();
		
		// Add handlers for each target
		Map<String, URI> targets = this.targets;
		if(targets != null) {
			for(Map.Entry<String, URI> target : targets.entrySet()) {
				String contextRoot = "/" + target.getKey();
				URI targetUri = target.getValue();
				
				LoadBalancingProxyClient appProxy = new LoadBalancingProxyClient().addHost(targetUri);
				ProxyHandler.Builder proxyHandler = ProxyHandler.builder().setProxyClient(appProxy);
				
				if(log.isLoggable(Level.FINE)) {
					log.fine("Reverse proxy: adding prefix path for " + contextRoot);
				}
				pathHandler.addPrefixPath(contextRoot, proxyHandler.build());
			}
		}
		
		// Construct the Domino proxy
		{
			String dominoUri = MessageFormat.format("http{0}://{1}:{2}", dominoHttps ? "s" : "", dominoHostName, Integer.toString(dominoHttpPort));
			LoadBalancingProxyClient dominoProxy = new LoadBalancingProxyClient().addHost(URI.create(dominoUri));
			
			ProxyHandler.Builder proxyHandler = ProxyHandler.builder()
            		.setProxyClient(dominoProxy);
			if(useDominoConnectorHeaders) {
				proxyHandler.addRequestHeader(HttpString.tryFromString("X-ConnectorHeaders-Secret"), new StringAttribute(dominoConnectorHeadersSecret));
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRH"), RemoteHostAttribute.INSTANCE);
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRA"), RemoteIPAttribute.INSTANCE);
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSC"), RequestSchemeAttribute.INSTANCE);
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSPR"), RequestProtocolAttribute.INSTANCE);
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSP"), LocalPortAttribute.INSTANCE);
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSIS"), SecureExchangeAttribute.INSTANCE);
			}
			pathHandler.addPrefixPath("/", proxyHandler.build());
		}

		Undertow.Builder serverBuilder = Undertow.builder()
			.setHandler(pathHandler)
			.setServerOption(UndertowOptions.ENABLE_HTTP2, true)
			.setServerOption(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH, true)
			.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, maxEntitySize)
			// Obligatory for XPages minifiers
			.setServerOption(UndertowOptions.ALLOW_ENCODED_SLASH, true);
		if(proxyHttpPort != -1) {
			serverBuilder.addHttpListener(proxyHttpPort, proxyHostName);
		}
		if(proxyHttpsPort != -1) {
			serverBuilder.addHttpsListener(proxyHttpsPort, proxyHostName, proxyHttpsContext);
		}
		Undertow server = serverBuilder.build();
		server.start();

		if(log.isLoggable(Level.INFO)) {
			log.info(MessageFormat.format("Reverse proxy listening on {0}:{1}", proxyHostName, Integer.toString(proxyHttpPort)));
		}
		
		return server;
	}
	
	private static class StringAttribute implements ExchangeAttribute {
		private final String value;
		public StringAttribute(String value) {
			this.value = value;
		}

		@Override
		public String readAttribute(HttpServerExchange exchange) {
			return value;
		}

		@Override
		public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException { }
	}
}
