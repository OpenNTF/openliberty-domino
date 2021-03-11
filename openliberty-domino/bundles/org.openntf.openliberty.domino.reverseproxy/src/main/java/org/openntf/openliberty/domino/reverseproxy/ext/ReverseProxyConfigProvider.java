package org.openntf.openliberty.domino.reverseproxy.ext;

import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;

/**
 * 
 * @author Jesse Gallagher
 * @since 2.1.0
 */
public interface ReverseProxyConfigProvider {
	ReverseProxyConfig createConfiguration(ReverseProxyService service);
}
