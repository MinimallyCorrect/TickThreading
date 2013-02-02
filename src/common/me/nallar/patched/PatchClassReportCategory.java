package me.nallar.patched;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;

public abstract class PatchClassReportCategory extends CrashReportCategory {
	public PatchClassReportCategory(CrashReport par1CrashReport, String par2Str) {
		super(par1CrashReport, par2Str);
	}

	@Override
	public int func_85073_a(int par1) {
		StackTraceElement[] var2 = Thread.currentThread().getStackTrace();
		this.stackTrace = new StackTraceElement[var2.length - 7 - par1];
		System.arraycopy(var2, 7 + par1, this.stackTrace, 0, this.stackTrace.length);
		return this.stackTrace.length;
	}
}
