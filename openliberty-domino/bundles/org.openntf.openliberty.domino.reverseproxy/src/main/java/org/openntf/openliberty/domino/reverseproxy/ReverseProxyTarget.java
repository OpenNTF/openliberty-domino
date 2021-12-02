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

	@Override
	public String toString() {
		return String.format("ReverseProxyTarget [uri=%s, useXForwardedFor=%s, useWsHeaders=%s]", //$NON-NLS-1$
				uri, useXForwardedFor, useWsHeaders);
	}
}
