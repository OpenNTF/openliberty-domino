package org.openntf.openliberty.wlp.userregistry;

import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;

import com.ibm.websphere.security.CertificateMapFailedException;
import com.ibm.websphere.security.CertificateMapNotSupportedException;
import com.ibm.websphere.security.CustomRegistryException;
import com.ibm.websphere.security.EntryNotFoundException;
import com.ibm.websphere.security.NotImplementedException;
import com.ibm.websphere.security.PasswordCheckFailedException;
import com.ibm.websphere.security.Result;
import com.ibm.websphere.security.UserRegistry;
import com.ibm.websphere.security.cred.WSCredential;

/**
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 * @see <a href="https://www.ibm.com/support/knowledgecenter/SSAW57_9.0.0/com.ibm.websphere.nd.multiplatform.doc/ae/tsec_users.html?view=kc">Developing the UserRegistry interface for using custom registries</a>
 */
public class DominoUserRegistry implements UserRegistry {

	public DominoUserRegistry() {
		System.out.println(getClass().getSimpleName() + " construct");
	}

	@Override
	public void initialize(Properties props) throws CustomRegistryException, RemoteException {
		// Nothing to do here
		System.out.println(getClass().getSimpleName() + " initialize " + props);
	}
	
	@Override
	public String checkPassword(String userSecurityName, String password)
			throws PasswordCheckFailedException, CustomRegistryException, RemoteException {
		System.out.println("Asked to check password for " + userSecurityName);
		
		// return user DN
		return null;
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
		// TODO use the active domain name?
		return "Domino";
	}

	@Override
	public Result getUsers(String pattern, int limit) throws CustomRegistryException, RemoteException {
		// TODO search for users
		
		return null;
	}

	@Override
	public String getUserDisplayName(String userSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO return user DN
		return userSecurityName;
	}

	@Override
	public String getUniqueUserId(String userSecurityName) throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO return user ID
		return userSecurityName;
	}

	@Override
	public String getUserSecurityName(String uniqueUserId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// TODO return user DN
		return null;
	}

	@Override
	public boolean isValidUser(String userSecurityName) throws CustomRegistryException, RemoteException {
		// TODO Look up user
		System.out.println("Asked if it's a valid user: " + userSecurityName);
		return false;
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
		return null;
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
