package me.nallar.tickthreading.util;

import javax.swing.text.NumberFormatter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class TableFormatter {
	public StringBuilder sb = new StringBuilder();
	private List<String> currentHeadings = new ArrayList<String>();
	private List<String> currentData = new ArrayList<String>();
	private Formatter fmt = new Formatter(sb);
	public String splitter = " | ";

	public TableFormatter heading(String text) {
		currentHeadings.add(text);
		return this;
	}

	public TableFormatter row(String data) {
		currentData.add(data);
		return this;
	}

	public TableFormatter row(float data) {
		return row((double) data);
	}

	public TableFormatter row(double data) {
		currentData.add(format(data, 3));
		return this;
	}

	public void finishTable() {
		int rowIndex = 0;
		int rowCount = currentHeadings.size();
		int[] rowLengths = new int[rowCount];
		getMaxLengths(rowLengths, rowIndex, rowCount, currentHeadings);
		getMaxLengths(rowLengths, rowIndex, rowCount, currentData);
		String cSplit = "";
		for (String heading : currentHeadings) {
			sb.append(cSplit);
			fmt.format("%" + (rowLengths[rowIndex % rowCount]) + 's', heading);
			cSplit = splitter;
			rowIndex++;
		}
		sb.append('\n');
		cSplit = "";
		for (String data : currentData) {
			sb.append(cSplit);
			fmt.format("%" + (rowLengths[rowIndex % rowCount]) + 's', data);
			cSplit = splitter;
			rowIndex++;
			if (rowIndex % rowCount == 0) {
				sb.append('\n');
				cSplit = "";
			}
		}
		currentHeadings.clear();
		currentData.clear();
	}

	private static int getMaxLengths(int[] rowLengths, int rowIndex, int rowCount, Iterable<String> stringIterable) {
		for (String data : stringIterable) {
			if (rowLengths[rowIndex % rowCount] < data.length()) {
				rowLengths[rowIndex % rowCount] = data.length();
			}
			rowIndex++;
		}
		return rowIndex;
	}

	private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};

	/*
	 * http://stackoverflow.com/a/10554128/250076
	 */
	private static String format(double val, int precision) {
		StringBuilder sb = new StringBuilder();
		if (val < 0) {
			sb.append('-');
			val = -val;
		}
		int exp = POW10[precision];
		long lval = (long)(val * exp + 0.5);
		sb.append(lval / exp).append('.');
		long fval = lval % exp;
		for (int p = precision - 1; p > 0 && fval < POW10[p]; p--) {
			sb.append('0');
		}
		sb.append(fval);
		return sb.toString();
	}
}
