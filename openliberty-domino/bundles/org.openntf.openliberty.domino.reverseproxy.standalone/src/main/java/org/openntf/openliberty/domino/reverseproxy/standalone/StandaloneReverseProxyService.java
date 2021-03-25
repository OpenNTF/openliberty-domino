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

import org.openntf.openliberty.domino.util.OpenLibertyUtil;

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
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;

import java.net.URI;
import java.text.MessageFormat;
import java.util.EventObject;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.event.EventRecipient;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyTarget;
import org.openntf.openliberty.domino.reverseproxy.event.ReverseProxyConfigChangedEvent;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;

/**
 * Reverse proxy implementation that opens a proxy on a configured port and supports
 * HTTP/2.
 * 
 * @author Jesse Gallagher
 * @since 2.1.0
 */
public class StandaloneReverseProxyService implements RuntimeService, ReverseProxyService, EventRecipient {
	private static final Logger log = OpenLibertyLog.getLog();
	
	public static final String TYPE = "Standalone"; //$NON-NLS-1$

	private Undertow server;
	ReverseProxyConfig config;
	private int configHash;
	
	@Override
	public String getProxyType() {
		return TYPE;
	}

	@Override
	public void notifyMessage(EventObject event) {
		if(event instanceof ReverseProxyConfigChangedEvent) {
			ReverseProxyConfig config = ((ReverseProxyConfigChangedEvent)event).getSource();
			int newHash = config.hashCode();
			if(this.configHash != newHash) {
				this.config = config;
				this.configHash = newHash;
				
				refreshServer();
			}
		}
	}
	
	@Override
	public void run() {
		try {
			ReverseProxyConfigProvider configProvider = OpenLibertyUtil.findRequiredExtension(ReverseProxyConfigProvider.class);
			this.config = configProvider.createConfiguration();
			this.configHash = this.config.hashCode();
			
			refreshServer();
		} catch(Throwable t) {
			t.printStackTrace();
		}
		
		OpenLibertyRuntime.instance.registerMessageRecipient(this);
	}
	
	private void refreshServer() {
		if(this.server != null) {
			this.server.stop();
			this.server = null;
		}
		if(this.config.isEnabled(this)) {
			this.server = startServer();
		}
	}
	
	private synchronized Undertow startServer() {
		PathHandler pathHandler = new PathHandler();
		
		// Add handlers for each target
		Map<String, ReverseProxyTarget> targets = this.config.getTargets();
		if(targets != null) {
			for(Map.Entry<String, ReverseProxyTarget> target : targets.entrySet()) {
				String contextRoot = "/" + target.getKey(); //$NON-NLS-1$
				URI targetUri = target.getValue().getUri();
				
				LoadBalancingProxyClient appProxy = new LoadBalancingProxyClient().addHost(targetUri);
				ProxyHandler.Builder proxyHandler = ProxyHandler.builder().setProxyClient(appProxy);
				
				if(target.getValue().isUseWsHeaders()) {
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRH"), RemoteHostAttribute.INSTANCE); //$NON-NLS-1$
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRA"), RemoteIPAttribute.INSTANCE); //$NON-NLS-1$
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSC"), RequestSchemeAttribute.INSTANCE); //$NON-NLS-1$
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSPR"), RequestProtocolAttribute.INSTANCE); //$NON-NLS-1$
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSP"), LocalPortAttribute.INSTANCE); //$NON-NLS-1$
					proxyHandler.addRequestHeader(HttpString.tryFromString("$WSIS"), SecureExchangeAttribute.INSTANCE); //$NON-NLS-1$
				}
				
				if(log.isLoggable(Level.FINE)) {
					log.fine(MessageFormat.format("Reverse proxy: adding prefix path for {0}", contextRoot));
				}
				pathHandler.addPrefixPath(contextRoot, proxyHandler.build());
			}
		}
		
		// Construct the Domino proxy
		{
			boolean dominoHttps = config.dominoHttps;
			String dominoHostName = config.dominoHostName;
			int dominoHttpPort = config.dominoHttpPort;
			String dominoUri = MessageFormat.format("http{0}://{1}:{2}", dominoHttps ? "s" : "", dominoHostName, Integer.toString(dominoHttpPort)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			LoadBalancingProxyClient dominoProxy = new LoadBalancingProxyClient().addHost(URI.create(dominoUri));
			
			ProxyHandler.Builder proxyHandler = ProxyHandler.builder()
            		.setProxyClient(dominoProxy);
			if(config.useDominoConnectorHeaders) {
				proxyHandler.addRequestHeader(HttpString.tryFromString("X-ConnectorHeaders-Secret"), new StringAttribute(config.dominoConnectorHeadersSecret)); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRH"), RemoteHostAttribute.INSTANCE); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSRA"), RemoteIPAttribute.INSTANCE); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSC"), RequestSchemeAttribute.INSTANCE); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSPR"), RequestProtocolAttribute.INSTANCE); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSSP"), LocalPortAttribute.INSTANCE); //$NON-NLS-1$
				proxyHandler.addRequestHeader(HttpString.tryFromString("$WSIS"), SecureExchangeAttribute.INSTANCE); //$NON-NLS-1$
			}
			pathHandler.addPrefixPath("/", proxyHandler.build()); //$NON-NLS-1$
		}

		Undertow.Builder serverBuilder = Undertow.builder()
			.setHandler(pathHandler)
			.setServerOption(UndertowOptions.ENABLE_HTTP2, true)
			.setServerOption(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH, true)
			.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, config.maxEntitySize)
			// Obligatory for XPages minifiers
			.setServerOption(UndertowOptions.ALLOW_ENCODED_SLASH, true);
		if(config.proxyHttpPort != -1) {
			if(config.redirectHttpToHttps) {
				if(config.proxyHttpsPort == ReverseProxyConfig.PORT_DISABLED) {
					throw new IllegalStateException("HTTP-to-HTTPS redirection cannot be enabled when HTTPS is disabled");
				}
				serverBuilder.addHttpListener(config.proxyHttpPort, config.proxyHostName, new RedirectHandler(new HttpRedirectAttribute(config.proxyHttpsPort)));
			} else {
				serverBuilder.addHttpListener(config.proxyHttpPort, config.proxyHostName);
			}
		}
		if(config.proxyHttpsPort != -1) {
			serverBuilder.addHttpsListener(config.proxyHttpsPort, config.proxyHostName, config.proxyHttpsContext);
		}
		Undertow server = serverBuilder.build();
		server.start();

		if(log.isLoggable(Level.INFO)) {
			log.info(MessageFormat.format("Reverse proxy listening on {0}:{1}", config.proxyHostName, Integer.toString(config.proxyHttpPort)));
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
	
	private static class HttpRedirectAttribute implements ExchangeAttribute {
		private final int httpsPort;
		public HttpRedirectAttribute(int httpsPort) {
			this.httpsPort = httpsPort;
		}

		@Override
		public String readAttribute(HttpServerExchange exchange) {
			String requestPath = exchange.getRequestPath();
			String requestHost = exchange.getHostName();
			String portPart = httpsPort == 443 ? "" : (":" + httpsPort); //$NON-NLS-1$ //$NON-NLS-2$
			return MessageFormat.format("https://{0}{1}{2}", requestHost, portPart, requestPath); //$NON-NLS-1$
		}

		@Override
		public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException { }
	}
}
