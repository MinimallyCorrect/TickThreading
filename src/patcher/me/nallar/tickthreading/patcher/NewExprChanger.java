package patcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.Cast;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.Handler;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;
import me.nallar.tickthreading.Log;

/**
 * Replaces new instances of one object with others
 */
public class NewExprChanger {
	private final Map<String, List<String>> replacementClasses;

	public NewExprChanger(Map replacementClasses) {
		this.replacementClasses = replacementClasses;
	}

	public void patchClass(CtClass ctClass) throws CannotCompileException {
		for (CtMethod method : ctClass.getDeclaredMethods()) {
			final Map<Integer, String> newExprType = new HashMap<Integer, String>();
			method.instrument(new ExprEditor() {
				NewExpr lastNewExpr;
				int newPos = 0;

				@Override
				public void edit(NewExpr e) {
					lastNewExpr = null;
					newPos++;
					if (replacementClasses.containsKey(e.getClassName())) {
						lastNewExpr = e;
					}
				}

				@Override
				public void edit(FieldAccess e) {
					NewExpr myLastNewExpr = lastNewExpr;
					lastNewExpr = null;
					if (myLastNewExpr != null) {
						Log.fine("(" + myLastNewExpr.getSignature() + ") " + myLastNewExpr.getClassName() + " at " + myLastNewExpr.getFileName() + ":" + myLastNewExpr.getLineNumber() + ":" + newPos);
						newExprType.put(newPos, signatureToName(e.getSignature()));
					}
				}

				@Override
				public void edit(MethodCall e) {
					lastNewExpr = null;
				}

				@Override
				public void edit(NewArray e) {
					lastNewExpr = null;
				}

				@Override
				public void edit(Cast e) {
					lastNewExpr = null;
				}

				@Override
				public void edit(Instanceof e) {
					lastNewExpr = null;
				}

				@Override
				public void edit(Handler e) {
					lastNewExpr = null;
				}

				@Override
				public void edit(ConstructorCall e) {
					lastNewExpr = null;
				}
			});
			method.instrument(new ExprEditor() {
				int newPos = 0;

				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					newPos++;
					try {
						Log.fine(e.getFileName() + ":" + e.getLineNumber() + ", pos: " + newPos);
						if (newExprType.containsKey(newPos)) {
							String replacementType = null, assignedType = newExprType.get(newPos);
							Log.fine(assignedType + " at " + e.getFileName() + ":" + e.getLineNumber());
							Class<?> assignTo = Class.forName(assignedType);
							for (String replacementClass : replacementClasses.get(e.getClassName())) {
								if (assignTo.isAssignableFrom(Class.forName(replacementClass))) {
									replacementType = replacementClass;
									break;
								}
							}
							if (replacementType == null) {
								return;
							}
							String block = "{$_=new " + replacementType + "($$);}";
							Log.fine("Replaced with " + block + ", " + replacementType.length() + ":" + assignedType.length());
							e.replace(block);
						}
					} catch (ClassNotFoundException el) {
						el.printStackTrace();
					}
				}
			});
		}
		try {
			ctClass.writeFile();
		} catch (NotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String signatureToName(String signature) {
		//noinspection HardcodedFileSeparator
		return signature.substring(1, signature.length() - 1).replace("/", ".");
	}
}
