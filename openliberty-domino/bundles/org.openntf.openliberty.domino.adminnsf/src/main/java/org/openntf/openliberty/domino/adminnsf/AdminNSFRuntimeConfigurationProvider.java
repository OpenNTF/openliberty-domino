package org.openntf.openliberty.domino.adminnsf;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openntf.openliberty.domino.adminnsf.config.AdminNSFProperties;
import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.jvm.AdoptOpenJDKJavaRuntimeProvider;
import org.openntf.openliberty.domino.jvm.RunningJVMJavaRuntimeProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

public class AdminNSFRuntimeConfigurationProvider implements RuntimeConfigurationProvider {
	
	public static final String ITEM_BASEDIRECTORY = "BaseDirectory"; //$NON-NLS-1$
	public static final String ITEM_VERSION = "Version"; //$NON-NLS-1$
	public static final String ITEM_ARTIFACT = "Artfiact"; //$NON-NLS-1$
	public static final String ITEM_MAVENREPO = "MavenRepo"; //$NON-NLS-1$

	public static final String ITEM_JAVAVERSION = "JavaVersion"; //$NON-NLS-1$
	public static final String ITEM_JAVAJVM = "JavaJVM"; //$NON-NLS-1$
	
	private Path baseDirectory;
	private String artifact;
	private String version;
	private String mavenRepo;
	private String javaVersion;
	private String javaType;

	@Override
	public Path getBaseDirectory() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.baseDirectory;
	}

	@Override
	public String getOpenLibertyVersion() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.version;
	}

	@Override
	public String getOpenLibertyArtifact() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.artifact;
	}

	@Override
	public String getOpenLibertyMavenRepository() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.mavenRepo;
	}
	
	@Override
	public String getJavaVersion() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.javaVersion;
	}
	
	@Override
	public String getJavaType() {
		if(this.baseDirectory == null) {
			loadData();
		}
		return this.javaType;
	}

	private synchronized void loadData() {
		try {
			DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
					Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
					String execDirName = config.getItemValueString(ITEM_BASEDIRECTORY);
					Path execDir;
					if(StringUtil.isEmpty(execDirName)) {
						execDir = Paths.get(OpenLibertyUtil.getDominoProgramDirectory()).resolve("wlp"); //$NON-NLS-1$
					} else {
						execDir = Paths.get(execDirName);
					}
					this.baseDirectory = execDir;
					
					String version = config.getItemValueString(ITEM_VERSION);
					if(StringUtil.isEmpty(version)) {
						version = AdminNSFProperties.instance.getDefaultVersion();
					}
					this.version = version;
					
					String mavenRepo = config.getItemValueString(ITEM_MAVENREPO);
					if(StringUtil.isEmpty(mavenRepo)) {
						mavenRepo = AdminNSFProperties.instance.getDefaultMavenRepo();
					}
					this.mavenRepo = mavenRepo;
					
					String artifact = config.getItemValueString(ITEM_ARTIFACT);
					if(StringUtil.isEmpty(artifact)) {
						artifact = AdminNSFProperties.instance.getDefaultArtifact();
					}
					this.artifact = artifact;
					
					this.javaVersion = config.getItemValueString(ITEM_JAVAVERSION);
					if("Domino".equals(this.javaVersion)) {
						this.javaType = RunningJVMJavaRuntimeProvider.TYPE_RUNNINGJVM;
					} else {
						if(StringUtil.isEmpty(this.javaVersion)) {
							this.javaVersion = "11";
						}
						this.javaType = config.getItemValueString(ITEM_JAVAJVM);
						if(StringUtil.isEmpty(this.javaType)) {
							this.javaType = AdoptOpenJDKJavaRuntimeProvider.TYPE_HOTSPOT;
						}
					}
				} finally {
					session.recycle();
				}
				
				return null;
			}).get();
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
