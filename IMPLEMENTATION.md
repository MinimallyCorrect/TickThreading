Patching
====

TT's patcher is quite simple, and based on javassist. It is configured via the patches(-deobfuscated).xml in the resources directory. The choice of XML for expressing patches is suboptimal, and other options will be available at some point.

The actual implementation of the patches used in the patch xml is in the `nallar.tickthreading.patcher.Patches` class.

For coremods and the minecraft server jar, the jar is modified. Mods folder mods are not modified directly, instead patched classes are stored in the patchedMods directory, and loaded at runtime. This allows TT to bypass any signature verification checks.

Concurrency model
====

In vanilla, a single thread ticks everything in order. The worlds, entites in the worlds, processes packets, and then repeats. This must be done in less than 50ms.

For compatibility reasons, TT still sticks with this model - one world can not run ticks ahead of another.

Each tick, first the pre server tick is handled by all mods with tick handlers. This is not performed concurrently.

Then, all worlds are ticked. Each world is ticked in a separate thread, up to a maximum of the number of threads set in the config.

Each world is split up into regions of 32x32 blocks, and a list of these regions is made. The number of threads set in the config are used to tick these regions. The entities and Tile Entities in these regions are then ticked, per region. A region will not be ticked at the same time as an adjacent region.

The world tick waits on completion of this until moving on to do other tasks. Waiting for completion can be disabled, but increases the risk of bugs, as then chunk unloading can occur while tile/entities are being ticked.

The server thread waits for all world ticks to complete, runs any mod post-server ticks, then processes all received packets. This then repeats from the start.
