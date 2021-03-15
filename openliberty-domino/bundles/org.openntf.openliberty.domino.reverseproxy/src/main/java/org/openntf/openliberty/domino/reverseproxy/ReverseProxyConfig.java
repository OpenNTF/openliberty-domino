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
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

public class ReverseProxyConfig {
	public static final int PORT_DISABLED = -1;
	
	private boolean enabled = true;
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
	
	public void setGlobalEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	public boolean isGlobalEnabled() {
		return this.enabled;
	}
	public boolean isEnabled(ReverseProxyService proxy) {
		return enabled;
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
