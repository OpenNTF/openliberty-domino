package org.openntf.openliberty.domino.userregistry;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;
import org.openntf.openliberty.domino.userregistry.config.UserRegistryProperties;

public class UserRegistryExtension implements ExtensionDeployer {

	@Override
	public List<InputStream> getBundleData() {
		return Arrays.asList(
			getClass().getResourceAsStream("/ext/" + getBundleFileName())
		);
	}

	@Override
	public List<String> getBundleFileNames() {
		return Arrays.asList(getBundleFileName());
	}

	@Override
	public String getFeatureId() {
		return "dominoUserRegistry-1.0";
	}
	
	@Override
	public String getSubsystemContent() {
		return UserRegistryProperties.instance.getPluginName() + ";version=" + UserRegistryProperties.instance.getUnqualifiedVersion() + '.' + UserRegistryProperties.instance.getBuildQualifier();
	}
	
	@Override
	public String getSubsystemVersion() {
		return UserRegistryProperties.instance.getUnqualifiedVersion() + '.' + UserRegistryProperties.instance.getBuildQualifier();
	}

	private String getBundleFileName() {
		String name = UserRegistryProperties.instance.getPluginName();
		String version = UserRegistryProperties.instance.getUnqualifiedVersion();
		String qualifier = UserRegistryProperties.instance.getBuildQualifier();
		return name + '_' + version + '.' + qualifier + ".jar";
	}
}
