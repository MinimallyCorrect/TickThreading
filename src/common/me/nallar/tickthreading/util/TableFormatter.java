package me.nallar.tickthreading.util;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.util.StringFillers.StringFiller;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;

public class TableFormatter {
	public final StringFiller stringFiller;
	public final StringBuilder sb = new StringBuilder();
	private final List<String> currentHeadings = new ArrayList<String>();
	private final List<String> currentData = new ArrayList<String>();
	public String splitter = " | ";
	public String headingSplitter = " | ";
	public String headingColour = "";
	public String rowColour = "";

	public TableFormatter(ICommandSender commandSender) {
		boolean chat = commandSender instanceof Entity;
		stringFiller = chat ? StringFiller.CHAT : StringFiller.FIXED_WIDTH;
		if (chat) {
			splitter = " " + ChatFormat.YELLOW + '|' + ChatFormat.RESET + ' ';
			headingSplitter = splitter;
			headingColour = ChatFormat.DARK_GREEN + "";
		}
	}

	public TableFormatter(StringFiller stringFiller) {
		this.stringFiller = stringFiller;
	}

	public TableFormatter heading(String text) {
		currentHeadings.add(text);
		return this;
	}

	public TableFormatter row(String data) {
		currentData.add(data);
		return this;
	}

	public TableFormatter row(Object data) {
		return row(String.valueOf(data));
	}

	public TableFormatter row(int data) {
		return row(String.valueOf(data));
	}

	public TableFormatter row(double data) {
		currentData.add(formatDoubleWithPrecision(data, 3));
		return this;
	}

	public void finishTable() {
		int rowIndex = 0;
		int rowCount = currentHeadings.size();
		double[] rowLengths = new double[rowCount];
		getMaxLengths(rowLengths, rowIndex, rowCount, currentHeadings);
		getMaxLengths(rowLengths, rowIndex, rowCount, currentData);
		String cSplit = "";
		for (String heading : currentHeadings) {
			sb.append(cSplit).append(headingColour).append(stringFiller.fill(heading, rowLengths[rowIndex % rowCount]));
			cSplit = headingSplitter;
			rowIndex++;
		}
		sb.append('\n');
		cSplit = "";
		rowIndex = 0;
		for (String data : currentData) {
			sb.append(cSplit).append(rowColour).append(stringFiller.fill(data, rowLengths[rowIndex % rowCount]));
			cSplit = splitter;
			rowIndex++;
			if (rowIndex % rowCount == 0 && rowIndex != currentData.size()) {
				sb.append('\n');
				cSplit = "";
			}
		}
		currentHeadings.clear();
		currentData.clear();
	}

	private int getMaxLengths(double[] rowLengths, int rowIndex, int rowCount, Iterable<String> stringIterable) {
		for (String data : stringIterable) {
			double length = stringFiller.getLength(data);
			if (rowLengths[rowIndex % rowCount] < length) {
				rowLengths[rowIndex % rowCount] = length;
			}
			rowIndex++;
		}
		return rowIndex;
	}

	private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};

	/*
	 * http://stackoverflow.com/a/10554128/250076
	 */
	public static String formatDoubleWithPrecision(double val, int precision) {
		StringBuilder sb = new StringBuilder();
		if (val < 0) {
			sb.append('-');
			val = -val;
		}
		int exp = POW10[precision];
		long lval = (long) (val * exp + 0.5);
		sb.append(lval / exp).append('.');
		long fval = lval % exp;
		for (int p = precision - 1; p > 0 && fval < POW10[p]; p--) {
			sb.append('0');
		}
		sb.append(fval);
		return sb.toString();
	}

	@Override
	public String toString() {
		return sb.toString();
	}
}
