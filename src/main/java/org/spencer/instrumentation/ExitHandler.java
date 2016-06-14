package org.spencer.instrumentation;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.TryCatchBlockSorter;

import java.util.HashSet;
import java.util.function.BiConsumer;

/**
 * A method visitor that inserts source code ({@link onExit}) every time a method exits.
 * This also deals with uncaught exceptions by wrapping all instrumented methods in a
 * try catch block that executes the code generated by {@code onExit} and then rethrows 
 * he exception. For technical reasons, this class does not instrument constructors.
 * 
 * @author Stephan Brandauer
 *
 */
public final class ExitHandler extends AdviceAdapter implements Opcodes {

	private final Label startTryLabel = new Label();
	private final Label endTryLabel = new Label();
	private final Label startHandlerLabel = new Label();
	private final Label endHandlerLabel = new Label();
	
	private final BiConsumer<ExitHandler,String> onExit;
	private int lastVisitedLine;
	private final HashSet<Integer> exitedOnLines = new HashSet<>();

	public static MethodVisitor mk(final MethodVisitor mv, final int access,
			final String name, final String desc, final String signature,
			final String[] exceptions, BiConsumer<ExitHandler, String> onExit) {
		
		return new TryCatchBlockSorter(
				new ExitHandler(mv, access, name, desc, signature, exceptions, onExit), 
				access,
				name,
				desc,
				signature,
				exceptions);
	}
	
	private ExitHandler(final MethodVisitor mv, final int access,
			final String name, final String desc, final String signature,
			final String[] exceptions, BiConsumer<ExitHandler, String> onExit) {
		super(ASM5, mv, access,
				name, desc);
		this.onExit = onExit;
	}

	@Override
	public void visitCode() {
		super.visitCode();
		super.visitLabel(startTryLabel);
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.tree.MethodNode#visitInsn(int)
	 */
	@Override
	public void visitInsn(int opcode) {
		switch (opcode) {
		case RETURN:
		case IRETURN:
		case FRETURN:
		case ARETURN:
		case LRETURN:
		case DRETURN:
			onMethodExit(opcode);
			break;
		}
		super.visitInsn(opcode);
	}
	
	@Override
	public void visitLineNumber(int line, Label start) {
		this.lastVisitedLine = line;
		super.visitLineNumber(line, start);
	}

	@Override
	public void onMethodExit(int opcode) {
		if (opcode == ATHROW) {
			//We'll catch this in the handler if needed!
			return;
		}
		if (!this.exitedOnLines.contains(this.lastVisitedLine)) {
			this.onExit.accept(this, "normal exit - "+opcode+" "+this.lastVisitedLine);
			this.exitedOnLines.add(this.lastVisitedLine);
		}
	}

	@Override
	public void visitMaxs(int locals, int maxs) {
		visitLabel(endTryLabel);
		visitJumpInsn(GOTO, endHandlerLabel);
		visitLabel(startHandlerLabel);
		this.onExit.accept(this, "exception being thrown");
		visitInsn(ATHROW);
		visitLabel(endHandlerLabel);
		super.visitMaxs(locals, maxs);
	}
}