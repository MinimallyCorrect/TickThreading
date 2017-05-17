Installing TickThreading
==========

- Download TickThreading
- Place it in your mods folder
- Have fun. Make sure to report any issues encountered while using TT to me, not other mod developers!

Updating TickThreading
==========

- Stop the server.
- Download the new TT jar and replace the old one in your mods folder
- Start server.

Java Tuning
==========

TODO: Determine new suggested JVM args. We may now suggest G1GC on java 8?

- Use the latest Java 8
- Make sure not to set -Xmx higher than you need. Don't set Xmx >= 31GB, as it will disable CompressedOOPs.
- Suggested java parameters - make sure to set -Xmx:  
    java -server -Xmx{memoryToUseInGB}G -XX:UseSSE=4 -XX:+UseConcMarkSweepGC -XX:+UseCMSCompactAtFullCollection -XX:+UseParNewGC -XX:+DisableExplicitGC -XX:+AggressiveOpts -jar server.jar nogui

- *Xmx* sets the memory the server is allowed to use. It should be set to a reasonable value - enough for the server to run, but not much higher than it needs. >= 32GB will prevent the JVM from using
compressed OOPs, an optimisation which reduces the size of pointers and generally gives a reasonable performance boost, so it's a good idea to keep Xmx low.
- *UseConcMarkSweepGC* enables the CMS garbage collector. The garbage collector is an important part of the JVM which frees memory used by objects which are no longer needed. By default,
a non-concurrent garbage collector is used, which can cause lag spikes if -Xmx is not very small.
- *UseCMSCompactAtFullCollection* asks the CMS garbage collector to compact, or defragment, the memory used by the JVM at full collections. When disabled, GC might need to run more often if a large
object is allocated which does not fit into any gaps in the heap.
- *UseParNewGC* enables concurrent garbage collection for recently created objects, and should be used for the same reasons as for UseConcMarkSweepGC
- *DisableExplicitGC* prevents silly mods/plugins from asking the JVM to immediately do a full garbage collection. Otherwise, when a mod/plugin does this, it will always freeze the entire server to perform the
garbage collection, ignoring the parameters set above.
- *AggressiveOpts* enables experimental JVM options which should improve performance, but may not have been fully tested and could make performance worse in some cases. For example, with the latest Java 7
it enables some optimisations relating to auto boxing - a conversion between primitive and object types.
