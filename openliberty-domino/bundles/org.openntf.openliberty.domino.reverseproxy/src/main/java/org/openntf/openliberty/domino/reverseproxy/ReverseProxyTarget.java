package org.openntf.openliberty.domino.reverseproxy;

import java.net.URI;

/**
 * Represents the configuration for a backing app server for the reverse proxy. 
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ReverseProxyTarget {
	private final URI uri;
	private final boolean useXForwardedFor;
	private final boolean useWsHeaders;

	public ReverseProxyTarget(URI uri, boolean useXForwardedFor, boolean useWsHeaders) {
		this.uri = uri;
		this.useXForwardedFor = useXForwardedFor;
		this.useWsHeaders = useWsHeaders;
	}
	
	public URI getUri() {
		return uri;
	}
	public boolean isUseWsHeaders() {
		return useWsHeaders;
	}
	public boolean isUseXForwardedFor() {
		return useXForwardedFor;
	}
}
