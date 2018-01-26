LX
==

**BY DOWNLOADING OR USING THE LX SOFTWARE OR ANY PART THEREOF, YOU AGREE TO THE TERMS AND CONDITIONS OF THE [LX STUDIO SOFTWARE LICENSE AND DISTRIBUTION AGREEMENT](http://lx.studio/license).**

Please note that LX is not open-source software. The license grants permission to use this software freely in non-commercial applications. Commercial use is subject to a total annual revenue limit of $25K on any and all projects associated with the software. If this licensing is obstructive to your needs or you are unclear as to whether your desired use case is compliant, contact me to discuss proprietary licensing: mark@heronarts.com

---

### Overview ###

LX is a software library for real-time procedural animation, primarily designed for pixel-based LED lighting systems. It is the foundation of the [LX Studio](http://lx.studio) application.

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

### GUI interface
A companion library, [P3LX](https://github.com/heronarts/P3LX), makes it simple to embed LX in the Processing 3 environment with modular UI controls and simulation, the  most typical use case. This core library is kept separate, free of any dependency on the Processing libraries or runtime.

[LX Studio](https://github.com/heronarts/LXStudio) is a fully-featured digital lighting workstation with a rich UI for visualization and control.

### Using this library headless
LX may be used headless.

+ **Build the library:** navigate to `./build` directory and run `ant`  This will build the LX library into a jar `./bin/LX.jar`
+ **Build an example:** navigate to `./examples/LXHeadless/` and run `ant` this will build the example which links against the LX library we just built.
+ **Run it!**  On a bytecode capable machine run: `java -jar bin/LXHeadless.jar`

By default the above example spews OPC formatted data via TCP on `localhost:7890`.
This data can be viewed using the emulator that comes with [openpixelcontrol](https://github.com/zestyping/openpixelcontrol) as follows...
+ `git clone git://github.com/zestyping/openpixelcontrol.git`
+ `cd openpixelcontrol`
+ `make`
+ `bin/gl_server layouts/freespace.json`

... you could also just use netcat to see raw data `nc -l 7890`

### Contact and Collaboration ###

Building a big cool project? I'm probably interested in hearing about it! Want to solicit some help, request new framework features, or just ask a random question? Open an issue on the project or drop me a line: mark@heronarts.com

---

HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE, WITH RESPECT TO THE SOFTWARE.
