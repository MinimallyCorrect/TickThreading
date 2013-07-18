package nallar.tickthreading.mappings;

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

import nallar.tickthreading.Log;

public class MCPMappings extends Mappings {
	private static final Pattern extendsPattern = Pattern.compile("\\s+?extends\\s+?([\\S]+)[^\\{]+?\\{", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packagePattern = Pattern.compile("package\\s+?([^\\s;]+)[^;]*?;", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern classObfuscatePattern = Pattern.compile("\\^class:([^\\^]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern methodObfuscatePattern = Pattern.compile("\\^method:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern fieldObfuscatePattern = Pattern.compile("\\^field:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private final Map<String, String> methodSeargeMappings = new HashMap<String, String>();
	private final Map<String, String> fieldSeargeMappings = new HashMap<String, String>();
	private final BiMap<ClassDescription, ClassDescription> classMappings = HashBiMap.create();
	private final BiMap<MethodDescription, MethodDescription> methodMappings = HashBiMap.create();
	private final Map<FieldDescription, FieldDescription> fieldMappings = new HashMap<FieldDescription, FieldDescription>();
	private final Map<String, MethodDescription> parameterlessMethodMappings = new HashMap<String, MethodDescription>();
	private final Map<String, String> classNameToSuperClassName = new HashMap<String, String>();
	private final Map<String, String> shortClassNameToFullName = new HashMap<String, String>();

	@Override
	public MethodDescription map(MethodDescription methodDescription) {
		MethodDescription obfuscated = methodMappings.get(methodDescription);
		if (obfuscated == null) {
			obfuscated = parameterlessMethodMappings.get(methodDescription.getShortName());
			if (methodDescription.isExact() || obfuscated == null) {
				obfuscated = methodDescription;
				obfuscated.obfuscateClasses();
			}
		}
		return obfuscated;
	}

	@Override
	public MethodDescription rmap(MethodDescription methodDescription) {
		return methodMappings.inverse().get(methodDescription);
	}

	@Override
	public String obfuscate(String code) {
		StringBuffer result = new StringBuffer();

		{
			Matcher methodMatcher = methodObfuscatePattern.matcher(code);
			while (methodMatcher.find()) {
				String className = shortClassNameToFullClassName(methodMatcher.group(1));
				String methodDescriptionString = methodMatcher.group(2);
				if (className == null) {
					className = methodMatcher.group(1);
					if (!className.contains(".")) {
						Log.severe("Could not find " + methodMatcher.group(1));
						continue;
					}
				}
				MethodDescription methodDescription = MethodDescription.fromString(className, methodDescriptionString);
				MethodDescription mapped = map(methodDescription);
				methodMatcher.appendReplacement(result, mapped.name);
			}
			methodMatcher.appendTail(result);
		}

		{
			Matcher fieldMatcher = fieldObfuscatePattern.matcher(result);
			result = new StringBuffer();
			while (fieldMatcher.find()) {
				String className = shortClassNameToFullClassName(fieldMatcher.group(1));
				String fieldName = fieldMatcher.group(2);
				if (className == null) {
					className = fieldMatcher.group(1);
					if (!className.contains(".")) {
						Log.severe("Could not find " + fieldMatcher.group(1));
						continue;
					}
				}
				FieldDescription fieldDescription = new FieldDescription(className, fieldName);
				FieldDescription mapped = map(fieldDescription);
				if (mapped == null) {
					Log.severe("Could not map " + fieldName);
					fieldMatcher.appendReplacement(result, fieldName);
				} else {
					fieldMatcher.appendReplacement(result, mapped.name);
				}
			}
			fieldMatcher.appendTail(result);
		}

		{
			Matcher classMatcher = classObfuscatePattern.matcher(result);
			result = new StringBuffer();
			while (classMatcher.find()) {
				String className = classStringToClassName(classMatcher.group(1));
				if (className == null) {
					Log.severe("Could not find " + classMatcher.group(1));
					continue;
				}
				classMatcher.appendReplacement(result, className);
			}
			classMatcher.appendTail(result);
		}

		return result.toString();
	}

	private String classStringToClassName(String name) {
		String mapped = shortClassNameToFullClassName(name);
		if (mapped != null) {
			name = mapped;
		}
		ClassDescription classDescription = map(new ClassDescription(name));
		if (classDescription == null) {
			Log.severe("Couldn't map class name " + name);
			return name;
		}
		return classDescription.name;
	}

	@Override
	public String shortClassNameToFullClassName(String shortName) {
		return shortClassNameToFullName.get(shortName);
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
				shortClassNameToFullName.put(deobfuscatedClass.name.substring(deobfuscatedClass.name.lastIndexOf('.') + 1), deobfuscatedClass.name);
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

	public Map<String, String> getSimpleClassNameMappings() {
		Map<String, String> map = new HashMap<String, String>();
		for (Map.Entry<ClassDescription, ClassDescription> entry : classMappings.entrySet()) {
			map.put(entry.getValue().name, entry.getKey().name);
		}
		return map;
	}
}
