package me.nallar.tickthreading.patcher;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.mappings.MCPMappings;
import me.nallar.tickthreading.mappings.Mappings;
import org.xml.sax.SAXException;

public class PatchObfuscator {
	public static void main(String[] args) {
		String mcpConfigPath = args.length > 0 ? args[1] : "build/forge/mcp/conf";
		String inputPatchPath = args.length > 1 ? args[2] : "resources/patches-unobfuscated.xml";
		String outputPatchPath = args.length > 2 ? args[3] : "build/classes/patches.xml";

		Log.info("Obfuscating " + inputPatchPath + " to " + outputPatchPath + " via " + mcpConfigPath);

		try {
			Mappings mcpMappings = new MCPMappings(new File(mcpConfigPath));
			PatchManager patchManager = new PatchManager(new File(inputPatchPath), Patches.class);

			try {
				patchManager.obfuscate(mcpMappings);
				patchManager.save(new File(outputPatchPath));
			} catch (TransformerException e) {
				Log.severe("Failed to save obfuscated patch");
			}
		} catch (IOException e) {
			Log.severe("Failed to read input file", e);
		} catch (SAXException e) {
			Log.severe("Failed to parse input file", e);
		}
	}
}
