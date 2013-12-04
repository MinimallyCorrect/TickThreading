/*
 * Included in TT as can not be loaded from cpw package as that loads much of FML.
 * Forge Mod Loader
 * Copyright (c) 2012-2013 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     cpw - implementation
 */
package nallar.tickthreading.patcher.remapping;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.io.InputSupplier;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Deobfuscator extends Remapper {
	public static final Deobfuscator INSTANCE = new Deobfuscator();
	private static final String[] EMPTY_INTERFACES = new String[0];
	private BiMap<String, String> classNameBiMap;
	private BiMap<String, String> mcpNameBiMap;
	private Map<String, Map<String, String>> rawFieldMaps;
	private Map<String, Map<String, String>> rawMethodMaps;
	private Map<String, Map<String, String>> fieldNameMaps;
	private Map<String, Map<String, String>> methodNameMaps;

	private static byte[] getClassBytes(String name) {
		//Log.info("Name");
		byte[] bytes = ByteSource.classes.get(name);
		if (bytes != null) {
			return bytes;
		}
		return getClassBytesClassPath(name);
	}

	private static byte[] getClassBytesClassPath(String name) {
		InputStream stream = TickThreading.class.getResourceAsStream(name.replace('.', '/') + ".class");
		if (stream == null) {
			return null;
		}
		try {
			return ByteStreams.toByteArray(stream);
		} catch (IOException e) {
			Log.severe("Failed to read class " + name, e);
		} finally {
			Closeables.closeQuietly(stream);
		}
		return null;
	}

	private Deobfuscator() {
		classNameBiMap = ImmutableBiMap.of();
		mcpNameBiMap = ImmutableBiMap.of();
	}

	public void setup(InputStream mapData) {
		try {
			LZMAInputSupplier zis = new LZMAInputSupplier(mapData);
			InputSupplier<InputStreamReader> srgSupplier = CharStreams.newReaderSupplier(zis, Charsets.UTF_8);
			List<String> srgList = CharStreams.readLines(srgSupplier);
			rawMethodMaps = Maps.newHashMap();
			rawFieldMaps = Maps.newHashMap();
			Builder<String, String> builder = ImmutableBiMap.builder();
			Builder<String, String> mcpBuilder = ImmutableBiMap.builder();
			Splitter splitter = Splitter.on(CharMatcher.anyOf(": ")).omitEmptyStrings().trimResults();
			for (String line : srgList) {
				String[] parts = Iterables.toArray(splitter.split(line), String.class);
				String typ = parts[0];
				if ("CL".equals(typ)) {
					parseClass(builder, parts);
					parseMCPClass(mcpBuilder, parts);
				} else if ("MD".equals(typ)) {
					parseMethod(parts);
				} else if ("FD".equals(typ)) {
					parseField(parts);
				}
			}
			classNameBiMap = builder.build();
			// Special case some mappings for modloader mods
			mcpBuilder.put("BaseMod", "net/minecraft/src/BaseMod");
			mcpBuilder.put("ModLoader", "net/minecraft/src/ModLoader");
			mcpBuilder.put("EntityRendererProxy", "net/minecraft/src/EntityRendererProxy");
			mcpBuilder.put("MLProp", "net/minecraft/src/MLProp");
			mcpBuilder.put("TradeEntry", "net/minecraft/src/TradeEntry");
			mcpNameBiMap = mcpBuilder.build();
		} catch (IOException ioe) {
			Log.severe("An error occurred loading the deobfuscation map data", ioe);
		}
		methodNameMaps = Maps.newHashMapWithExpectedSize(rawMethodMaps.size());
		fieldNameMaps = Maps.newHashMapWithExpectedSize(rawFieldMaps.size());
	}

	public boolean isRemappedClass(String className) {
		className = className.replace('.', '/');
		return classNameBiMap.containsKey(className) || mcpNameBiMap.containsKey(className) || (!classNameBiMap.isEmpty() && className.indexOf('/') == -1);
	}

	private void parseField(String[] parts) {
		String oldSrg = parts[1];
		int lastOld = oldSrg.lastIndexOf('/');
		String cl = oldSrg.substring(0, lastOld);
		String oldName = oldSrg.substring(lastOld + 1);
		String newSrg = parts[2];
		int lastNew = newSrg.lastIndexOf('/');
		String newName = newSrg.substring(lastNew + 1);
		if (!rawFieldMaps.containsKey(cl)) {
			rawFieldMaps.put(cl, Maps.<String, String>newHashMap());
		}
		rawFieldMaps.get(cl).put(oldName + ':' + getFieldType(cl, oldName), newName);
		rawFieldMaps.get(cl).put(oldName + ":null", newName);
	}

	/*
	 * Cache the field descriptions for classes so we don't repeatedly reload the same data again and again
	 */
	private final Map<String, Map<String, String>> fieldDescriptions = Maps.newHashMap();

	private String getFieldType(String owner, String name) {
		if (fieldDescriptions.containsKey(owner)) {
			return fieldDescriptions.get(owner).get(name);
		}
		synchronized (fieldDescriptions) {
			byte[] classBytes = getClassBytes(owner);
			if (classBytes == null) {
				return null;
			}
			ClassReader cr = new ClassReader(classBytes);
			ClassNode classNode = new ClassNode();
			cr.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			Map<String, String> resMap = Maps.newHashMap();
			for (FieldNode fieldNode : classNode.fields) {
				resMap.put(fieldNode.name, fieldNode.desc);
			}
			fieldDescriptions.put(owner, resMap);
			return resMap.get(name);
		}
	}

	private static void parseClass(Builder<String, String> builder, String[] parts) {
		builder.put(parts[1], parts[2]);
	}

	private static void parseMCPClass(Builder<String, String> builder, String[] parts) {
		int clIdx = parts[2].lastIndexOf('/');
		builder.put("net/minecraft/src/" + parts[2].substring(clIdx + 1), parts[2]);
	}

	private void parseMethod(String[] parts) {
		String oldSrg = parts[1];
		int lastOld = oldSrg.lastIndexOf('/');
		String cl = oldSrg.substring(0, lastOld);
		String oldName = oldSrg.substring(lastOld + 1);
		String sig = parts[2];
		String newSrg = parts[3];
		int lastNew = newSrg.lastIndexOf('/');
		String newName = newSrg.substring(lastNew + 1);
		if (!rawMethodMaps.containsKey(cl)) {
			rawMethodMaps.put(cl, Maps.<String, String>newHashMap());
		}
		rawMethodMaps.get(cl).put(oldName + sig, newName);
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		if (classNameBiMap == null || classNameBiMap.isEmpty()) {
			return name;
		}
		Map<String, String> fieldMap = getFieldMap(owner);
		return fieldMap != null && fieldMap.containsKey(name + ':' + desc) ? fieldMap.get(name + ':' + desc) : name;
	}

	@Override
	public String map(String typeName) {
		if (classNameBiMap == null || classNameBiMap.isEmpty()) {
			return typeName;
		}

		int dollarIdx = typeName.indexOf('$');
		String realType = dollarIdx > -1 ? typeName.substring(0, dollarIdx) : typeName;
		String subType = dollarIdx > -1 ? typeName.substring(dollarIdx + 1) : "";

		String result = classNameBiMap.containsKey(realType) ? classNameBiMap.get(realType) : mcpNameBiMap.containsKey(realType) ? mcpNameBiMap.get(realType) : realType;
		result = dollarIdx > -1 ? result + '$' + subType : result;
		//        System.out.printf("Mapping %s=>%s\n",typeName,result);
		return result;
	}

	public String unmap(String typeName) {
		if (classNameBiMap == null || classNameBiMap.isEmpty()) {
			return typeName;
		}
		int dollarIdx = typeName.indexOf('$');
		String realType = dollarIdx > -1 ? typeName.substring(0, dollarIdx) : typeName;
		String subType = dollarIdx > -1 ? typeName.substring(dollarIdx + 1) : "";

		String result = classNameBiMap.containsValue(realType) ? classNameBiMap.inverse().get(realType) : mcpNameBiMap.containsValue(realType) ? mcpNameBiMap.inverse().get(realType) : realType;
		result = dollarIdx > -1 ? result + '$' + subType : result;
		//        System.out.printf("Unmapping %s=>%s\n",typeName,result);
		return result;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		if (classNameBiMap == null || classNameBiMap.isEmpty()) {
			return name;
		}
		Map<String, String> methodMap = getMethodMap(owner);
		String methodDescriptor = name + desc;
		return methodMap != null && methodMap.containsKey(methodDescriptor) ? methodMap.get(methodDescriptor) : name;
	}

	private Map<String, String> getFieldMap(String className) {
		if (!fieldNameMaps.containsKey(className)) {
			findAndMergeSuperMaps(className);
		}
		return fieldNameMaps.get(className);
	}

	private Map<String, String> getMethodMap(String className) {
		if (!methodNameMaps.containsKey(className)) {
			findAndMergeSuperMaps(className);
		}
		return methodNameMaps.get(className);
	}

	private void findAndMergeSuperMaps(String name) {
		byte[] classBytes = getClassBytes(name);
		if (classBytes == null) {
			return;
		}
		ClassReader cr = new ClassReader(classBytes);
		String superName = cr.getSuperName();
		String[] interfaces = cr.getInterfaces();
		if (interfaces == null) {
			interfaces = EMPTY_INTERFACES;
		}
		mergeSuperMaps(name, superName, interfaces);
	}

	void mergeSuperMaps(String name, String superName, String[] interfaces) {
		//        System.out.printf("Computing super maps for %s: %s %s\n", name, superName, Arrays.asList(interfaces));
		if (classNameBiMap == null || classNameBiMap.isEmpty()) {
			return;
		}
		// Skip Object
		if (Strings.isNullOrEmpty(superName)) {
			return;
		}

		List<String> allParents = ImmutableList.<String>builder().add(superName).addAll(Arrays.asList(interfaces)).build();
		// generate maps for all parent objects
		for (String parentThing : allParents) {
			if (!methodNameMaps.containsKey(parentThing)) {
				findAndMergeSuperMaps(parentThing);
			}
		}
		Map<String, String> methodMap = Maps.newHashMap();
		Map<String, String> fieldMap = Maps.newHashMap();
		for (String parentThing : allParents) {
			if (methodNameMaps.containsKey(parentThing)) {
				methodMap.putAll(methodNameMaps.get(parentThing));
			}
			if (fieldNameMaps.containsKey(parentThing)) {
				fieldMap.putAll(fieldNameMaps.get(parentThing));
			}
		}
		if (rawMethodMaps.containsKey(name)) {
			methodMap.putAll(rawMethodMaps.get(name));
		}
		if (rawFieldMaps.containsKey(name)) {
			fieldMap.putAll(rawFieldMaps.get(name));
		}
		methodNameMaps.put(name, ImmutableMap.copyOf(methodMap));
		fieldNameMaps.put(name, ImmutableMap.copyOf(fieldMap));
		//        System.out.printf("Maps: %s %s\n", name, methodMap);
	}

	public static class ZipInputSupplier implements InputSupplier<InputStream> {
		private final ZipFile zipFile;
		private final ZipEntry zipEntry;

		public ZipInputSupplier(ZipFile zip, ZipEntry entry) {
			this.zipFile = zip;
			this.zipEntry = entry;
		}

		@Override
		public InputStream getInput() throws IOException {
			return zipFile.getInputStream(zipEntry);
		}
	}
}
