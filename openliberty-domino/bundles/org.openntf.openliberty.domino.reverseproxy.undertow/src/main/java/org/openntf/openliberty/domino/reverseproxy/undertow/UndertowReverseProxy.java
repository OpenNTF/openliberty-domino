package org.openntf.openliberty.domino.reverseproxy.undertow;

import java.io.PrintStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import io.undertow.Undertow;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;

public class UndertowReverseProxy implements Runnable {
	private PrintStream out = System.out;
	
	private String proxyHostName = "0.0.0.0";
	private int proxyPort = 8080;
	
	private int dominoHttpPort = 80;
	private String dominoHostName = "localhost";
	private boolean dominoHttps = false;
	private boolean useDominoConnectorHeaders = false;
	private String dominoConnectorHeadersSecret;

	private Map<String, URI> targets = new HashMap<>();
	
	public UndertowReverseProxy() {
	}
	
	public void setPrintStream(PrintStream out) {
		this.out = out;
	}
	
	public void setProxyHostName(String proxyHostName) {
		this.proxyHostName = proxyHostName;
	}
	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
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
			PathHandler pathHandler = new PathHandler();
			
			// Add handlers for each target
			Map<String, URI> targets = this.targets;
			if(targets != null) {
				for(Map.Entry<String, URI> target : targets.entrySet()) {
					String contextRoot = "/" + target.getKey();
					URI targetUri = target.getValue();
					
					LoadBalancingProxyClient appProxy = new LoadBalancingProxyClient().addHost(targetUri);
					ProxyHandler.Builder proxyHandler = ProxyHandler.builder().setProxyClient(appProxy);
					
					System.out.println("adding prefix path for " + contextRoot);
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

			out.println("hello there, friend - ask me for " + proxyPort);
			Undertow server = Undertow.builder()
	            .addHttpListener(proxyPort, proxyHostName)
	            .setHandler(pathHandler)
	            .build();
	        server.start();
		} catch(Throwable t) {
			t.printStackTrace();
		}
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
