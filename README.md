# Domino Open Liberty Runtime

Inspired by [Sven Hasselbach's blog posts](http://hasselba.ch/blog/?p=2625), this project wraps [Open Liberty](https://openliberty.io), the open-source version of WebSphere Liberty, to run alongside the Domino HTTP task using the Domino JVM.

### What It Does

Like the original blog posts, this provides access to Domino classes and the surrounding Domino server environment. At least in initial testing, unlike the blog posts, accessing databases on the server doesn't cause a panic, allowing this to run alongside normal Domino server operations.

### What It Doesn't Do

This project does not enhance the Domino HTTP stack in any way. The normal HTTP task continues as normal, with its same abilities and limitations.

Additionally, this doesn't give the Liberty server any particular knowledge of how Domino normally works - it won't serve resources from NSFs on its own, nor does it automatically have access to the XPages OSGi framework.

## Requirements

- Domino 9.0.1 FP10 or newer

## Usage

To set up the Open Liberty runtime, install the contents of the `UpdateSite` directory on your Domino server, either via an Update Site NSF or in the data directory, and then create a new database named `libertyadmin.nsf` in the root of your server using the provided NTF.

After it is installed, open the admin NSF and add at least one server document. When HTTP is (re-)started on the server, servers configured here will be automatically deployed and launched. Additionally, if you create a "Dropin App" response document, you can attach .war files that will be automatically deposited in the "dropins" folder in the server. These applications can also be manually deployed there or added in the server.xml, as with a normal Open Liberty runtime.

### Console Commands

The runtime supports several Domino console commands, all of which are prefixed by `tell http osgi wlp`:

* `status`: Displays the status of all running Liberty servers. This is equivalent to the `server status $name` command in the Liberty package
* `stop`: Stops all running Liberty servers
* `start`: Starts all configured Liberty servers
* `restart`: Equivalent to `stop` followed by `start`

### Domino Proxy Application

The distribution also comes with a proxy application, `openliberty-domino-proxy.war`, that can be used to cause any unmatched requests to the Liberty server to proxy to the equivalent URL on Domino, allowing it to serve as the main front-end HTTP server.

To use this proxy, deploy the .war to the Liberty server, such as via a Dropin App response document. Then, configure the "server.env" field of the server configuration document to include configuration properties:

```
DominoProxyServlet.targetUri=http://localhost:8080/
DominoProxyServlet.sendWsis=false
```

The `DominoProxyServlet.targetUri` property should point to your configured Domino HTTP stack. In this case, Domino can be configured to bind to HTTP on "localhost" only for security purposes.

The `DominoProxyServlet.sendWsis` property tells the proxy whether or not to send the connector header to Domino that indicates whether or not the incoming connection is SSL. It's often useful to leave this as the default of "true", but it may be necessary in some cases to set it to "false" to work around the Domino HTTP stack's lack of knowledge of multiple SSL-enabled web sites.

Finally, to enable advanced proxy features, set `HTTPEnableConnectorHeaders=1` in your Domino server's notes.ini. This property allows Domino to treat proxied requests as if they were coming from the original client machine instead of the local proxy. If you enable this, it is *very important* that you ensure that the Domino server's HTTP stack is not publicly available, and ideally is bound to "localhost" only.

## Building

Building this project requires the presence of a p2 update site of the Domino XPages runtime. A version of this site matching Domino 9.0.1 is [available from OpenNTF](https://extlib.openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management/summary), and an updated version can be created from a Notes or Domino installation using the [generate-domino-update-site](https://stash.openntf.org/projects/P2T/repos/generate-domino-update-site/browse) tool. Put the file:// URL for this update site in the `notes-platform` property of your Maven configuration.

The Open Liberty projects rely on several additional plugins, which Maven finds via dependencies. When working with Eclipse, however, you will have to add them to your Target Platform. They can either be found within an existing Open Liberty installation, in the `dev/api/ibm` directory, or by running a Maven `package` goal on the included `osgi-deps` project, which will create a p2 site within its `target/repository` directory.

Additionally, to compile the Admin NSF via Maven and generate the final distribution, you will need to set up and configure a compilation server using the [NSF ODP Tooling project](https://github.com/OpenNTF/org.openntf.nsfodp).

## License

The code in the project is licensed under the Apache License 2.0. The runtime may download software licensed under compatible licenses - see NOTICE for details.