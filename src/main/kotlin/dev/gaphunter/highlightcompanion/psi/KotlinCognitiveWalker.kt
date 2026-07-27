package dev.gaphunter.highlightcompanion.psi

import com.intellij.psi.tree.IElementType
import dev.gaphunter.highlightcompanion.complexity.BoolExpr
import dev.gaphunter.highlightcompanion.complexity.ControlNode
import dev.gaphunter.highlightcompanion.complexity.LogicalOp
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression

/**
 * Kotlin counterpart of JavaCognitiveWalker. Same documented tradeoff on
 * recursion detection (simple-name match against the enclosing function,
 * no resolve). Unlike Java, Kotlin's control-flow constructs are
 * expressions, not a separate statement grammar, so one dispatcher (walk)
 * handles both "used as a bare statement" and "used as a value" positions
 * uniformly - a `return if (x) a else b` is scored exactly like a bare
 * `if` used as a statement.
 *
 * Known, deliberate gap (matches JavaCognitiveWalker): a control-flow
 * expression buried inside a larger expression - e.g. an if passed as a
 * function argument, or one inside a lambda body - falls back to the
 * generic recursive-call scan instead of being scored structurally. Only
 * direct positions (block statements, return values, property
 * initializers, branch bodies) get full structural treatment. See README.
 */
object KotlinCognitiveWalker {

    fun buildBody(function: KtNamedFunction): List<ControlNode> {
        val methodName = function.name ?: return emptyList()
        val block = function.bodyBlockExpression
        if (block != null) return walkBlockChildren(block, methodName)
        return walk(function.bodyExpression, methodName)
    }

    private fun walkBlockChildren(block: KtBlockExpression, methodName: String): List<ControlNode> =
        block.children.flatMap { child ->
            when (child) {
                is KtProperty -> walk(child.initializer, methodName)
                is KtExpression -> walk(child, methodName)
                else -> emptyList()
            }
        }

    private fun walk(expr: KtExpression?, methodName: String): List<ControlNode> {
        if (expr == null) return emptyList()
        return when (expr) {
            is KtLabeledExpression -> walk(expr.baseExpression, methodName)

            is KtBlockExpression -> walkBlockChildren(expr, methodName)

            is KtIfExpression -> recursiveCallsIn(expr.condition, methodName) + walkIf(expr, methodName)

            is KtWhileExpression -> recursiveCallsIn(expr.condition, methodName) +
                ControlNode.Loop(extractBoolExpr(expr.condition), walk(expr.body, methodName))

            is KtDoWhileExpression -> recursiveCallsIn(expr.condition, methodName) +
                ControlNode.Loop(extractBoolExpr(expr.condition), walk(expr.body, methodName))

            is KtForExpression -> recursiveCallsIn(expr.loopRange, methodName) +
                ControlNode.Loop(null, walk(expr.body, methodName))

            is KtWhenExpression -> walkWhen(expr, methodName)

            is KtTryExpression -> walkTry(expr, methodName)

            is KtBreakExpression -> if (expr.getLabelName() != null) listOf(ControlNode.LabeledJump(true)) else emptyList()

            is KtContinueExpression -> if (expr.getLabelName() != null) listOf(ControlNode.LabeledJump(true)) else emptyList()

            is KtReturnExpression -> walk(expr.returnedExpression, methodName)

            // Generic fallback: plain calls, assignments, dot-qualified calls, throw
            // expressions, etc. Scanned for recursive calls only, not walked structurally.
            else -> recursiveCallsIn(expr, methodName)
        }
    }

    private fun walkIf(expr: KtIfExpression, methodName: String): ControlNode.If {
        val condition = extractBoolExpr(expr.condition)
        val thenBranch = walk(expr.then, methodName)
        return when (val elseBranch = expr.`else`) {
            null -> ControlNode.If(condition, thenBranch)
            is KtIfExpression -> ControlNode.If(condition, thenBranch, elseIf = walkIf(elseBranch, methodName))
            else -> ControlNode.If(condition, thenBranch, elseBody = walk(elseBranch, methodName))
        }
    }

    private fun walkWhen(expr: KtWhenExpression, methodName: String): List<ControlNode> {
        val recursionInSubject = recursiveCallsIn(expr.subjectExpression, methodName)
        val entryNodes = expr.entries.flatMap { entry -> walk(entry.expression, methodName) }
        return recursionInSubject + ControlNode.Switch(entryNodes)
    }

    private fun walkTry(expr: KtTryExpression, methodName: String): List<ControlNode> {
        val tryNodes = walkBlockChildren(expr.tryBlock, methodName)
        val catchNodes = expr.catchClauses.map { clause ->
            ControlNode.Catch(walk(clause.catchBody, methodName))
        }
        val finallyBlock = expr.finallyBlock?.finalExpression
        val finallyNodes = if (finallyBlock != null) walkBlockChildren(finallyBlock, methodName) else emptyList()
        return tryNodes + catchNodes + finallyNodes
    }

    private fun recursiveCallsIn(expr: KtExpression?, methodName: String): List<ControlNode.RecursiveCall> {
        if (expr == null) return emptyList()
        val calls = mutableListOf<ControlNode.RecursiveCall>()
        expr.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val name = (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                if (name == methodName) {
                    calls.add(ControlNode.RecursiveCall(methodName))
                }
            }
        })
        return calls
    }

    private fun extractBoolExpr(expr: KtExpression?): BoolExpr? {
        val ops = mutableListOf<LogicalOp>()
        collectLogicalOps(expr, ops)
        return if (ops.isEmpty()) null else BoolExpr(ops)
    }

    private fun collectLogicalOps(expr: KtExpression?, sink: MutableList<LogicalOp>) {
        val unwrapped = unwrap(expr) ?: return
        when (unwrapped) {
            is KtBinaryExpression -> {
                val op = logicalOpOf(unwrapped.operationToken)
                if (op != null) {
                    collectLogicalOps(unwrapped.left, sink)
                    sink.add(op)
                    collectLogicalOps(unwrapped.right, sink)
                }
            }
            is KtPrefixExpression -> if (unwrapped.operationToken == KtTokens.EXCL) {
                collectLogicalOps(unwrapped.baseExpression, sink)
            }
            else -> {}
        }
    }

    private fun unwrap(expr: KtExpression?): KtExpression? = when (expr) {
        is KtParenthesizedExpression -> unwrap(expr.expression)
        else -> expr
    }

    private fun logicalOpOf(tokenType: IElementType?): LogicalOp? = when (tokenType) {
        KtTokens.ANDAND -> LogicalOp.AND
        KtTokens.OROR -> LogicalOp.OR
        else -> null
    }
}
