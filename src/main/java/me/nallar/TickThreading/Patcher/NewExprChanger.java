package me.nallar.tickthreading.patcher;

import java.io.IOException;
import java.lang.reflect.Field;
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

/**
 * Replaces new instances of one object with others
 */
public class NewExprChanger {
	Map<String, List<String>> replacementClasses;
	final Field positionField;

	public NewExprChanger(Map replacementClasses) throws NoSuchFieldException {
		positionField = NewExpr.class.getDeclaredField("newPos");
		positionField.setAccessible(true);
		this.replacementClasses = replacementClasses;
	}

	public void patchClass(CtClass ctClass) throws CannotCompileException {
		for (CtMethod method : ctClass.getDeclaredMethods()) {
			final Map<Integer, String> newExprType = new HashMap<Integer, String>();
			method.instrument(new ExprEditor() {
				NewExpr lastNewExpr;

				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					lastNewExpr = null;
					if (replacementClasses.containsKey(e.getClassName())) {
						System.out.println("(" + e.getSignature() + ") " + e.getClassName() + " at " + e.getFileName() + ":" + e.getLineNumber());
						lastNewExpr = e;
					}
				}

				@Override
				public void edit(FieldAccess e) throws CannotCompileException {
					NewExpr myLastNewExpr = lastNewExpr;
					lastNewExpr = null;
					if (myLastNewExpr != null) {
						try {
							newExprType.put((Integer) positionField.get(myLastNewExpr), signatureToName(e.getSignature()));
						} catch (IllegalAccessException e1) {
							e1.printStackTrace();
							//This should never happen.
						}
					}
				}

				@Override
				public void edit(MethodCall e) throws CannotCompileException {
					lastNewExpr = null;
				}

				@Override
				public void edit(NewArray e) throws CannotCompileException {
					lastNewExpr = null;
				}

				@Override
				public void edit(Cast e) throws CannotCompileException {
					lastNewExpr = null;
				}

				@Override
				public void edit(Instanceof e) throws CannotCompileException {
					lastNewExpr = null;
				}

				@Override
				public void edit(Handler e) throws CannotCompileException {
					lastNewExpr = null;
				}

				@Override
				public void edit(ConstructorCall e) throws CannotCompileException {
					lastNewExpr = null;
				}
			});
			method.instrument(new ExprEditor() {
				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					int newPos;
					try {
						newPos = (Integer) positionField.get(e);
					} catch (IllegalAccessException e1) {
						e1.printStackTrace();
						//This should never happen
						return;
					}
					try {
						if (newExprType.containsKey(newPos)) {
							String replacementType = null, assignedType = newExprType.get(newPos);
							System.out.println(assignedType + " at " + e.getFileName() + ":" + e.getLineNumber());
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
							System.out.println("Replaced with " + block);
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
		return signature.substring(1, signature.length() - 1).replace("/", ".");
	}
}
