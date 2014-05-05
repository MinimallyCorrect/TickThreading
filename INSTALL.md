Installing TickThreading
==========

- Install Forge, if you don't already have it. MCPC+ may also be used as the server jar.
- [Download TickThreading](http://nallar.me/buildservice/job/TickThreading/lastSuccessfulBuild/artifact/target/)
- Put the TickThreading-x.x.x.xxx.jar in the main server folder.
- Change the .jar file in your start script to the TickThreading.jar.
- Add this JVM argument that points TickThreading to the unpatched server .jar file: --serverJar=
- Example JVM arguments: java -Xms1G -Xmx1G -XX:PermSize=128m -jar TickThreading-1.6.4.105.jar --serverJar=minecraftforge.jar nogui
- Have fun, if anything breaks make an issue report or contact me on EsperNet/Freenode IRC. nick: nallar

Updating TickThreading
==========

- Stop the server.
- Remove the old TT jar from your server folder.
- Add the new jar.
- Change start script to new TickThreading version number.
- Start server.


Java Tuning
==========

- Use the latest Java 7.
- Make sure not to set -Xmx higher than you need - higher values may reduce performance, especially >= 32GB
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
