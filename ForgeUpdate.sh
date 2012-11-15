#!/bin/bash

MCP_VERSION="721"
FORGE_VERSION="latest"

MCP_URL="https://dl.dropbox.com/s/dao8itysfwuhj9q/mcp$MCP_VERSION.zip?dl=1"
FORGE_URL="http://files.minecraftforge.net/minecraftforge-src-$FORGE_VERSION.zip"

mkdir -p forge
cd forge
mkdir -p downloads
cd downloads

update="false"

if [[ $1 == "force" ]]; then
	update="true"
fi

if [ ! -f lastMCPVersion ] || [ `cat lastMCPVersion` != $MCP_VERSION ]; then
	touch lastMCPVersion
	echo "Updating MCP from" `cat lastMCPVersion` to $MCP_VERSION
	echo $MCP_VERSION > lastMCPVersion
	rm -f mcp.zip
	curl -L --progress-bar -o mcp.zip $MCP_URL
	update="true"
fi

if [ ! -f lastForgeVersion ] || [ `cat lastForgeVersion` != $FORGE_VERSION ]; then
	touch lastForgeVersion
	echo "Updating Forge from" `cat lastForgeVersion` to $FORGE_VERSION
	echo $FORGE_VERSION > lastForgeVersion
	rm -f forge.zip
	curl -L --progress-bar -o forge.zip $FORGE_URL
	update="true"
fi

if [ $update == "true" ]; then
	rm -rf ../mcp
	mkdir -p ../mcp
	unzip -u -d ../mcp mcp.zip > mcpExtract.log
	unzip -u -d ../mcp forge.zip > forgeExtract.log
	cd ../mcp/forge
	chmod -R +x ../
	./install.sh
	cd ..
	mv src forge_src
	cd ../..
	cp -r forge/mcp/forge_src forge/mcp/src

	cd forge/mcp
	
	./recompile.sh
	./reobfuscate.sh

	cd bin/minecraft

	rm ../minecraft.jar
	zip -r ../minecraft.jar *

	cd ../../../..

	mkdir -p libs/net/mojang/minecraft/1.0/

	mv forge/mcp/bin/minecraft.jar libs/net/mojang/minecraft/1.0/minecraft-1.0.jar
fi