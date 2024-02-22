**BY DOWNLOADING OR USING THE LX SOFTWARE OR ANY PART THEREOF, YOU AGREE TO THE TERMS AND CONDITIONS OF THE [CHROMATIK / LX SOFTWARE LICENSE AND DISTRIBUTION AGREEMENT](http://chromatik.co/license/).**

Please note that LX is not open-source software. The license grants permission to use this software freely in non-commercial applications. Commercial use is subject to a total annual revenue limit of $25K on any and all projects associated with the software. If this licensing is obstructive to your needs or you are unclear as to whether your desired use case is compliant, contact me to discuss proprietary licensing: licensing@chromatik.co

---

### LX Overview ###

LX is a software library for real-time procedural animation, primarily designed for pixel-based LED lighting systems. It is the foundation of the [Chromatik](https://chromatik.co) application (formerly known as LX Studio).

The modular engine design contains a variety of components:

* Generic parameter and time-based modulation APIs
* Geometric model and matrix transformations
* MIDI interactivity
* Real-time audio analysis
* Color composition and blending

Output via a variety of lighting protocols is supported, including:

* [Open Pixel Control](http://openpixelcontrol.org/)
* [ArtNet](http://art-net.org.uk/)
* [E1.31 Streaming ACN](http://www.opendmx.net/index.php/E1.31)
* [Distributed Display Protocol](http://www.3waylabs.com/ddp/)
* [Fadecandy](https://github.com/scanlime/fadecandy)
* KiNET

LX differs from many other lighting/VJ software packages in that it is designed to support non-uniform 3D pixel layouts, rather than dense 2D screens. Whereas many applications are capable of video mapping LED pixel arrays, LX functions more like a sparse vertex shader. The rendering engine takes into account the discrete spatial position of each pixel.

### GUI ###

A companion library, [GLX](https://github.com/heronarts/GLX), makes it simple to embed LX in a GUI environment (using [LWJGL](https://www.lwjgl.org/) and [bgfx](https://github.com/bkaradzic/bgfx)) with modular UI controls and simulation, the  most typical use case. This core library is kept separate, free of any UI dependencies.

[Chromatik](https://chromatik.co) is a fully-featured digital lighting workstation with a rich UI for visualization and control.

### Headless ###

LX may be run in headless mode on any Java-enabled device (such as a Raspberry Pi).

### Contact and Collaboration ###

Building a big cool project? I'm probably interested in hearing about it! Want to solicit some help, request new framework features, or just ask a random question? Feel free to open a GitHub issue directly on this project, drop me a line at mark@chromatik.co or [join the Discord](https://chromatik.co/discord).

---

HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE, WITH RESPECT TO THE SOFTWARE.

### Building with Maven ###
Install Maven for your platform. Google is your friend.

To compile, package, and make available via local Maven repository:
```
$ cd LX
$ mvn install
```

`mvn install` creates the following artifacts:

in `LX/target`:
1. fat jar with dependencies
1. thin jar for distribution via maven repository publishing
1. source jar for distribution via maven repository publishing
1. javadoc jar for distribution via maven repository publishing
1. javadoc html files for publishing to web: `apidocs`

To deploy the signed package to Sonatype Maven repository (requires admin GPG keys and access):
```
$ cd LX
$ mvn deploy -Pdeploy
```
