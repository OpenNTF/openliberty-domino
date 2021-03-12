package org.openntf.openliberty.domino.reverseproxy.ext;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

public class ReverseProxyConfig {
	public static final int PORT_DISABLED = -1;
	
	public boolean enabled = true;
	public String proxyHostName;
	public int proxyHttpPort = PORT_DISABLED;
	public int proxyHttpsPort = PORT_DISABLED;
	public SSLContext proxyHttpsContext;
	public long maxEntitySize;
	
	public String dominoHostName = "localhost";
	public int dominoHttpPort;
	public boolean dominoHttps;
	public boolean useDominoConnectorHeaders;
	public String dominoConnectorHeadersSecret;
	
	private Map<String, URI> targets = new HashMap<>();
	
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
