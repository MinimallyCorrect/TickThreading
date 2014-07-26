TickThreading [![Build Status](http://nallar.me/buildservice/job/TickThreading-1.6.4/badge/icon)](http://nallar.me/buildservice/job/TickThreading-1.6.4/)
==========
Multi-threaded minecraft server wrapper. Requires Forge, compatible with MCPC+.

Copyright &copy; 2012-2014, nallar <rallan.pcl+tt@gmail.com>

TickThreading is licensed under the [N Open License, Version 1][License]

Download
-----

Download the latest builds from [Jenkins].

Compatibility with other mods
-----
[See the wiki.](https://github.com/nallar/TickThreading/wiki/Mod-Compatibility)

Configuration
-----
TickThreading uses minecraft forge's suggested config location - minecraft folder/configs/TickThreading.cfg
Some additional configuration options which need to be set before the server is started can be changed in the ttlaunch.properties file in your server folder.
It's commented quite well, and is hopefully understandable. If any of the descriptions don't make sense please make an issue.

Logging
-----
TickThreading stores its logs in the TickThreadingLogs directory, and will keep the previous 5 logs.
Make sure to include all relevant logs if you run into a problem.

Source
------
You're staring right at it! :P

Compiling
---------
TickThreading is built using Gradle.

* Install JDK 7. Set your JDK_HOME environment variable to your JDK 7 install path
* Checkout this repo and run: `gradlew.bat setupDecompWorkspace setupDevWorkspace build`
* Make some Tea/Coffee/Yuanyang and watch youtube videos while Forge takes 15 minutes to set up

Coding and Pull Request Formatting
----------------------------------
* Generally follows the Oracle coding standards.
* Tabs, no spaces.
* Pull requests must compile and work.
* Pull requests must be formatted properly.
* Code should be self-documenting - when possible meaningful names and good design should make comments unnecessary

Please follow the above conventions if you want your pull requests accepted.

IRC
----------------------------------

Chat with us at [irc.esper.net #TickThreading](irc://irc.esper.net/TickThreading)

Build service alerts of new builds are available at #n-builds.

Donations
----------------------------------

Bitcoin address: [1BUjvwxxGH23Fkj7vdGtbrgLEF91u8npQu](bitcoin:1BUjvwxxGH23Fkj7vdGtbrgLEF91u8npQu)

Paypal address: rossallan3+pp@googlemail.com

Contributors
----------------------------------

* [nallar](https://github.com/nallar/ "Ross Allan")
* Everyone who's helped with testing and reporting bugs :)

Acknowledgements
----------------------------------

YourKit is kindly supporting open source projects with its full-featured Java Profiler. YourKit, LLC is the creator of innovative and intelligent tools for profiling Java and .NET applications. Take a look at YourKit's leading software products: [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).

[License]: http://nallar.me/licenses/n-open-license-v1.txt
[Jenkins]: http://nallar.me/buildservice
