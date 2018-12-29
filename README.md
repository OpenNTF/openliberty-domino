# Domino Open Liberty Runtime

Inspired by [Sven Hasselbach's blog posts](http://hasselba.ch/blog/?p=2625), this project wraps [Open Liberty](https://openliberty.io), the open-source version of WebSphere Liberty, to run alongside the Domino HTTP task using the Domino JVM.

### What It Does

Like the original blog posts, this provides access to Domino classes and the surrounding Domino server environment. At least in initial testing, unlike the blog posts, accessing databases on the server doesn't cause a panic, allowing this to run alongside normal Domino server operations.

### What It Doesn't Do

This project does not enhance the Domino HTTP stack in any way. The normal HTTP task continues as normal, with its same abilities and limitations.

Additionally, this doesn't give the Liberty server any particular knowledge of how Domino normally works - it won't serve resources from NSFs on its own, nor does it automatically have access to the XPages OSGi framework.

## Requirements

- Domino 9.0.1 FP10 or newer

## Building

Building this project requires the presence of a p2 update site of the Domino XPages runtime. A version of this site matching Domino 9.0.1 is [available from OpenNTF](https://extlib.openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management/summary), and an updated version can be created from a Notes or Domino installation using the [generate-domino-update-site](https://stash.openntf.org/projects/P2T/repos/generate-domino-update-site/browse) tool. Put the file:// URL for this update site in the `notes-platform` property of your Maven configuration.

The Open Liberty projects rely on several additional plugins, which Maven finds via dependencies. When working with Eclipse, however, you will have to add them to your Target Platform. They can either be found within an existing Open Liberty installation, in the `dev/api/ibm` directory, or by running a Maven `package` goal on the included `osgi-deps` project, which will create a p2 site within its `target/repository` directory.

Additionally, to compile the Admin NSF via Maven and generate the final distribution, you will need to set up and configure a compilation server using the [NSF ODP Tooling project](https://github.com/OpenNTF/org.openntf.nsfodp).

## License

The code in the project is licensed under the Apache License 2.0. The dependencies in the binary distribution are licensed under compatible licenses - see NOTICE for details.