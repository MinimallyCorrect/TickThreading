#!/bin/bash
bash ForgeUpdate.sh
rm -rf forge/mcp/src
cp -r forge/mcp/forge_src forge/mcp/src
cp -r src/main/java/* forge/mcp/src/

cd forge/mcp

./recompile.sh
./reobfuscate.sh

cd reobf/minecraft_server

rm ../reobf_server.jar
zip -r ../reobf_server.jar *

cd ../../../..

mkdir -p reobfuscated

mv forge/mcp/reobf/reobf_server.jar reobfuscated/