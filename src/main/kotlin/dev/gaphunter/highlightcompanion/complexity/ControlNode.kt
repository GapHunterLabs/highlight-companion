package dev.gaphunter.highlightcompanion.complexity

/**
 * Structural summary of one function/method body's control flow, stripped
 * of any PSI/AST dependency. Java and Kotlin PSI walkers each translate a
 * real function body into this tree; CognitiveComplexityCalculator scores
 * the tree. Keeping the two apart means the scoring rules can be unit
 * tested without booting the IntelliJ platform at all.
 */
sealed class ControlNode {
    data class If(
        val condition: BoolExpr? = null,
        val then: List<ControlNode> = emptyList(),
        // "else if" is represented by chaining into another If at the same
        // nesting level (see CognitiveComplexityCalculator) rather than
        // nesting elseBody one level deeper, matching the SonarSource rule
        // that an else-if chain doesn't compound nesting for each rung.
        val elseIf: If? = null,
        // A plain "else" (no further condition). Mutually exclusive with
        // elseIf: a real if-statement has at most one of the two.
        val elseBody: List<ControlNode>? = null,
    ) : ControlNode()

    data class Loop(
        val condition: BoolExpr? = null,
        val body: List<ControlNode> = emptyList(),
    ) : ControlNode()

    /** All case/when bodies flattened into one list: case labels themselves never add complexity. */
    data class Switch(val body: List<ControlNode> = emptyList()) : ControlNode()

    data class Catch(val body: List<ControlNode> = emptyList()) : ControlNode()

    /** Only ever built for a labeled break/continue: an unlabeled jump scores 0, so walkers just don't emit one. */
    data class LabeledJump(val labeled: Boolean = true) : ControlNode()

    /** A call from a function body back to its own enclosing function/method. */
    data class RecursiveCall(val calleeName: String) : ControlNode()
}

enum class LogicalOp { AND, OR }

/** The left-to-right sequence of &&/|| operators found in one boolean expression, parentheses stripped. */
data class BoolExpr(val operators: List<LogicalOp>)
