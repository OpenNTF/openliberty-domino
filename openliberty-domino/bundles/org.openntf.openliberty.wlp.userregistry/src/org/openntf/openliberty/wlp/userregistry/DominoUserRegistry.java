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

import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.wlp.userregistry.util.DominoThreadFactory;

import com.ibm.websphere.security.CertificateMapFailedException;
import com.ibm.websphere.security.CertificateMapNotSupportedException;
import com.ibm.websphere.security.CustomRegistryException;
import com.ibm.websphere.security.EntryNotFoundException;
import com.ibm.websphere.security.NotImplementedException;
import com.ibm.websphere.security.PasswordCheckFailedException;
import com.ibm.websphere.security.Result;
import com.ibm.websphere.security.UserRegistry;
import com.ibm.websphere.security.cred.WSCredential;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Name;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

/**
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 * @see <a href="https://www.ibm.com/support/knowledgecenter/SSAW57_9.0.0/com.ibm.websphere.nd.multiplatform.doc/ae/tsec_users.html?view=kc">Developing the UserRegistry interface for using custom registries</a>
 */
public class DominoUserRegistry implements UserRegistry {
	private static final Logger log = Logger.getLogger(DominoUserRegistry.class.getPackage().getName());
	static {
		log.setLevel(Level.FINER);
	}

	public DominoUserRegistry() {
		if(log.isLoggable(Level.FINER)) {
			log.finer(getClass().getSimpleName() + " construct");
		}
	}

	@Override
	public void initialize(Properties props) throws CustomRegistryException, RemoteException {
		// Nothing to do here
		if(log.isLoggable(Level.FINER)) {
			log.finer(getClass().getSimpleName() + " initialize " + props);
		}
	}
	
	@Override
	public String checkPassword(String userSecurityName, String password)
			throws PasswordCheckFailedException, CustomRegistryException, RemoteException {
		if(log.isLoggable(Level.FINE)) {
			log.fine(getClass().getSimpleName() + " checking password for user \"" + userSecurityName + "\"");
		}
		
		try {
			return DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database names = session.getDatabase("", "names.nsf");
					Document tempDoc = names.createDocument();
					tempDoc.replaceItemValue("Username", userSecurityName);
					tempDoc.replaceItemValue("Password", password);
					session.evaluate(" @SetField('HashPassword'; @NameLookup([NoCache]:[Exhaustive]; Username; 'HTTPPassword')[1]) ", tempDoc);
					// TODO look up against other password variants, or find real way to do this
					List<?> result = session.evaluate(" @VerifyPassword(Password; HashPassword) ", tempDoc);
					if(!result.isEmpty() && Double.valueOf(1).equals(result.get(0))) {
						// Then it's good! Look up the user's real name
						String fullName = (String)session.evaluate(" @NameLookup([NoCache]:[Exhaustive]; Username; 'FullName') ", tempDoc).get(0);
						if(log.isLoggable(Level.FINER)) {
							log.finer("Successfully logged in for user \"" + userSecurityName + "\"; fullName=\"" + fullName + "\"");
						}
						return fullName;
					} else {
						return null;
					}
				} catch(Throwable t) {
					t.printStackTrace();
					throw t;
				} finally {
					session.recycle();
				}
			}).get();
		} catch (Throwable e) {
			e.printStackTrace();
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String mapCertificate(X509Certificate[] cert) throws CertificateMapNotSupportedException,
			CertificateMapFailedException, CustomRegistryException, RemoteException {
		// Not yet implemented
		
		// return user DN
		return null;
	}

	@Override
	public String getRealm() throws CustomRegistryException, RemoteException {
		return "defaultRealm";
	}

	@Override
	public Result getUsers(String pattern, int limit) throws CustomRegistryException, RemoteException {
		// TODO search for users
		
		return null;
	}

	@Override
	public String getUserDisplayName(String userSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		if(log.isLoggable(Level.FINE)) {
			log.fine(getClass().getSimpleName() + " getting display name user \"" + userSecurityName + "\"");
		}
		
		try {
			return DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database names = session.getDatabase("", "names.nsf");
					Document tempDoc = names.createDocument();
					tempDoc.replaceItemValue("Username", userSecurityName);
					List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'FullName')) ", tempDoc);
					if(!result.isEmpty()) {
						Name name = session.createName((String)result.get(0));
						return name.getCommon();
					} else {
						return null;
					}
				} finally {
					session.recycle();
				}
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getUniqueUserId(String userSecurityName) throws EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			return DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database names = session.getDatabase("", "names.nsf");
					Document tempDoc = names.createDocument();
					tempDoc.replaceItemValue("Username", userSecurityName);
					List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'ShortName')) ", tempDoc);
					if(!result.isEmpty()) {
						return (String)result.get(0);
					} else {
						return null;
					}
				} finally {
					session.recycle();
				}
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getUserSecurityName(String uniqueUserId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			return DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database names = session.getDatabase("", "names.nsf");
					Document tempDoc = names.createDocument();
					tempDoc.replaceItemValue("Username", uniqueUserId);
					List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'FullName')) ", tempDoc);
					if(!result.isEmpty()) {
						return (String)result.get(0);
					} else {
						return null;
					}
				} finally {
					session.recycle();
				}
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public boolean isValidUser(String userSecurityName) throws CustomRegistryException, RemoteException {
		String cn;
		try {
			cn = getUserSecurityName(userSecurityName);
		} catch (EntryNotFoundException e) {
			throw new CustomRegistryException(e);
		}
		return cn != null && !cn.isEmpty();
	}

	@Override
	public Result getGroups(String pattern, int limit) throws CustomRegistryException, RemoteException {
		// TODO Look up groups
		return null;
	}

	@Override
	public String getGroupDisplayName(String groupSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO return CN?
		return groupSecurityName;
	}

	@Override
	public String getUniqueGroupId(String groupSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// No such distinction
		return groupSecurityName;
	}

	@Override
	public List<String> getUniqueGroupIds(String uniqueUserId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO Look up groups
		return Collections.emptyList();
	}

	@Override
	public String getGroupSecurityName(String uniqueGroupId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// No such distinction
		return uniqueGroupId;
	}

	@Override
	public boolean isValidGroup(String groupSecurityName) throws CustomRegistryException, RemoteException {
		// TODO Look up group
		return false;
	}

	@Override
	public List<String> getGroupsForUser(String userSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		return getUniqueGroupIds(userSecurityName);
	}

	@Override
	public Result getUsersForGroup(String groupSecurityName, int limit)
			throws NotImplementedException, EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO Look up and expand group
		return null;
	}

	@Override
	public WSCredential createCredential(String userSecurityName)
			throws NotImplementedException, EntryNotFoundException, CustomRegistryException, RemoteException {
		// This is not yet implemented on the WLP side
		return null;
	}

}
