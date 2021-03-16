package org.openntf.openliberty.domino.server;

import static java.text.MessageFormat.format;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.jvm.JavaRuntimeProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

public abstract class AbstractJavaServerInstance<T extends AbstractJavaServerConfiguration> implements ServerInstance<T> {
	private static final Logger log = OpenLibertyLog.getLog();
	private static final Map<JVMIdentifier, Path> javaHomes = Collections.synchronizedMap(new HashMap<>());
	
	public Path getJavaHome() {
		return javaHomes.computeIfAbsent(getConfiguration().getJavaVersion(), javaIdentifier -> {
			JavaRuntimeProvider javaRuntimeProvider = OpenLibertyUtil.findExtensions(JavaRuntimeProvider.class)
				.filter(p -> p.canProvide(javaIdentifier))
				.sorted(Comparator.comparing(JavaRuntimeProvider::getPriority).reversed())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(format(Messages.getString("OpenLibertyRuntime.unableToFindJVMFor"), javaIdentifier))); //$NON-NLS-1$
			Path javaHome = javaRuntimeProvider.getJavaHome(javaIdentifier);
			if(log.isLoggable(Level.INFO)) {
				log.info(format(Messages.getString("OpenLibertyRuntime.usingJavaRuntimeAt"), javaHome)); //$NON-NLS-1$
			}
			return javaHome;
		});
	}
	
	@Override
	public abstract T getConfiguration();
}
