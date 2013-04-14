TickThreading [![Build Status](http://nallar.me/buildservice/job/TickThreading/badge/icon)](http://nallar.me/buildservice/job/TickThreading/)
==========
Mod to run entity and tileentity ticks in threads and a patcher to make the server and any mods/plugins threadsafe.
Optionally, allows for automatically variable tick rate per tick region. (Normally a chunk)

Copyright &copy; 2012, nallar <rallan.pcl+tt@gmail.com>
TickThreading is licensed under the [N Open License, Version 1][License]

Compatibility with other mods
-----
[See the wiki.](https://github.com/nallar/TickThreading/wiki/Mod-Compatibility)

Configuration
-----
TickThreading uses minecraft forge's suggested config location - minecraft folder/configs/TickThreading.cfg
It's commented quite well, and is hopefully understandable. If any of the descriptions don't make sense please make an issue.

Logging
-----
TickThreading stores its logs in the TickThreadingLogs directory, and will keep the previous 5 logs.
Make sure to include all relevant logs if you run into a problem.

Source
------
You're staring right at it! :P
Download the latest builds from [Jenkins].  

Compiling
---------
TickThreading is built using Ant. astyle is also required for linux users.

* Install [Ant](http://ant.apache.org/)
* Checkout this repo and run: `ant`

Coding and Pull Request Formatting
----------------------------------
* Generally follows the Oracle coding standards.
* Tabs, no spaces.
* Pull requests must compile and work.
* Pull requests must be formatted properly.

Please follow the above conventions if you want your pull requests accepted.

Donations
----------------------------------

![QR Code](http://i.imgur.com/U2ftDFQ.png)

[1BUjvwxxGH23Fkj7vdGtbrgLEF91u8npQu](bitcoin:1BUjvwxxGH23Fkj7vdGtbrgLEF91u8npQu)

![](http://ansrv.com/png?s=http://blockexplorer.com/q/getreceivedbyaddress/1BUjvwxxGH23Fkj7vdGtbrgLEF91u8npQu&amp;c=000000&amp;b=FFFFFF&amp;size=5)

Contributors
----------------------------------

* [nallar](https://github.com/nallar/ "Ross Allan")
* Everyone who's helped with testing and reporting bugs :)

Acknowledgements
----------------------------------

YourKit is kindly supporting open source projects with its full-featured Java Profiler. YourKit, LLC is the creator of innovative and intelligent tools for profiling Java and .NET applications. Take a look at YourKit's leading software products: [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).

[License]: http://nallar.me/licenses/n-open-license-v1.txt
[Jenkins]: http://nallar.me/buildservice
