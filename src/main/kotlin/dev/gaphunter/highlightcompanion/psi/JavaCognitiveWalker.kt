package dev.gaphunter.highlightcompanion.psi

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiBreakStatement
import com.intellij.psi.PsiContinueStatement
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLabeledStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiPrefixExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiSwitchLabeledRuleStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.PsiYieldStatement
import com.intellij.psi.tree.IElementType
import dev.gaphunter.highlightcompanion.complexity.BoolExpr
import dev.gaphunter.highlightcompanion.complexity.ControlNode
import dev.gaphunter.highlightcompanion.complexity.LogicalOp

/**
 * Translates a real Java method body into the PSI-free ControlNode tree
 * that CognitiveComplexityCalculator scores. Purely syntactic: recursion
 * detection compares a call's simple method name against the enclosing
 * method's name (no PsiReference.resolve()), so it can be fooled by an
 * unrelated same-named method in a different class, or miss a call made
 * through an interface/super reference with a different apparent name.
 * Documented tradeoff, not an oversight - resolving every call would defeat
 * the point of keeping this walk cheap enough to run on every edit.
 */
object JavaCognitiveWalker {

    fun buildBody(method: PsiMethod): List<ControlNode> {
        val block = method.body ?: return emptyList()
        return walkStatements(block.statements.toList(), method.name)
    }

    private fun walkStatements(statements: List<PsiStatement>, methodName: String): List<ControlNode> =
        statements.flatMap { walkStatement(it, methodName) }

    private fun walkBody(statement: PsiStatement?, methodName: String): List<ControlNode> = when (statement) {
        null -> emptyList()
        is PsiBlockStatement -> walkStatements(statement.codeBlock.statements.toList(), methodName)
        else -> walkStatement(statement, methodName)
    }

    private fun walkStatement(statement: PsiStatement, methodName: String): List<ControlNode> = when (statement) {
        is PsiLabeledStatement -> statement.statement?.let { walkStatement(it, methodName) } ?: emptyList()

        is PsiBlockStatement -> walkStatements(statement.codeBlock.statements.toList(), methodName)

        is PsiIfStatement -> recursiveCallsIn(statement.condition, methodName) + walkIf(statement, methodName)

        is PsiWhileStatement -> recursiveCallsIn(statement.condition, methodName) +
            ControlNode.Loop(extractBoolExpr(statement.condition), walkBody(statement.body, methodName))

        is PsiDoWhileStatement -> recursiveCallsIn(statement.condition, methodName) +
            ControlNode.Loop(extractBoolExpr(statement.condition), walkBody(statement.body, methodName))

        is PsiForStatement -> recursiveCallsIn(statement.condition, methodName) +
            ControlNode.Loop(extractBoolExpr(statement.condition), walkBody(statement.body, methodName))

        is PsiForeachStatement -> recursiveCallsIn(statement.iteratedValue, methodName) +
            ControlNode.Loop(null, walkBody(statement.body, methodName))

        is PsiSwitchStatement -> walkSwitch(statement, methodName)

        is PsiSwitchLabeledRuleStatement -> walkBody(statement.body, methodName)

        is PsiTryStatement -> walkTry(statement, methodName)

        is PsiBreakStatement -> if (statement.labelIdentifier != null) listOf(ControlNode.LabeledJump(true)) else emptyList()

        is PsiContinueStatement -> if (statement.labelIdentifier != null) listOf(ControlNode.LabeledJump(true)) else emptyList()

        is PsiExpressionStatement -> recursiveCallsIn(statement.expression, methodName)

        is PsiReturnStatement -> recursiveCallsIn(statement.returnValue, methodName)

        is PsiThrowStatement -> recursiveCallsIn(statement.exception, methodName)

        is PsiYieldStatement -> recursiveCallsIn(statement.expression, methodName)

        is PsiDeclarationStatement -> statement.declaredElements.filterIsInstance<PsiLocalVariable>()
            .flatMap { recursiveCallsIn(it.initializer, methodName) }

        else -> emptyList()
    }

    private fun walkIf(statement: PsiIfStatement, methodName: String): ControlNode.If {
        val condition = extractBoolExpr(statement.condition)
        val thenBranch = walkBody(statement.thenBranch, methodName)
        return when (val elseBranch = statement.elseBranch) {
            null -> ControlNode.If(condition, thenBranch)
            // Real "else if" (no braces) chains at the same nesting level.
            is PsiIfStatement -> ControlNode.If(condition, thenBranch, elseIf = walkIf(elseBranch, methodName))
            else -> ControlNode.If(condition, thenBranch, elseBody = walkBody(elseBranch, methodName))
        }
    }

    private fun walkSwitch(statement: PsiSwitchStatement, methodName: String): List<ControlNode> {
        val recursionInSelector = recursiveCallsIn(statement.expression, methodName)
        val caseNodes = statement.body?.statements?.toList()?.let { walkStatements(it, methodName) } ?: emptyList()
        return recursionInSelector + ControlNode.Switch(caseNodes)
    }

    private fun walkTry(statement: PsiTryStatement, methodName: String): List<ControlNode> {
        val tryNodes = statement.tryBlock?.statements?.toList()?.let { walkStatements(it, methodName) } ?: emptyList()
        val catchNodes = statement.catchSections.map { section ->
            ControlNode.Catch(section.catchBlock?.statements?.toList()?.let { walkStatements(it, methodName) } ?: emptyList())
        }
        val finallyNodes = statement.finallyBlock?.statements?.toList()?.let { walkStatements(it, methodName) } ?: emptyList()
        return tryNodes + catchNodes + finallyNodes
    }

    private fun recursiveCallsIn(expr: PsiExpression?, methodName: String): List<ControlNode.RecursiveCall> {
        if (expr == null) return emptyList()
        val calls = mutableListOf<ControlNode.RecursiveCall>()
        expr.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                if (expression.methodExpression.referenceName == methodName) {
                    calls.add(ControlNode.RecursiveCall(methodName))
                }
            }
        })
        return calls
    }

    private fun extractBoolExpr(expr: PsiExpression?): BoolExpr? {
        val ops = mutableListOf<LogicalOp>()
        collectLogicalOps(expr, ops)
        return if (ops.isEmpty()) null else BoolExpr(ops)
    }

    private fun collectLogicalOps(expr: PsiExpression?, sink: MutableList<LogicalOp>) {
        val unwrapped = unwrap(expr) ?: return
        when (unwrapped) {
            is PsiPolyadicExpression -> {
                val op = logicalOpOf(unwrapped.operationTokenType)
                val operands = unwrapped.operands
                if (op != null && operands.isNotEmpty()) {
                    collectLogicalOps(operands[0], sink)
                    for (i in 1 until operands.size) {
                        sink.add(op)
                        collectLogicalOps(operands[i], sink)
                    }
                }
            }
            is PsiPrefixExpression -> if (unwrapped.operationTokenType == JavaTokenType.EXCL) {
                collectLogicalOps(unwrapped.operand, sink)
            }
            else -> {}
        }
    }

    private fun unwrap(expr: PsiExpression?): PsiExpression? = when (expr) {
        is PsiParenthesizedExpression -> unwrap(expr.expression)
        else -> expr
    }

    private fun logicalOpOf(tokenType: IElementType?): LogicalOp? = when (tokenType) {
        JavaTokenType.ANDAND -> LogicalOp.AND
        JavaTokenType.OROR -> LogicalOp.OR
        else -> null
    }
}
