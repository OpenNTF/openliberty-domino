package org.openntf.openliberty.domino.adminnsf.wlp;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.openntf.openliberty.domino.adminnsf.AbstractJavaServerDocumentHandler;
import org.openntf.openliberty.domino.adminnsf.AdminNSFService;
import org.openntf.openliberty.domino.adminnsf.Messages;
import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.jvm.RunningJVMJavaRuntimeProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.server.wlp.LibertyExtensionDeployer;
import org.openntf.openliberty.domino.server.wlp.LibertyServerConfiguration;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.openntf.openliberty.domino.util.xml.XMLDocument;
import org.openntf.openliberty.domino.util.xml.XMLNode;
import org.openntf.openliberty.domino.util.xml.XMLNodeList;
import org.xml.sax.SAXException;

import lotus.domino.Document;
import lotus.domino.EmbeddedObject;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;

public class LibertyServerDocumentHandler extends AbstractJavaServerDocumentHandler {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String FORM_LIBERTY = "Server"; //$NON-NLS-1$
	
	public static final String ITEM_SERVERENV = "ServerEnv"; //$NON-NLS-1$
	public static final String ITEM_SERVERXML = "ServerXML"; //$NON-NLS-1$
	public static final String ITEM_DEPLOYMENTZIPS = "DeploymentZIPs"; //$NON-NLS-1$
	public static final String ITEM_WAR = "WarFile"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_JVMOPTIONS = "JvmOptions"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_BOOTSTRAPPROPS = "BootstrapProperties"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_INTEGRATIONFEATURES = "IntegrationFeatures"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYVERSION = "LibertyVersion"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYARTIFACT = "LibertyArtifact"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYMAVENREPO = "LibertyMavenRepo"; //$NON-NLS-1$
	
	@Override
	public boolean canHandle(Document serverDoc) throws NotesException {
		return FORM_LIBERTY.equals(serverDoc.getItemValueString("Form")); //$NON-NLS-1$
	}

