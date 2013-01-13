package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;

public abstract class PatchClassReportCategory extends CrashReportCategory {
	public PatchClassReportCategory(CrashReport par1CrashReport, String par2Str) {
		super(par1CrashReport, par2Str);
	}

	@Override
	public int func_85073_a(int par1) {
		StackTraceElement[] var2 = Thread.currentThread().getStackTrace();
		this.field_85075_d = new StackTraceElement[var2.length - 7 - par1];
		System.arraycopy(var2, 7 + par1, this.field_85075_d, 0, this.field_85075_d.length);
		return this.field_85075_d.length;
	}
}
