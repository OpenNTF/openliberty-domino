# Developer Readme

The Domino Open Liberty Runtime is made up of several major components:

## Domino Runtime

The Domino runtime consists of most of the modules in the "bundles" directory, which are by default packaged as OSGi bundles. However, since the runtime can also be packaged as a RunJava extension, there are several rules the code attempts to follow:

* Use ServiceLoader extensions for cross-module capabilities. To this end, most bundles are fragments attached to the `org.openntf.openliberty.domino` host so that their services will be available to the runtime in the same way regardless of context.
* Avoid any assumptions about the presence of OSGi when at all possible. For example, only `org.openntf.openliberty.domino.httpservice` (the HTTP-task-bound runner) and `org.openntf.openliberty.domino.httpident` (the HTTP-based identity servlet) have any knowledge of OSGi or the Equinox servlet environment.
* Limit dependencies and repackage necessary ones internally, as with Apache Commons Compress and portions of IBM Commons. Since RunJava deployment requires putting the packaged JAR in the global classpath, it's critical to avoid polluting it with dependencies that could reasonably show up at the OSGi or agent level.

### Runtime Extensions

The runtime has several extension points available, some of which are intended to represent a single provider of a vital capability and some of which are intended for multi-service extension.

#### Required Services

* `org.openntf.openliberty.config.RuntimeConfigurationProvider` is used to load global configuration for the runtime. The implementation for this is found in `org.openntf.openliberty.domino.adminnsf`, which reads the configuration from libertyadmin.nsf.
* `org.openntf.openliberty.domino.runtime.RuntimeDeploymentTask` is used to find the location of the active Open Liberty installation. The implementation for this is found in the core module, which reads Liberty coordinates from the `RuntimeConfigurationProvider`, downloads it from Maven Central, and provides it to the runtime.
* `org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfigProvider` is used by the Reverse Proxy service to find the configuration for the proxy. This is also provided in `org.openntf.openliberty.domino.adminnsf`, which reads the Domino server configuration from names.nsf and reverse-proxy config from libertyadmin.nsf.
* `org.openntf.openliberty.domino.config.RuntimeAccessProvider` is used by services to check whether a given user has permission to perform a task. This is provided in `org.openntf.openliberty.domino.adminnsf`, which reads roles from the ACL of the libertyadmin.nsf database.
* `org.openntf.openliberty.domino.runtime.AppDeploymentProvider` is used to deploy new and updated apps to the central configuration. This is also provided in `org.openntf.openliberty.domino.adminnsf`, which updates the server and app documents in libertyadmin.nsf.

#### Extension Services

* `org.openntf.openliberty.domino.jvm.JavaRuntimeProvider` is used to find the location of a Java runtime for a given version and type (such as "HotSpot"). Standard implementations of this, which provide the currently-running JVM and AdoptOpenJDK builds as options, are found in the core module.
* `org.openntf.openliberty.domino.ext.RuntimeService` allows for an extended `Runnable` to be launched when the core starts, and to receive notifications about server lifecycles and other events.
* `org.openntf.openliberty.domino.ext.ExtensionDeployer` allows for the deployment of ESA-based Liberty extensions into the runtime. It also provides the information needed by the "Integration Features" checkboxes in the admin NSF to auto-register in the deployed server.xml

### Event Queue

The `OpenLibertyRuntime` class has a small event-broker capability, where `org.openntf.openliberty.domino.event.EventRecipient` instances can register themselves to be notified of various events during runtime. These events are subclasses of `java.util.EventObject`, and so implementations should check for specific subclass instances to determine the nature of incoming events.

## Liberty Extensions

In addition to the core runtime, there are several Open Liberty extensions that may be deployed with the Liberty instances. These are denoted by "org.openntf.openliberty.wlp" prefixes in the "bundles" directory, and also make up all the modules in the "subsystems" directory. The Liberty extensions each have corresponding Domino bundles to deploy them. For example, `bundles/org.openntf.openliberty.wlp.notesruntime` is packaged into a Liberty subsystem by `subsystems/notesRuntime`, which in turn is provided to Domino by `bundles/org.openntf.openliberty.domino.notesruntime`.