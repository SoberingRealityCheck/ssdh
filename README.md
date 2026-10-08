# Seasonal LODs (ssdh26)

Recolors Distant Horizons LODs when the Serene Seasons season changes.

Without it, near terrain follows the season and far LODs stay stuck on
the old colors. Trees and grass both. See "How it works" below.

Client only. Needs Minecraft 26.2, Fabric, Distant Horizons 3.3.x and
Serene Seasons.

## Build

Needs Java 25. Put the two jars from `libs/README.md` in `libs/`.

```
./gradlew build
```

The mod lands in `build/libs/ssdh26-1.0.0.jar`.

## How it works

1. Watch the Serene Seasons sub-season every tick.
2. On a change (or joining a world), wait a few ticks.
3. Clear DH's tint cache and block color cache.
4. Walk every rendered LOD section, nearest first. Hand 4 per tick to
   DH's own reload queue. (It also queues each section's neighbors, so
   some sections rebuild more than once. See the comment in the code.)

DH keeps the old buffer on screen until the new one is ready. So the
recolor is a quiet sweep. No flash, no ring.

`/ssdh reload` runs the sweep by hand. Watch the log for `Sweep started`.

## Fake snow on far LODs

Serene Seasons adds and melts snow as real blocks, but only in loaded
chunks. A chunk nobody has visited since winter still shows autumn on the
LODs. So the mod paints the season onto the LODs.

For each column DH draws, it asks Serene Seasons if that spot is cold
enough to snow right now. Cold: the color goes toward white. Not cold: a
snow layer gets the biome's grass color back. The world is not changed.
Only how the LOD looks.

`/ssdh snow off` and `/ssdh snow on` turn it off and on. Not saved. It's on
at launch.

It's a fake, and it's rough:

- DH colors per block, not per face. Cliff sides go white with the tops.
- It can't see under a snow layer. Melted snow becomes grass colored, even
  over stone or sand.
- It can't see roofs. Anything DH draws gets snow.
- It doesn't fix the in-world snow, or maps like Xaero's.

## Status

It compiles. It has never been run in a real client. Things to check
when it is:

- Do far LODs actually change color after the sweep? Read from the
  bytecode, it should. DH asks vanilla's tint sources for colors. Those
  read `BiomeColors.GRASS_COLOR_RESOLVER` at call time. Serene Seasons
  replaces that field. Not seen working yet.
- Serene Seasons only swaps the grass and foliage resolvers. Dry foliage
  and water are not seasonal, so LODs won't change for those. Same as
  nearby terrain.
- Is 4 sections per tick too slow or too heavy? It's a guess.
- Do the delays (10 ticks after a season change, 60 after joining)
  catch the season sync from the server?

It reaches only public DH fields and methods, no reflection. A DH update
can still rename them. That breaks the build, or at runtime the sweep logs
an error and stops. `fabric.mod.json` pins DH to 3.3.x.

## Credit

The idea comes from ItsThatNova's
[Serene Seasons X Distant Horizons](https://github.com/ItsThatNova/serene-seasons-x-distant-horizons)
(MIT, Minecraft 1.21.1). This is a rewrite for 26.2 and was
written from that design. The MIT license and their copyright notice stay in `LICENSE`.
