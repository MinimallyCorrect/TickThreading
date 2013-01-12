package me.nallar.tickthreading.mappings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

import me.nallar.tickthreading.Log;

public class MCPMappings extends Mappings {
	private static final Pattern extendsPattern = Pattern.compile("\\s+?extends\\s+?([\\S]+)[^\\{]+?\\{", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packagePattern = Pattern.compile("package\\s+?([^\\s;]+)[^;]*?;", Pattern.DOTALL | Pattern.MULTILINE);
	private final Map<String, String> methodSeargeMappings = new HashMap<String, String>();
	private final Map<String, String> fieldSeargeMappings = new HashMap<String, String>();
	private final BiMap<ClassDescription, ClassDescription> classMappings = HashBiMap.create();
	private final Map<MethodDescription, MethodDescription> methodMappings = new HashMap<MethodDescription, MethodDescription>();
	private final Map<FieldDescription, FieldDescription> fieldMappings = new HashMap<FieldDescription, FieldDescription>();
	private final Map<String, MethodDescription> parameterlessMethodMappings = new HashMap<String, MethodDescription>();
	private final Map<String, String> classNameToSuperClassName = new HashMap<String, String>();

	@Override
	public MethodDescription map(MethodDescription methodDescription) {
		MethodDescription obfuscated = methodMappings.get(methodDescription);
		if (obfuscated == null) {
			obfuscated = parameterlessMethodMappings.get(methodDescription.getShortName());
			if (methodDescription.isExact()) {
				Log.info("Failed to directly map " + methodDescription
						+ "\n would map to " + obfuscated);
				obfuscated = methodDescription;
				obfuscated.obfuscateClasses();
			}
		}
		return obfuscated;
	}

	@Override
	public ClassDescription map(ClassDescription classDescription) {
		return classMappings.get(classDescription);
	}

	@Override
	public FieldDescription map(FieldDescription fieldDescription) {
		FieldDescription obfuscated = fieldMappings.get(fieldDescription);
		if (obfuscated == null) {
			String className;
			do {
				className = classNameToSuperClassName.get(fieldDescription.className);
				Log.info(fieldDescription + " -> " + className);
				if (className != null) {
					fieldDescription = new FieldDescription(className, fieldDescription.name);
					obfuscated = fieldMappings.get(fieldDescription);
				}
			} while (obfuscated == null && className != null);
		}
		return obfuscated;
	}

	public MCPMappings(File mcpDir) throws IOException {
		parse(mcpDir);
	}

	void parse(File mcpDir) throws IOException {
		loadCsv(new File(mcpDir, "methods.csv"), methodSeargeMappings);
		loadCsv(new File(mcpDir, "fields.csv"), fieldSeargeMappings);
		loadSrg(new File(mcpDir, "packaged.srg"));
		methodSeargeMappings.clear();
		fieldSeargeMappings.clear();
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
				File sourceLocation = new File(mappingsSrg.getParentFile().getParentFile(), "src/minecraft/" + (toClass.replace('.', '/') + ".java"));
				try {
					String contents = Files.toString(sourceLocation, Charset.forName("UTF-8"));
					Matcher extendsMatcher = extendsPattern.matcher(contents);
					if (extendsMatcher.find()) {
						String shortExtendsClassName = extendsMatcher.group(1);
						String extendsClassName = null;
						for (String line : Splitter.on('\n').trimResults().split(contents)) {
							if (line.endsWith('.' + shortExtendsClassName + ';')) {
								extendsClassName = line.substring(7, line.length() - 1);
							}
						}
						if (extendsClassName == null) {
							Matcher packageMatcher = packagePattern.matcher(contents);
							if (packageMatcher.find()) {
								extendsClassName = packageMatcher.group(1) + '.' + shortExtendsClassName;
							}
						}
						if (extendsClassName != null) {
							classNameToSuperClassName.put(toClass, extendsClassName);
						}
					}
				} catch (FileNotFoundException ignored) {
				}
			} else if (srgScanner.hasNext("FD:")) {
				srgScanner.next();
				String obfuscatedMCPName = srgScanner.next();
				String seargeName = srgScanner.next();
				seargeName = seargeName.substring(seargeName.lastIndexOf('/') + 1);
				String deobfuscatedName = fieldSeargeMappings.get(seargeName);
				if (deobfuscatedName == null) {
					deobfuscatedName = seargeName;
				}
				FieldDescription obfuscatedField = new FieldDescription(obfuscatedMCPName);
				FieldDescription deobfuscatedField = new FieldDescription(classMappings.inverse().get(new ClassDescription(obfuscatedField.className)).name, deobfuscatedName);
				fieldMappings.put(deobfuscatedField, obfuscatedField);
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
				String deobfuscatedName = methodSeargeMappings.get(seargeName);
				if (deobfuscatedName == null) {
					deobfuscatedName = seargeName;
				}
				MethodDescription deobfuscatedMethodDescription = new MethodDescription(deobfuscatedClassName, deobfuscatedName, deobfuscatedTypeInfo);
				MethodDescription obfuscatedMethodDescription = new MethodDescription(obfuscatedClassName, obfuscatedName, obfuscatedTypeInfo);
				methodMappings.put(deobfuscatedMethodDescription, obfuscatedMethodDescription);
				parameterlessMethodMappings.put(deobfuscatedMethodDescription.getShortName(), obfuscatedMethodDescription);
			} else {
				srgScanner.nextLine();
			}
		}
	}

	private static void loadCsv(File mappingsCsv, Map<String, String> seargeMappings) throws IOException {
		Scanner in = new Scanner(mappingsCsv);
		try {
			in.useDelimiter(",");
			while (in.hasNextLine()) {
				String seargeName = in.next();
				String name = in.next();
				String side = in.next();
				in.nextLine();
				if ("2".equals(side)) { // 2 = joined 'side'.
					seargeMappings.put(seargeName, name);
				}
			}
		} finally {
			in.close();
		}
	}
}
