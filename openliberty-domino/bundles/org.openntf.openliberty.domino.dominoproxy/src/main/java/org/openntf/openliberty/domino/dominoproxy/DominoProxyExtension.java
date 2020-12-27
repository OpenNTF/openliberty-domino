package org.openntf.openliberty.domino.dominoproxy;

import java.io.InputStream;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;

public class DominoProxyExtension implements ExtensionDeployer {

	@Override
	public InputStream getEsaData() {
		return getClass().getResourceAsStream("/ext/dominoProxy-1.0.esa"); //$NON-NLS-1$
	}

}
