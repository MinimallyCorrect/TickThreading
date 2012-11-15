package me.nallar.tickthreading.patcher;

import java.io.IOException;
import java.util.ArrayList;
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
	public void fixUnsafeCollections(CtClass ctClass) throws CannotCompileException {
		final Map<String, List<String>> replacementClasses = new HashMap<String, List<String>>();

		for (CtMethod method : ctClass.getDeclaredMethods()) {
			final List<String> newExprType = new ArrayList<String>();
			method.instrument(new ExprEditor() {
				NewExpr lastNewExpr;

				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					lastNewExpr = null;
					if (replacementClasses.containsKey(e.getClassName())) {
						System.out.println("(" + e.getSignature() + ") " + e.getClassName() + " at " + e.getFileName() + ":" + e.getLineNumber());
						lastNewExpr = e;
						newExprType.add("");
					}
				}

				@Override
				public void edit(FieldAccess e) throws CannotCompileException {
					NewExpr myLastNewExpr = lastNewExpr;
					lastNewExpr = null;
					if (myLastNewExpr != null) {
						newExprType.add(newExprType.size() - 1, signatureToName(e.getSignature()));
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
					if (e.getClassName().equals("java.util.HashMap") && newExprType.size() > 0) {
						String hashMapType, assignedType = newExprType.get(0);
						newExprType.remove(0);
						System.out.println(assignedType + " at " + e.getFileName() + ":" + e.getLineNumber());
						if (assignedType.equals("java.util.Map")) {
							hashMapType = "java.util.concurrent.ConcurrentHashMap";
						} else if (assignedType.equals("java.util.HashMap")) {
							hashMapType = "me.nallar.tickthreading.concurrentcollections.CHashMap";
						} else {
							return;
						}
						String block = "{$_=new " + hashMapType + "($$);}";
						System.out.println("Replaced with " + block);
						e.replace(block);
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
