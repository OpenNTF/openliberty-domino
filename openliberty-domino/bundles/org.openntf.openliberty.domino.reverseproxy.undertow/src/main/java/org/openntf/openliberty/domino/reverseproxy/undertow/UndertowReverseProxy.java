package org.openntf.openliberty.domino.reverseproxy.undertow;

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
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;

public class UndertowReverseProxy implements Runnable {
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
	
	public UndertowReverseProxy() {
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
