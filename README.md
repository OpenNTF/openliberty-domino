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

## Reverse Proxy

The installation contains a reverse proxy that can be enabled in the main configuration document of `libertyadmin.nsf`. In there, you can specify ports to listen on as well as a TLS private key and certificate chain, if desired. By default, the reverse proxy will relay all requests to the Domino server, while individual WAR apps deployed to Liberty servers can also be included via their documents. When they are marked as such, their context roots will be forward to them first, rather than to Domino.

## Admin REST API

When installed on Domino, the runtime provides an Admin API at `/org.openntf.openliberty.domino/admin`. The available resources are described in "adminapi.yaml".

## Liberty Server Extensions

Deployed Liberty servers are installed with several custom features, which can be enabled per-server in the server configuration document in the NSF.

### Notes Runtime

The `notesRuntime-1.0` feature handles initialization and termination of the Notes runtime for the Liberty process, allowing individual web apps to skip this step and not compete.  This feature sets the Java property `notesruntime.init` to `"true"` when enabled, so  apps can check for that and skip process initialization.

### Domino User Registry

This feature allows the use of Domino credentials for Liberty authentication, when applicable. It proxies authentication requests through to the backing Domino server specified by `Domino_HTTP`, and so it should allow any authentication that is configured on the Domino server. By default, `Domino_HTTP` is configured to be the local server, but it can be overridden in server.env.

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