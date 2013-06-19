package nallar.tickthreading.mappings;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import nallar.tickthreading.Log;

public class MCPMappings extends Mappings {
	private static final Pattern classObfuscatePattern = Pattern.compile("\\^class:([^\\^]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern methodObfuscatePattern = Pattern.compile("\\^method:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern fieldObfuscatePattern = Pattern.compile("\\^field:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private final Map<String, String> methodSeargeMappings = new HashMap<String, String>();
	private final Map<String, String> fieldSeargeMappings = new HashMap<String, String>();
	private final BiMap<ClassDescription, ClassDescription> classMappings = HashBiMap.create();
	private final BiMap<MethodDescription, MethodDescription> methodMappings = HashBiMap.create();
	private final BiMap<MethodDescription, MethodDescription> methodSrgMappings = HashBiMap.create();
	private final Map<FieldDescription, FieldDescription> fieldMappings = new HashMap<FieldDescription, FieldDescription>();
	private final Map<FieldDescription, FieldDescription> fieldSrgMappings = new HashMap<FieldDescription, FieldDescription>();
	private final Map<String, MethodDescription> parameterlessMethodMappings = new HashMap<String, MethodDescription>();
	private final Map<String, MethodDescription> parameterlessSrgMethodMappings = new HashMap<String, MethodDescription>();
	private final Map<String, String> shortClassNameToFullName = new HashMap<String, String>();
	public boolean seargeMappings = false;

	@SuppressWarnings ("IOResourceOpenedButNotSafelyClosed")
	public MCPMappings() throws IOException {
		loadCsv(Mappings.class.getResourceAsStream("/mappings/methods.csv"), methodSeargeMappings);
		loadCsv(Mappings.class.getResourceAsStream("/mappings/fields.csv"), fieldSeargeMappings);
		loadSrg(Mappings.class.getResourceAsStream("/mappings/packaged.srg"));
		methodSeargeMappings.clear();
		fieldSeargeMappings.clear();
	}

	@Override
	public MethodDescription map(MethodDescription methodDescription) {
		MethodDescription obfuscated = (seargeMappings ? methodSrgMappings : methodMappings).get(methodDescription);
		if (obfuscated == null) {
			obfuscated = (seargeMappings ? parameterlessSrgMethodMappings : parameterlessMethodMappings).get(methodDescription.getShortName());
			if (methodDescription.isExact() || obfuscated == null) {
				obfuscated = methodDescription;
				obfuscated.obfuscateClasses();
			}
		}
		return obfuscated;
	}

	@Override
	public MethodDescription rmap(MethodDescription methodDescription) {
		return (seargeMappings ? methodSrgMappings : methodMappings).inverse().get(methodDescription);
	}

	@Override
	public String shortClassNameToFullClassName(String shortName) {
		return shortClassNameToFullName.get(shortName);
	}

	@Override
	public ClassDescription map(ClassDescription classDescription) {
		return seargeMappings ? classDescription : classMappings.get(classDescription);
	}

	@Override
	public FieldDescription map(FieldDescription fieldDescription) {
		return (seargeMappings ? fieldSrgMappings : fieldMappings).get(fieldDescription);
	}

	private String classStringToClassName(String name) {
		String mapped = shortClassNameToFullClassName(name);
		if (mapped != null) {
			name = mapped;
		}
		return map(new ClassDescription(name)).name;
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
					Log.severe("Failed to deobfuscate field " + className + '/' + fieldName);
				}
				fieldMatcher.appendReplacement(result, mapped.name);
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

	private void loadSrg(InputStream mappings) throws IOException {
		Scanner srgScanner = new Scanner(mappings);
		while (srgScanner.hasNextLine()) {
			if (srgScanner.hasNext("CL:")) {
				srgScanner.next();
				String fromClass = srgScanner.next().replace('/', '.');
				String toClass = srgScanner.next().replace('/', '.');
				ClassDescription obfuscatedClass = new ClassDescription(fromClass);
				ClassDescription deobfuscatedClass = new ClassDescription(toClass);
				shortClassNameToFullName.put(deobfuscatedClass.name.substring(deobfuscatedClass.name.lastIndexOf('.') + 1), deobfuscatedClass.name);
				classMappings.put(deobfuscatedClass, obfuscatedClass);
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
				FieldDescription srgField = new FieldDescription(deobfuscatedField.className, seargeName);
				fieldMappings.put(deobfuscatedField, obfuscatedField);
				fieldSrgMappings.put(deobfuscatedField, srgField);
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
				MethodDescription srgMethodDescription = new MethodDescription(deobfuscatedClassName, seargeName, deobfuscatedTypeInfo);
				methodMappings.put(deobfuscatedMethodDescription, obfuscatedMethodDescription);
				methodSrgMappings.put(deobfuscatedMethodDescription, srgMethodDescription);
				parameterlessMethodMappings.put(deobfuscatedMethodDescription.getShortName(), obfuscatedMethodDescription);
				parameterlessSrgMethodMappings.put(deobfuscatedMethodDescription.getShortName(), srgMethodDescription);
			} else {
				srgScanner.nextLine();
			}
		}
	}

	private static void loadCsv(InputStream mappingsCsv, Map<String, String> seargeMappings) throws IOException {
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
