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
