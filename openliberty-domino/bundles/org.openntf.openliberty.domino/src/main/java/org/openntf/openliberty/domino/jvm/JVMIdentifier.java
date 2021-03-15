package org.openntf.openliberty.domino.jvm;

import java.text.MessageFormat;

public class JVMIdentifier {
	private final String version;
	private final String type;
	public JVMIdentifier(String version, String type) {
		this.version = version;
		this.type = type;
	}
	public String getVersion() {
		return version;
	}
	public String getType() {
		return type;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("JVM version={0}, type={1}", version, type);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		JVMIdentifier other = (JVMIdentifier) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}
}