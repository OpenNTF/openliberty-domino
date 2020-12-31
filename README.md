# Domino Open Liberty Runtime

Inspired by [Sven Hasselbach's blog posts](http://hasselba.ch/blog/?p=2625), this project wraps [Open Liberty](https://openliberty.io), the open-source version of WebSphere Liberty, to run alongside the Domino HTTP task.

### What It Does

Like the original blog posts, this provides access to Domino classes and the surrounding Domino server environment. At least in initial testing, unlike the blog posts, accessing databases on the server doesn't cause a panic, allowing this to run alongside normal Domino server operations.

### What It Doesn't Do

This project does not enhance the Domino HTTP stack in any way. The traditional HTTP task continues as normal, with its same abilities and limitations.

Additionally, this doesn't give the Liberty server any particular knowledge of how Domino normally works - it won't serve resources from NSFs on its own, nor does it automatically have access to the XPages OSGi framework.

## Requirements

- Domino 9.0.1 FP10 or newer

## Installation

The Open Liberty runtime can be installed either as a set of OSGi plugins running with Domino's HTTP runtime or using the RunJava command. In both cases, create a new database named `libertyadmin.nsf` in the root of your server using the provided NTF before loading the runtime.

### OSGi Deployment

To install via OSGi, install the contents of the `UpdateSite` directory on your Domino server, either via an Update Site NSF or in the data directory.

### RunJava Deployment

To install via RunJava, copy the JAR file from the `RunJava` directory into either the `jvm/lib/ext` directory or the `ndext` directory in your Domino installation. The runtime can be loaded by running `load runjava WLP` on the console or at startup adding `runjava WLP` to the `ServerTasks` notes.ini property. In these cases, "WLP" is case-sensitive.

## Usage

After it is installed, open the admin NSF and add at least one server document. When HTTP is (re-)started on the server, servers configured here will be automatically deployed and launched. Additionally, if you create a "Dropin App" response document, you can attach .war files that will be automatically deposited in the "dropins" folder in the server. These applications can also be manually deployed there or added in the server.xml, as with a normal Open Liberty runtime.

### Console Commands

The runtime supports several Domino console commands, all of which are prefixed by `tell wlp`:

* `status`: Displays the status of all running Liberty servers. This is equivalent to the `server status $name` command in the Liberty package
* `stop`: Stops all running Liberty servers
* `start`: Starts all configured Liberty servers
* `restart`: Equivalent to `stop` followed by `start`
* `help`: Get a listing of currently-supported options

## Liberty Server Extensions

Deployed Liberty servers are installed with several custom features, which can be enabled per-server in the server configuration document in the NSF.

### Notes Runtime

The `notesRuntime-1.0` feature handles initialization and termination of the Notes runtime for the Liberty process, allowing individual web apps to skip this step and not compete.  This feature sets the Java property `notesruntime.init` to `"true"` when enabled, so  apps can check for that and skip process initialization.

### Domino Proxy

bootstrap.properties or server.env:

```properties
Domino_HTTP=http://localhost:8080/
DominoProxyServlet.sendWsis=false
```

This feature can be used to cause any unmatched requests to the Liberty server to proxy to the equivalent URL on Domino, allowing it to serve as the main front-end HTTP server.

The  property should point to your configured Domino HTTP stack. In this case, Domino can be configured to bind to HTTP on "localhost" only for security purposes.  If the proxy target should be different from `dominoUserRegistry` below, you can specify `DominoProxyServlet.targetUri` as the target instead and it will take priority.

The `DominoProxyServlet.sendWsis` property tells the proxy whether or not to send the connector header to Domino that indicates whether or not the incoming connection is SSL. It's often useful to leave this as the default of "true", but it may be necessary in some cases to set it to "false" to work around the Domino HTTP stack's lack of knowledge of multiple SSL-enabled web sites.

Finally, to enable advanced proxy features, set `HTTPEnableConnectorHeaders=1` in your Domino server's notes.ini. This property allows Domino to treat proxied requests as if they were coming from the original client machine instead of the local proxy. If you enable this, it is *very important* that you ensure that the Domino server's HTTP stack is not publicly available, and ideally is bound to "localhost" only.

### Domino User Registry

server.env:

```properties
Domino_HTTP=http://localhost:8080/
```

This feature allows the use of Domino credentials for Liberty authentication, when applicable. It proxies authentication requests through to the backing Domino server specified by `Domino_HTTP`, and so it should allow any authentication that is configured on the Domino server.

Additionally, it allows for a shared login by proxying cookies containing Domino authentication information to the backing Domino server to determine the username.

This uses a servlet on the Domino side that responds to local requests only by default. To allow this service to respond to non-local requests, set the notes.ini property `WLP_IdentityServlet_LocalOnly` to `0`.

## Domino API Access

Code that uses the Notes runtime should take care to terminate all Notes-initialized threads, as leaving threads open may lead to server crashes. In practice, these steps have helped avoid trouble:

- Ensure that any `ExecutorService` that contains Notes threads is shut down properly in a `ServletContextListener`
- Run any Notes-based code in infrastructure listeners (such as `ServletContextListener`s) inside explicit `NotesThread`s and use `Thread#join` to wait for their results

## Building

Building this project requires the presence of a p2 update site of the Domino XPages runtime. A version of this site matching Domino 9.0.1 is [available from OpenNTF](https://extlib.openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management/summary), and an updated version can be created from a Notes or Domino installation using the [generate-domino-update-site](https://stash.openntf.org/projects/P2T/repos/generate-domino-update-site/browse) tool. Put the file:// URL for this update site in the `notes-platform` property of your Maven configuration.

Additionally, to compile the Admin NSF via Maven and generate the final distribution, you will need to set up and configure a compilation server or local runtime using the [NSF ODP Tooling project](https://github.com/OpenNTF/org.openntf.nsfodp).

## License

The code in the project is licensed under the Apache License 2.0. The runtime may download software licensed under compatible licenses - see NOTICE for details.