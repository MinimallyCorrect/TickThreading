Installing TickThreading
==========

- Install Forge, if you don't already have it.
- [Download TickThreading](http://nallar.me/buildservice/job/TickThreading/lastSuccessfulBuild/artifact/target/)
- Put it in your minecraft mods directory
- Run minecraft, join a world once if using a client, else just start it if using a server
- Close minecraft
- Run PATCHME.cmd/sh in your minecraft directory
- Have fun, if anything breaks make an issue report or contact me on EsperNet/Freenode IRC. nick: nallar

Extra Performance Hints For Servers
==========

- Your start.bat/sh should look something like this (replace 8192 with the RAM the server should be allowed to use, in megabytes):
- java -server -XX:useSSE=3 -XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC -XX:+CMSIncrementalMode -XX:+CMSIncrementalPacing -XX:ParallelGCThreads=3 -XX:+UseCompressedOops -XX:+AggressiveOpts -Xmx8192M -jar server.jar nogui
