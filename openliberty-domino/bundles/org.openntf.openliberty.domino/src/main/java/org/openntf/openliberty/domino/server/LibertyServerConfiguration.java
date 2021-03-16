/*
 * Copyright © 2018-2021 Jesse Gallagher
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
package org.openntf.openliberty.domino.server;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.openntf.openliberty.domino.util.xml.XMLDocument;

/**
 * Represents the configuration of a deployable Liberty server instance.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class LibertyServerConfiguration extends AbstractJavaServerConfiguration {
	
	private String libertyVersion;
	private String libertyArtifact;
	private String libertyMavenRepo;
	
	private XMLDocument serverXml;
	private String serverEnv;
	private String jvmOptions;
	private String bootstrapProperties;
	private Collection<Path> additionalZips = new HashSet<>();
	
	public String getLibertyVersion() {
		return libertyVersion;
	}
	public void setLibertyVersion(String libertyVersion) {
		this.libertyVersion = libertyVersion;
	}
	public String getLibertyArtifact() {
		return libertyArtifact;
	}
	public void setLibertyArtifact(String libertyArtifact) {
		this.libertyArtifact = libertyArtifact;
	}
	public String getLibertyMavenRepo() {
		return libertyMavenRepo;
	}
	public void setLibertyMavenRepo(String libertyMavenRepo) {
		this.libertyMavenRepo = libertyMavenRepo;
	}
	
	public XMLDocument getServerXml() {
		return serverXml;
	}
	public void setServerXml(XMLDocument serverXml) {
		this.serverXml = serverXml;
	}
	
	public String getServerEnv() {
		return serverEnv;
	}
	public void setServerEnv(String serverEnv) {
		this.serverEnv = serverEnv;
	}
	
	public String getJvmOptions() {
		return jvmOptions;
	}
	public void setJvmOptions(String jvmOptions) {
		this.jvmOptions = jvmOptions;
	}
	
	public String getBootstrapProperties() {
		return bootstrapProperties;
	}
	public void setBootstrapProperties(String bootstrapProperties) {
		this.bootstrapProperties = bootstrapProperties;
	}
	
	public void addAdditionalZip(Path zip) {
		this.additionalZips.add(zip);
	}
	public Collection<Path> getAdditionalZips() {
		return Collections.unmodifiableCollection(additionalZips);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((additionalZips == null) ? 0 : additionalZips.hashCode());
		result = prime * result + ((bootstrapProperties == null) ? 0 : bootstrapProperties.hashCode());
		result = prime * result + ((jvmOptions == null) ? 0 : jvmOptions.hashCode());
		result = prime * result + ((libertyArtifact == null) ? 0 : libertyArtifact.hashCode());
		result = prime * result + ((libertyMavenRepo == null) ? 0 : libertyMavenRepo.hashCode());
		result = prime * result + ((libertyVersion == null) ? 0 : libertyVersion.hashCode());
		result = prime * result + ((serverEnv == null) ? 0 : serverEnv.hashCode());
		result = prime * result + ((serverXml == null) ? 0 : serverXml.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LibertyServerConfiguration other = (LibertyServerConfiguration) obj;
		if (additionalZips == null) {
			if (other.additionalZips != null)
				return false;
		} else if (!additionalZips.equals(other.additionalZips))
			return false;
		if (bootstrapProperties == null) {
			if (other.bootstrapProperties != null)
				return false;
		} else if (!bootstrapProperties.equals(other.bootstrapProperties))
			return false;
		if (jvmOptions == null) {
			if (other.jvmOptions != null)
				return false;
		} else if (!jvmOptions.equals(other.jvmOptions))
			return false;
		if (libertyArtifact == null) {
			if (other.libertyArtifact != null)
				return false;
		} else if (!libertyArtifact.equals(other.libertyArtifact))
			return false;
		if (libertyMavenRepo == null) {
			if (other.libertyMavenRepo != null)
				return false;
		} else if (!libertyMavenRepo.equals(other.libertyMavenRepo))
			return false;
		if (libertyVersion == null) {
			if (other.libertyVersion != null)
				return false;
		} else if (!libertyVersion.equals(other.libertyVersion))
			return false;
		if (serverEnv == null) {
			if (other.serverEnv != null)
				return false;
		} else if (!serverEnv.equals(other.serverEnv))
			return false;
		if (serverXml == null) {
			if (other.serverXml != null)
				return false;
		} else if (!serverXml.equals(other.serverXml))
			return false;
		return true;
	}
}
