TickThreading [![Build Status](http://nallar.me/buildservice/job/TickThreading/badge/icon)](http://nallar.me/buildservice/job/TickThreading/)
==========
Mod to run entity and tileentity ticks in threads and a patcher to improve thread-safety issues.
Optionally, allows for automatically variable tick rate per tick region.

Copyright &copy; 2012, nallar <rallan.pcl+gt@gmail.com>
TickThreading is licensed under the [N Open License, Version 1][License]

Compatibility with other mods
-----
Things will break! I don't expect other mod developers to try to make things threadsafe, the patcher takes care of it.

What you can't do:

* Access the loadedTileEntityList. It just won't work, and if you grab its iterator everything will be ticked. I may try to write compatibility for this later.
* Call removeAll on loadedEntityList without also setting its tickAccess field to false after, else .size() will return 0 for the next call to it.

Source
------
The latest and greatest source of TickThreading can be found right here.  
Download the latest builds from [Jenkins].  

Compiling
---------
TickThreading is built using Ant.

* Install [Ant](http://ant.apache.org/)
* Checkout this repo and run: `ant`

Coding and Pull Request Formatting
----------------------------------
* Generally follows the Oracle coding standards.
* Tabs, no spaces.
* Pull requests must compile and work.
* Pull requests must be formatted properly.

Please follow the above conventions if you want your pull requests accepted.

[License]: http://nallar.me/licenses/n-open-license-v1.txt
[Jenkins]: http://nallar.me/buildservice
