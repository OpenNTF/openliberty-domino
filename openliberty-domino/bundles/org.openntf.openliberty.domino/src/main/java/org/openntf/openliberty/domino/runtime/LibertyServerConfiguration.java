package org.openntf.openliberty.domino.runtime;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.util.xml.XMLDocument;

/**
 * Represents the configuration of a deployable Liberty server instance.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class LibertyServerConfiguration {
	private JVMIdentifier javaVersion;
	
	private XMLDocument serverXml;
	private String serverEnv;
	private String jvmOptions;
	private String bootstrapProperties;
	private Collection<Path> additionalZips = new HashSet<>();
	
	public JVMIdentifier getJavaVersion() {
		return javaVersion;
	}
	public void setJavaVersion(JVMIdentifier javaVersion) {
		this.javaVersion = javaVersion;
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
}
