package org.openntf.openliberty.domino.notesruntime;

import java.io.InputStream;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;

public class NotesRuntimeExtension implements ExtensionDeployer {

	@Override
	public InputStream getEsaData() {
		return getClass().getResourceAsStream("/ext/notesRuntime-1.0.esa"); //$NON-NLS-1$
	}

}
