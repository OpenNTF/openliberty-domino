package org.openntf.openliberty.domino.reverseproxy.ext;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ReverseProxyConfig {
	public static final int PORT_DISABLED = -1;
	
	private boolean enabled = true;
	private String proxyHostName;
	private int proxyHttpPort;
	
	private String dominoHostName = "localhost";
	private int dominoHttpPort;
	private boolean dominoHttps;
	private boolean useDominoConnectorHeaders;
	private String dominoConnectorHeadersSecret;
	
	private Map<String, URI> targets = new HashMap<>();
	

	public boolean isEnabled() {
		return enabled;
	}
	public ReverseProxyConfig setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}
	
	public String getProxyHostName() {
		return proxyHostName;
	}
	public ReverseProxyConfig setProxyHostName(String proxyHostName) {
		this.proxyHostName = proxyHostName;
		return this;
	}
	
	public int getProxyHttpPort() {
		return proxyHttpPort;
	}
	public ReverseProxyConfig setProxyHttpPort(int proxyHttpPort) {
		this.proxyHttpPort = proxyHttpPort;
		return this;
	}
	
	public String getDominoHostName() {
		return this.dominoHostName;
	}
	public ReverseProxyConfig setDominoHostName(String dominoHostName) {
		this.dominoHostName = dominoHostName;
		return this;
	}

	public boolean isDominoHttps() {
		return dominoHttps;
	}
	public ReverseProxyConfig setDominoHttps(boolean dominoHttps) {
		this.dominoHttps = dominoHttps;
		return this;
	}
	
	public int getDominoHttpPort() {
		return this.dominoHttpPort;
	}
	public ReverseProxyConfig setDominoHttpPort(int port) {
		this.dominoHttpPort = port;
		return this;
	}
	
	public boolean isUseDominoConnectorHeaders() {
		return useDominoConnectorHeaders;
	}
	public ReverseProxyConfig setUseDominoConnectorHeaders(boolean useDominoConnectorHeaders) {
		this.useDominoConnectorHeaders = useDominoConnectorHeaders;
		return this;
	}
	public String getDominoConnectorHeadersSecret() {
		return dominoConnectorHeadersSecret;
	}
	public ReverseProxyConfig setDominoConnectorHeadersSecret(String dominoConnectorHeadersSecret) {
		this.dominoConnectorHeadersSecret = dominoConnectorHeadersSecret;
		return this;
	}
	
	/**
	 * @return a {@link Map} of app context paths to server URLs
	 */
	public Map<String, URI> getTargets() {
		return this.targets;
	}
	
	public ReverseProxyConfig addTarget(String contextPath, URI serverUri) {
		this.targets.put(contextPath, serverUri);
		return this;
	}
}
