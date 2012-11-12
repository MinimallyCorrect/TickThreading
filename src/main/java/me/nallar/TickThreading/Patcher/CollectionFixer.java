package me.nallar.tickthreading.patcher;

import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

/**
 * Finds non-concurrent collections, determines whether they should be changed,
 * and automatically fixes if possible.
 */
public class CollectionFixer {
	ClassPool pool;

	public CollectionFixer() {
		this(ClassPool.getDefault());
	}

	public CollectionFixer(ClassPool pool) {
		this.pool = pool;
	}

	public void fixUnsafeCollections(CtClass ctClass) throws CannotCompileException {
		for (CtMethod method : ctClass.getDeclaredMethods()) {
			method.instrument(new ExprEditor() {
				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					if (e.getClassName().equals("java.util.HashMap")) {
						System.out.println("(" + e.getSignature() + ") " + e.getClassName() + " at " + e.getFileName() + ":" + e.getLineNumber());
						String hashMapType = true ? "me.nallar.tickthreading.concurrentcollections.CHashMap" : "java.util.concurrent.ConcurrentHashMap";
						// TODO Detect if Map or HashMap is type of local variable
						String block = "{$_=($r)new " + hashMapType + "($$);}";
						e.replace(block);
					}
				}
			});
		}
		try {
			ctClass.writeFile();
		} catch (NotFoundException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		} catch (IOException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
	}
}
