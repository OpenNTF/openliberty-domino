package org.openntf.openliberty.domino.adminnsf;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

public class AdminNSFRuntimeConfigurationProvider implements RuntimeConfigurationProvider {
	
	public static final String ITEM_BASEDIRECTORY = "BaseDirectory"; //$NON-NLS-1$
	
	private Path baseDirectory;

	@Override
	public Path getBaseDirectory() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.baseDirectory;
	}

	private synchronized void loadData() {
		try {
			DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
					Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
					String execDirName = config.getItemValueString(ITEM_BASEDIRECTORY);
					Path execDir;
					if(StringUtil.isEmpty(execDirName)) {
						execDir = Paths.get(OpenLibertyUtil.getDominoProgramDirectory()).resolve("wlp"); //$NON-NLS-1$
					} else {
						execDir = Paths.get(execDirName);
					}
					this.baseDirectory = execDir;
				} finally {
					session.recycle();
				}
				
				return null;
			}).get();
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
