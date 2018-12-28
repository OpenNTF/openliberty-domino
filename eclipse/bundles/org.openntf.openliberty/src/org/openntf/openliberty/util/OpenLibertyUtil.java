package org.openntf.openliberty.util;

public enum OpenLibertyUtil {
	;
	
	public static final boolean IS_WINDOWS;
	static {
		String os = System.getProperty("os.name");
		IS_WINDOWS = os.toLowerCase().contains("windows");
	}
	
	/**
	 * Returns an appropriate temp directory for the system. On Windows, this is
	 * equivalent to <code>System.getProperty("java.io.tmpdir")</code>. On
	 * Linux, however, since this seems to return the data directory in some
	 * cases, it uses <code>/tmp</code>.
	 *
	 * @return an appropriate temp directory for the system
	 */
	public static String getTempDirectory() {
		if (!IS_WINDOWS) {
			return "/tmp"; //$NON-NLS-1$
		} else {
			return System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
		}
	}
}
