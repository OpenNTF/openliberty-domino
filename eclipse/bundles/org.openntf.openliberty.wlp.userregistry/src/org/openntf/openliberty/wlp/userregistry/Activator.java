package org.openntf.openliberty.wlp.userregistry;

import java.util.Dictionary;
import java.util.Hashtable;

import org.openntf.openliberty.wlp.userregistry.util.DominoThreadFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import com.ibm.websphere.security.UserRegistry;

public class Activator implements BundleActivator, ManagedService {
	private ServiceRegistration<ManagedService> configRef = null;
	private ServiceRegistration<UserRegistry> curRef = null;
	private static final String CFG_PID = "dominoUserRegistry";
	private final UserRegistry registry = new DominoUserRegistry();
	
	Hashtable<String, ?> getDefaults() {
		Hashtable<String, Object> defaults = new Hashtable<>();
		defaults.put("service.pid", CFG_PID);
		return defaults;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		DominoThreadFactory.init();
		
		this.configRef = context.registerService(ManagedService.class, this, getDefaults());
		this.curRef = context.registerService(UserRegistry.class, registry, getDefaults());
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
		
		DominoThreadFactory.term();
	}

	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
	}

}
