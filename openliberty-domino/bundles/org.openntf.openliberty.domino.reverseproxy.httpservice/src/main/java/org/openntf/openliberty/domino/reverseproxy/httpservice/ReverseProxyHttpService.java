/*
 * Copyright © 2018-2022 Jesse Gallagher
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
package org.openntf.openliberty.domino.reverseproxy.httpservice;

import java.io.IOException;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import org.openntf.openliberty.domino.event.EventRecipient;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyTarget;
import org.openntf.openliberty.domino.reverseproxy.event.ReverseProxyConfigChangedEvent;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

/**
 * Reverse proxy implementation that resides in Domino's HTTP task.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ReverseProxyHttpService extends HttpService implements ReverseProxyService, EventRecipient {
	private static final Logger log = OpenLibertyLog.getLog();

	public static final String TYPE = "NHTTP"; //$NON-NLS-1$
	private boolean enabled;
	private Map<String, ComponentModule> targets;

	public ReverseProxyHttpService(LCDEnvironment env) {
		super(env);

		try {
			ReverseProxyConfigProvider configProvider = OpenLibertyUtil.findRequiredExtension(ReverseProxyConfigProvider.class);
			ReverseProxyConfig config = configProvider.createConfiguration();
	
			this.enabled = config.isEnabled(this);
			if (!enabled) {
				this.targets = Collections.emptyMap();
			} else {
				if (log.isLoggable(Level.INFO)) {
					log.info("NHTTP reverse proxy enabled");
				}
				this.targets = buildModules(config.getTargets());
			}
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
		OpenLibertyRuntime.instance.registerMessageRecipient(this);
	}

	@Override
	public String getProxyType() {
		return TYPE;
	}
	
	@Override
	public void notifyMessage(EventObject event) {
		if(event instanceof ReverseProxyConfigChangedEvent) {
			this.targets.values().forEach(ComponentModule::destroyModule);
			ReverseProxyConfig config = ((ReverseProxyConfigChangedEvent)event).getSource();
			this.enabled = config.isEnabled(this);
			this.targets = buildModules(config.getTargets());
		}
	}

	@Override
	public boolean isXspUrl(String fullPath, boolean arg1) {
		if (!enabled) {
			return false;
		}
		String pathInfo = getChompedPathInfo(fullPath);
		boolean match = this.targets.keySet().stream()
			.anyMatch(contextRoot -> pathInfo.equals(contextRoot) || pathInfo.startsWith(contextRoot + '/'));
		return match;
	}

	@Override
	public boolean doService(String arg0, String fullPath, HttpSessionAdapter httpSessionAdapter,
			HttpServletRequestAdapter servletRequest, HttpServletResponseAdapter servletResponse)
			throws ServletException, IOException {
		if (!enabled) {
			return false;
		}
		String pathInfo = getChompedPathInfo(fullPath);
		if (StringUtil.isEmpty(fullPath)) {
			return false;
		}
		Optional<ComponentModule> target = this.targets.entrySet()
			.stream()
			.filter(entry -> pathInfo.equals(entry.getKey()) || pathInfo.startsWith(entry.getKey() + '/'))
			.map(Map.Entry::getValue)
			.findFirst();
		if (target.isPresent()) {
			target.get().doService(arg0, fullPath, httpSessionAdapter, servletRequest, servletResponse);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void getModules(List<ComponentModule> modules) {
		modules.addAll(this.targets.values());
	}
	
	@Override
	public void destroyService() {
		// NOP
	}
	
	// *******************************************************************************
	// * Internal implementation methods
	// *******************************************************************************
	
	private String getChompedPathInfo(String fullPath) {
		if (StringUtil.isEmpty(fullPath)) {
			return StringUtil.EMPTY_STRING;
		} else {
			int qIndex = fullPath.indexOf('?');
			if (qIndex >= 0) {
				return fullPath.substring(1, qIndex);
			} else {
				return fullPath.substring(1);
			}
		}
	}
	
	private Map<String, ComponentModule> buildModules(Map<String, ReverseProxyTarget> targets) {
		return targets.entrySet()
			.stream()
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				entry -> {
					ReverseProxyModule module = new ReverseProxyModule(this.getEnvironment(), this, entry.getKey(), entry.getValue());
					module.initModule();
					return module;
				}
			));
	}

}
