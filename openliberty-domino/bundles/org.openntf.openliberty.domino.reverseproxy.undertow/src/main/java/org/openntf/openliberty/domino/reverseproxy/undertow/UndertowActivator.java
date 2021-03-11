package org.openntf.openliberty.domino.reverseproxy.undertow;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.xnio.Xnio;
import org.xnio.XnioProvider;
import org.xnio.nio.NioXnioProvider;

public class UndertowActivator implements BundleActivator {

	private ServiceRegistration<Xnio> registrationS;
    private ServiceRegistration<XnioProvider> registrationP;

    @Override
    public void start(BundleContext context) throws Exception {
        XnioProvider provider = new NioXnioProvider();
        Xnio xnio = provider.getInstance();
        String name = xnio.getName();
        Hashtable<String, String> props = new Hashtable<>();
        props.put("name", name);
        registrationS = context.registerService(Xnio.class, xnio, props);
        registrationP = context.registerService(XnioProvider.class, provider, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registrationS.unregister();
        registrationP.unregister();
    }

}
