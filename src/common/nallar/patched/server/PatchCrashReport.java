package nallar.patched.server;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;

public abstract class PatchCrashReport extends CrashReport {
	public PatchCrashReport(String par1Str, Throwable par2Throwable) {
		super(par1Str, par2Throwable);
	}

	@Override
	public CrashReportCategory makeCategoryDepth(String par1Str, int par2) {
		CrashReportCategory var3 = new CrashReportCategory(this, par1Str);

		if (this.field_85059_f) {
			int var4 = var3.func_85073_a(par2);
			StackTraceElement[] var5 = this.cause.getStackTrace();
			StackTraceElement var6 = null;
			StackTraceElement var7 = null;

			if (var5 != null && var5.length - var4 < var5.length && var5.length - var4 >= 0) {
				var6 = var5[var5.length - var4];

				if (var5.length + 1 - var4 < var5.length) {
					var7 = var5[var5.length + 1 - var4];
				}
			}

			this.field_85059_f = var3.func_85069_a(var6, var7);

			if (var4 > 0 && !this.crashReportSections.isEmpty()) {
				CrashReportCategory var8 = (CrashReportCategory) this.crashReportSections.get(this.crashReportSections.size() - 1);
				var8.func_85070_b(var4);
			} else if (var5 != null && var5.length >= var4) {
				this.field_85060_g = new StackTraceElement[var5.length - var4];
				System.arraycopy(var5, 0, this.field_85060_g, 0, this.field_85060_g.length);
			} else {
				this.field_85059_f = false;
			}
		}

		this.crashReportSections.add(var3);
		return var3;
	}
}
