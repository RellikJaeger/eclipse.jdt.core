/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.ast;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.flow.*;
import org.eclipse.jdt.internal.compiler.lookup.*;

public class YieldStatement extends BranchStatement {

	public Expression expression;
	public SwitchExpression switchExpression;
	/**
	 * @noreference This field is not intended to be referenced by clients.
	 */
	public boolean isImplicit;

public YieldStatement(Expression exp, int sourceStart, int e) {
	super(null, sourceStart, e);
	this.expression = exp;
}
@Override
public FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo) {
	// this.switchExpression != null && this.expression != null true here.

	// here requires to generate a sequence of finally blocks invocations depending corresponding
	// to each of the traversed try statements, so that execution will terminate properly.


	// lookup the null label, this should answer the returnContext
	FlowContext targetContext = flowContext.getTargetContextForDefaultBreak();

	flowInfo = this.expression.analyseCode(currentScope, flowContext, flowInfo);
	this.expression.checkNPEbyUnboxing(currentScope, flowContext, flowInfo);
	if (flowInfo.reachMode() == FlowInfo.REACHABLE && currentScope.compilerOptions().isAnnotationBasedNullAnalysisEnabled)
		checkAgainstNullAnnotation(currentScope, flowContext, flowInfo, this.expression);

	targetContext.recordAbruptExit();
	targetContext.expireNullCheckedFieldInfo();

	this.initStateIndex =
		currentScope.methodScope().recordInitializationStates(flowInfo);

	this.targetLabel = targetContext.breakLabel();
	FlowContext traversedContext = flowContext;
	int subCount = 0;
	this.subroutines = new SubRoutineStatement[5];

	do {
		SubRoutineStatement sub;
		if ((sub = traversedContext.subroutine()) != null) {
			if (subCount == this.subroutines.length) {
				System.arraycopy(this.subroutines, 0, (this.subroutines = new SubRoutineStatement[subCount*2]), 0, subCount); // grow
			}
			this.subroutines[subCount++] = sub;
			if (sub.isSubRoutineEscaping()) {
				break;
			}
		}
		traversedContext.recordReturnFrom(flowInfo.unconditionalInits());
		traversedContext.recordBreakTo(targetContext);

		if (traversedContext instanceof InsideSubRoutineFlowContext) {
			ASTNode node = traversedContext.associatedNode;
			if (node instanceof TryStatement) {
				TryStatement tryStatement = (TryStatement) node;
				flowInfo.addInitializationsFrom(tryStatement.subRoutineInits); // collect inits
			}
		} else if (traversedContext == targetContext) {
			// only record break info once accumulated through subroutines, and only against target context
			targetContext.recordBreakFrom(flowInfo);
			break;
		}
	} while ((traversedContext = traversedContext.getLocalParent()) != null);

	// resize subroutines
	if (subCount != this.subroutines.length) {
		System.arraycopy(this.subroutines, 0, (this.subroutines = new SubRoutineStatement[subCount]), 0, subCount);
	}
	return FlowInfo.DEAD_END;
}
@Override
protected void generateExpressionResultCode(BlockScope currentScope, CodeStream codeStream) {
	this.expression.generateCode(currentScope, codeStream, this.switchExpression != null);
}
@Override
protected void adjustStackSize(BlockScope currentScope, CodeStream codeStream) {
	if (this.label == null && this.expression != null && this.switchExpression != null) {
		TypeBinding postConversionType = this.expression.postConversionType(currentScope);
		switch(postConversionType.id) {
			case TypeIds.T_long :
			case TypeIds.T_double :
				codeStream.decrStackSize(2);
				break;
			case TypeIds.T_void :
				break;
			default :
				codeStream.decrStackSize(1);
				break;
		}
	}
}
@Override
public void resolve(BlockScope scope) {
	// METHOD IN WORKS - INCOMPLETE
	super.resolve(scope);
//	if (this.expression == null)
//	currentScope.problemReporter().switchExpressionYieldMissingExpression(this);
//	
	if (this.switchExpression == null) {
		if (scope.compilerOptions().sourceLevel >= ClassFileConstants.JDK13) {
			if (scope.compilerOptions().enablePreviewFeatures) {
				scope.problemReporter().switchExpressionsYieldOutsideSwitchExpression(this);
			} else {
				scope.problemReporter().switchExpressionsYieldIllegalStatement(this);
			}
		}
	}
	if  (this.expression != null) {
		this.expression.resolveType(scope);
	}
}

@Override
public TypeBinding resolveExpressionType(BlockScope scope) {
	return this.expression != null ? this.expression.resolveType(scope) : null;
}

@Override
public StringBuffer printStatement(int tab, StringBuffer output) {
	printIndent(tab, output).append("yield"); //$NON-NLS-1$
	if (this.expression != null) {
		output.append(' ');
		this.expression.printExpression(tab, output);
	}
	return output.append(';');
}

@Override
public void traverse(ASTVisitor visitor, BlockScope blockscope) {
	if (visitor.visit(this, blockscope)) {
		if (this.expression != null)
			this.expression.traverse(visitor, blockscope);
	}
	visitor.endVisit(this, blockscope);
}
@Override
public boolean doesNotCompleteNormally() {
	return true;
}
}