	@Override
	public void handle(Document serverDoc) throws NotesException {
		String serverName = serverDoc.getItemValueString(AdminNSFService.ITEM_SERVERNAME);
		XMLDocument serverXml = null;
		
		if(log.isLoggable(Level.INFO)) {
			log.info(format(Messages.getString("AdminNSFService.deployingDefinedServer"), getClass().getSimpleName(), serverName)); //$NON-NLS-1$
		}

		try {
			LibertyServerConfiguration config = new LibertyServerConfiguration();
			
			String javaVersion = serverDoc.getItemValueString(ITEM_JAVAVERSION);
			String javaType = serverDoc.getItemValueString(ITEM_JAVATYPE);
			if(StringUtil.isEmpty(javaType)) {
				javaType = RunningJVMJavaRuntimeProvider.TYPE_RUNNINGJVM;
			}
			config.setJavaVersion(new JVMIdentifier(javaVersion, javaType));
			
			config.setServerXml(generateServerXml(serverDoc));
			config.setServerEnv(serverDoc.getItemValueString(ITEM_SERVERENV));
			config.setJvmOptions(serverDoc.getItemValueString(ITEM_JVMOPTIONS));
			config.setBootstrapProperties(serverDoc.getItemValueString(ITEM_BOOTSTRAPPROPS));
			if(serverDoc.hasItem(ITEM_DEPLOYMENTZIPS)) {
				RichTextItem deploymentItem = (RichTextItem)serverDoc.getFirstItem(ITEM_DEPLOYMENTZIPS);
				@SuppressWarnings("unchecked")
				List<EmbeddedObject> objects = deploymentItem.getEmbeddedObjects();
				for(EmbeddedObject eo : objects) {
					if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
						Path zip = Files.createTempFile(OpenLibertyUtil.getTempDirectory(), "nsfdeployment", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
						Files.deleteIfExists(zip);
						eo.extractFile(zip.toString());
						config.addAdditionalZip(zip);
					}
				}
			}
			
			config.setLibertyVersion(serverDoc.getItemValueString(ITEM_LIBERTYVERSION));
			config.setLibertyArtifact(serverDoc.getItemValueString(ITEM_LIBERTYARTIFACT));
			config.setLibertyMavenRepo(serverDoc.getItemValueString(ITEM_LIBERTYMAVENREPO));
			
			
			OpenLibertyRuntime.instance.registerServer(serverName, config);
			OpenLibertyRuntime.instance.createServer(serverName);
			OpenLibertyRuntime.instance.startServer(serverName);
			
			// Deploy the attached WAR
			String contextPath = serverDoc.getItemValueString(AdminNSFService.ITEM_CONTEXTPATH);
			if(StringUtil.isEmpty(contextPath)) {
				contextPath = "/app"; //$NON-NLS-1$
			}
			if(!contextPath.startsWith("/")) { //$NON-NLS-1$
				contextPath = "/" + contextPath; //$NON-NLS-1$
			}
			
			// Generate a name based on the modification time, to avoid Windows file-locking trouble
			// Shave off the last digit, to avoid trouble with Domino TIMEDATE limitations
			long docMod = serverDoc.getLastModified().toJavaDate().getTime();
			docMod = docMod / 10 * 10;
			Path appsRoot = OpenLibertyUtil.getTempDirectory().resolve("apps"); //$NON-NLS-1$
			Path appDir = appsRoot.resolve(serverDoc.getNoteID() + '-' + docMod);
			Files.createDirectories(appDir);
			Path warPath = appDir.resolve("app.war"); //$NON-NLS-1$
			
	
			// See if we need to deploy the file
			if(!Files.exists(warPath)) {
				if(log.isLoggable(Level.INFO)) {
					log.info(format(Messages.getString("AdminNSFService.deployingDefinedApp"), getClass().getSimpleName(), serverName, contextPath)); //$NON-NLS-1$
				}
				
				if(serverDoc.hasItem(ITEM_WAR)) {
					Item warItem = serverDoc.getFirstItem(ITEM_WAR);
					if(warItem.getType() == Item.RICHTEXT) {
						RichTextItem rtItem = (RichTextItem)warItem;
						@SuppressWarnings("unchecked")
						Vector<EmbeddedObject> objects = rtItem.getEmbeddedObjects();
						try {
							for(EmbeddedObject eo : objects) {
								// Deploy all attached files
								if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
									eo.extractFile(warPath.toString());
									break;
								}
							}
						} finally {
							rtItem.recycle(objects);
						}
					}
					
					// Add a webApplication entry
					if(serverXml == null) {
						serverXml = generateServerXml(serverDoc);
					}
					XMLNode webApplication = serverXml.selectSingleNode("/server").addChildElement("webApplication"); //$NON-NLS-1$ //$NON-NLS-2$
					webApplication.setAttribute("contextRoot", contextPath); //$NON-NLS-1$
					webApplication.setAttribute("id", "app-" + serverDoc.getNoteID()); //$NON-NLS-1$ //$NON-NLS-2$
					webApplication.setAttribute("location", warPath.toString()); //$NON-NLS-1$
					webApplication.setAttribute("name", serverName); //$NON-NLS-1$
				}
			}
			
			if(serverXml != null) {
				// TODO create a full configuration
				LibertyServerConfiguration newConfig = new LibertyServerConfiguration();
				newConfig.setServerXml(serverXml);
				OpenLibertyRuntime.instance.updateConfiguration(serverName, newConfig);
			}
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private XMLDocument generateServerXml(Document serverDoc) throws NotesException, IOException {
		try {
			String serverXmlString = serverDoc.getItemValueString(ITEM_SERVERXML);
			XMLDocument serverXml = new XMLDocument(serverXmlString);
			
			@SuppressWarnings("unchecked")
			List<String> integrationFeatures = serverDoc.getItemValue(ITEM_INTEGRATIONFEATURES);
			integrationFeatures.remove(null);
			integrationFeatures.remove(""); //$NON-NLS-1$
			if(!integrationFeatures.isEmpty()) {
				List<LibertyExtensionDeployer> extensions = OpenLibertyUtil.findExtensions(LibertyExtensionDeployer.class).collect(Collectors.toList());
				XMLNode featuresElement = serverXml.selectSingleNode("/server/featureManager"); //$NON-NLS-1$
				if(featuresElement == null) {
					featuresElement = serverXml.getDocumentElement().addChildElement("featureManager"); //$NON-NLS-1$
				}
				
				for(String featureName : integrationFeatures) {
					// Map the feature to the right version from the current runtime
					String version = extensions.stream()
						.filter(ext -> featureName.equals(ext.getShortName()))
						.map(LibertyExtensionDeployer::getFeatureVersion)
						.findFirst()
						.orElse(""); //$NON-NLS-1$
					if(!version.isEmpty()) {
						String feature = format("usr:{0}-{1}", featureName, version); //$NON-NLS-1$
						XMLNodeList result = featuresElement.selectNodes(format("./feature[starts-with(text(), ''usr:{0}-'')]", featureName)); //$NON-NLS-1$
						if(result.isEmpty()) {
							featuresElement.addChildElement("feature").setTextContent(feature); //$NON-NLS-1$
						}
					}
				}
			}
			
			return serverXml;
		} catch(SAXException | ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

}
