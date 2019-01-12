/**
 * Copyright © 2018-2019 Jesse Gallagher
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
package org.openntf.openliberty.wlp.userregistry;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import com.ibm.websphere.security.UserRegistry;
import com.ibm.wsspi.security.tai.TrustAssociationInterceptor;

public class Activator implements BundleActivator, ManagedService {
	private ServiceRegistration<ManagedService> configRef = null;
	private ServiceRegistration<UserRegistry> curRef = null;
	private ServiceRegistration<TrustAssociationInterceptor> taiRef = null;
	private static final String CFG_PID = "dominoUserRegistry";
	
	private final TrustAssociationInterceptor tai = new DominoTAI();
	
	Hashtable<String, Object> getDefaults() {
		Hashtable<String, Object> defaults = new Hashtable<>();
		defaults.put("service.pid", CFG_PID);
		return defaults;
	}
	Hashtable<String, Object> getTAIDefaults() {
		Hashtable<String, Object> defaults = new Hashtable<>();
		defaults.put("invokeBeforeSSO", true);
		defaults.put("service.pid", tai.getType());
		defaults.put("id", tai.getType());
		return defaults;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		this.configRef = context.registerService(ManagedService.class, this, getDefaults());
		this.curRef = context.registerService(UserRegistry.class, new DominoUserRegistry(), getDefaults());
		this.taiRef = context.registerService(TrustAssociationInterceptor.class, tai, getTAIDefaults());
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if(this.configRef != null) {
			this.configRef.unregister();
			this.configRef = null;
		}
		if(this.curRef != null) {
			this.curRef.unregister();
			this.curRef = null;
		}
		if(this.taiRef != null) {
			this.taiRef.unregister();
			this.taiRef = null;
		}
	}

	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
	}

}
