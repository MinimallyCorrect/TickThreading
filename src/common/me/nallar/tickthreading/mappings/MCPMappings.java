package me.nallar.tickthreading.mappings;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import me.nallar.tickthreading.Log;

public class MCPMappings extends Mappings {
	Map<String, String> seargeMappings = new HashMap<String, String>();
	Map<ClassDescription, ClassDescription> classMappings = new HashMap<ClassDescription, ClassDescription>();
	Map<MethodDescription, MethodDescription> methodMappings = new HashMap<MethodDescription, MethodDescription>();
	Map<String, MethodDescription> parameterlessMethodMappings = new HashMap<String, MethodDescription>();

	public MethodDescription map(MethodDescription methodDescription) {
		MethodDescription obfuscated = methodMappings.get(methodDescription);
		if (obfuscated == null) {
			Log.info(methodDescription.getShortName());
			obfuscated = parameterlessMethodMappings.get(methodDescription.getShortName());
		}
		return obfuscated;
	}

	public ClassDescription map(ClassDescription classDescription) {
		return classMappings.get(classDescription);
	}

	public MCPMappings(File mcpDir) throws IOException {
		parse(mcpDir);
	}

	public void parse(File mcpDir) throws IOException {
		loadCsv(new File(mcpDir, "methods.csv"));
		loadSrg(new File(mcpDir, "joined.srg"));
		seargeMappings.clear();
	}

	private void loadSrg(File mappingsSrg) throws IOException {
		Scanner srgScanner = new Scanner(mappingsSrg);
		while (srgScanner.hasNextLine()) {
			if (srgScanner.hasNext("CL:")) {
				srgScanner.next();
				String fromClass = srgScanner.next().replace('/', '.');
				String toClass = srgScanner.next().replace('/', '.');
				ClassDescription obfuscatedClass = new ClassDescription(fromClass);
				ClassDescription deobfuscatedClass = new ClassDescription(toClass);
				classMappings.put(deobfuscatedClass, obfuscatedClass);
			} else if (srgScanner.hasNext("MD:")) {
				srgScanner.next();
				String obfuscatedName = srgScanner.next();
				String obfuscatedTypeInfo = srgScanner.next();
				String seargeName = srgScanner.next();
				String deobfuscatedTypeInfo = srgScanner.next();
				String obfuscatedClassName = obfuscatedName.substring(0, obfuscatedName.lastIndexOf('/')).replace('/', '.');
				obfuscatedName = obfuscatedName.substring(obfuscatedName.lastIndexOf('/') + 1);
				String deobfuscatedClassName = seargeName.substring(0, seargeName.lastIndexOf('/')).replace('/', '.');
				seargeName = seargeName.substring(seargeName.lastIndexOf('/') + 1);
				String deobfuscatedName = seargeMappings.get(seargeName);
				if (deobfuscatedName == null) {
					deobfuscatedName = seargeName;
				}
				MethodDescription deobfuscatedMethodDescription = new MethodDescription(deobfuscatedClassName, deobfuscatedName, deobfuscatedTypeInfo);
				MethodDescription obfuscatedMethodDescription = new MethodDescription(obfuscatedClassName, obfuscatedName, obfuscatedTypeInfo);
				methodMappings.put(deobfuscatedMethodDescription, obfuscatedMethodDescription);
				parameterlessMethodMappings.put(deobfuscatedMethodDescription.getShortName(), obfuscatedMethodDescription);
				Log.info(deobfuscatedMethodDescription.getShortName());
			} else {
				srgScanner.nextLine();
			}
		}
	}

	private void loadCsv(File mappingsCsv) throws IOException {
		Scanner in = new Scanner(mappingsCsv);
		try {
			in.useDelimiter(",");
			while (in.hasNextLine()) {
				String seargeName = in.next();
				String name = in.next();
				String side = in.next();
				in.nextLine();
				if (side.equals("2")) { // 2 = joined 'side'.
					seargeMappings.put(seargeName, name);
				}
			}
		} finally {
			in.close();
		}
	}
}
