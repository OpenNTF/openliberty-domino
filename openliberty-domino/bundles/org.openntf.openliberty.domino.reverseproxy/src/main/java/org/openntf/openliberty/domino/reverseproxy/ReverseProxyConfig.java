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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.net.ssl.SSLContext;

public class ReverseProxyConfig {
	public static final int PORT_DISABLED = -1;
	
	private boolean globalEnabled = true;
	private Collection<String> enabledTypes = new HashSet<>();
	
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
	
	private Map<String, ReverseProxyTarget> targets = new HashMap<>();
	
	public void setGlobalEnabled(boolean globalEnabled) {
		this.globalEnabled = globalEnabled;
	}
	public boolean isGlobalEnabled() {
		return this.globalEnabled;
	}
	public void addEnabledType(String enabledType) {
		this.enabledTypes.add(enabledType);
	}
	public boolean isEnabled(ReverseProxyService proxy) {
		return globalEnabled && enabledTypes.contains(proxy.getProxyType());
	}
	
	/**
	 * @return a {@link Map} of app context paths to server configurations
	 */
	public Map<String, ReverseProxyTarget> getTargets() {
		return this.targets;
	}
	
	public ReverseProxyConfig addTarget(String contextPath, ReverseProxyTarget target) {
		this.targets.put(contextPath, target);
		return this;
	}
}
