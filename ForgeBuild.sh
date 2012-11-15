#!/bin/bash
bash ForgeUpdate.sh
rm -rf forge/mcp/src
cp -r forge/mcp/forge_src forge/mcp/src
cp -r src/main/java/* forge/mcp/src/

cd forge/mcp

#./recompile.sh
#./reobfuscate.sh

cd forge/mcp/bin/minecraft

rm ../minecraft.jar
zip -r ../minecraft.jar *

cd ../../../..

mkdir -p libs/net/mojang/minecraft/1.0/

mv forge/mcp/bin/minecraft.jar libs/net/mojang/minecraft/1.0/minecraft-1.0.jar