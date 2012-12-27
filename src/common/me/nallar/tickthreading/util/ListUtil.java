package me.nallar.tickthreading.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListUtil {
	public static List<String> split(String input) {
		return split(input, ",");
	}

	public static List<String> split(String input, String delimiter) {
		if (input == null || input.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<String>(Arrays.asList(input.split(delimiter)));
	}
}
