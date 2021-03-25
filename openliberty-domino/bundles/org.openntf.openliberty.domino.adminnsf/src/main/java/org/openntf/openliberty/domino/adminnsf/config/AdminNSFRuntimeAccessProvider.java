package org.openntf.openliberty.domino.adminnsf.config;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.config.RuntimeAccessProvider;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

/**
 * Implementation of {@link RuntimeAccessProvider} that grants access based on roles
 * in the Admin NSF ACL.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class AdminNSFRuntimeAccessProvider implements RuntimeAccessProvider {
	public static final String ROLE_DEPLOYAPP = "[DeployApp]"; //$NON-NLS-1$

	@Override
	public boolean canDeployApps(String userName) {
		try {
			Session session = NotesFactory.createSession();
			try {
				
				Database database = AdminNSFUtil.getAdminDatabase(session);
				return database.queryAccessRoles(userName).contains(ROLE_DEPLOYAPP);		
			} finally {
				session.recycle();
			}
		} catch(NotesException e) {
			throw new RuntimeException(e);
		}
	}

}
