# Domino Open Liberty Runtime

Inspired by [Sven Hasselbach's blog posts](http://hasselba.ch/blog/?p=2625), this project wraps [Open Liberty](https://openliberty.io), the open-source version of WebSphere Liberty, to run alongside the Domino HTTP task using the Domino JVM.

### What It Does

Like the original blog posts, this provides access to Domino classes and the surrounding Domino server environment. At least in initial testing, unlike the blog posts, accessing databases on the server doesn't cause a panic, allowing this to run alongside normal Domino server operations.

### What It Doesn't Do

This project does not enhance the Domino HTTP stack in any way. The normal HTTP task continues as normal, with its same abilities and limitations.

Additionally, this doesn't give the Liberty server any particular knowledge of how Domino normally works - it won't serve resources from NSFs on its own, nor does it automatically have access to the XPages OSGi framework.

## License

The code in the project is licensed under the Apache License 2.0. The dependencies in the binary distribution are licensed under compatible licenses - see NOTICE for details.