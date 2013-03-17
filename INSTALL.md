Installing TickThreading
==========

- Install Forge, if you don't already have it.
- [Download TickThreading](http://nallar.me/buildservice/job/TickThreading/lastSuccessfulBuild/artifact/target/)
- Put it in your minecraft mods directory
- Run minecraft, join a world once if using a client, else just start it if using a server
- Close minecraft
- Run PATCHME.cmd/sh in your minecraft directory
- Have fun, if anything breaks make an issue report or contact me on EsperNet/Freenode IRC. nick: nallar

Updating TickThreading
==========

- Remove the old TT jar from your mods folder
- Add the new jar
- Start and stop the server OR change the path to the TT jar in the "PATCHME" file
- Run PATCHME.cmd/sh

Optional - these JVM flags may improve performance
==========

- Your start.bat/sh should look something like this (replace 8192 with the RAM the server should be allowed to use, in megabytes):
- java -server -XX:UseSSE=4 -XX:+UseCMSCompactAtFullCollection -XX:ParallelGCThreads=6 -XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC -XX:+CMSIncrementalMode -XX:+CMSIncrementalPacing -XX:+UseCompressedOops -XX:+AggressiveOpts -Xmx8192M -jar server.jar nogui
- Replace the "6" in -XX:ParallelGCThreads=6 with the number of cores your server has.
