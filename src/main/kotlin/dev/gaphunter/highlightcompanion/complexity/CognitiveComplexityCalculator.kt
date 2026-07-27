package dev.gaphunter.highlightcompanion.complexity

/**
 * Scores a ControlNode tree using SonarSource's Cognitive Complexity metric
 * (G. Ann Campbell, "Cognitive Complexity: A new way of measuring
 * understandability", SonarSource whitepaper), restricted to the subset of
 * constructs this plugin recognizes: if/else-if/else, for/while/do-while,
 * switch/when, catch, mixed boolean-operator sequences, labeled
 * break/continue, and recursion.
 *
 * Two kinds of increment, per the whitepaper:
 * - B1 (structural): every construct below adds a flat 1, no matter how
 *   deeply nested it is.
 * - B2/B3 (nesting): if/loop/switch/catch additionally add their current
 *   nesting level on top of B1. The nesting level itself only increases for
 *   code inside one of those four constructs' bodies. Mixed-operator
 *   sequences, labeled jumps, and recursive calls are B1-only per the
 *   whitepaper (they never scale with nesting).
 * - else-if is scored at the SAME nesting level as the if it chains from
 *   (an else-if ladder doesn't compound); a plain else is B1-only (+1, no
 *   nesting term) because the "else" keyword itself isn't a new decision
 *   point, but its body is nested one level deeper than the if.
 *
 * Known, deliberate gap: nested functions/lambdas increasing the ambient
 * nesting level for code inside them (a real rule in the whitepaper) is not
 * modeled — out of scope for v0.1, see README.
 */
object CognitiveComplexityCalculator {

    fun score(body: List<ControlNode>, config: RuleConfig = RuleConfig.ALL_ENABLED): Int =
        scoreSequence(body, nesting = 0, config)

    private fun scoreSequence(nodes: List<ControlNode>, nesting: Int, config: RuleConfig): Int =
        nodes.sumOf { score(it, nesting, config) }

    private fun score(node: ControlNode, nesting: Int, config: RuleConfig): Int = when (node) {
        is ControlNode.If -> scoreIf(node, nesting, config)

        is ControlNode.Loop ->
            if (config.loopsEnabled) {
                1 + nesting + boolExprCost(node.condition, config) + scoreSequence(node.body, nesting + 1, config)
            } else {
                scoreSequence(node.body, nesting, config)
            }

        is ControlNode.Switch ->
            if (config.switchEnabled) {
                1 + nesting + scoreSequence(node.body, nesting + 1, config)
            } else {
                scoreSequence(node.body, nesting, config)
            }

        is ControlNode.Catch ->
            if (config.catchEnabled) {
                1 + nesting + scoreSequence(node.body, nesting + 1, config)
            } else {
                scoreSequence(node.body, nesting, config)
            }

        is ControlNode.LabeledJump -> if (config.labeledJumpsEnabled && node.labeled) 1 else 0

        is ControlNode.RecursiveCall -> if (config.recursionEnabled) 1 else 0
    }

    private fun scoreIf(node: ControlNode.If, nesting: Int, config: RuleConfig): Int {
        if (!config.ifElseEnabled) {
            // Rule disabled: the if/else nodes are transparent. Their own
            // increment disappears AND they no longer add a nesting level
            // to whatever they contain - a disabled rule must not leave a
            // "ghost" nesting penalty behind for the rules still enabled.
            var total = scoreSequence(node.then, nesting, config)
            total += when {
                node.elseIf != null -> scoreIf(node.elseIf, nesting, config)
                node.elseBody != null -> scoreSequence(node.elseBody, nesting, config)
                else -> 0
            }
            return total
        }

        var total = 1 + nesting + boolExprCost(node.condition, config) + scoreSequence(node.then, nesting + 1, config)
        total += when {
            node.elseIf != null -> scoreIf(node.elseIf, nesting, config)
            node.elseBody != null -> 1 + scoreSequence(node.elseBody, nesting + 1, config)
            else -> 0
        }
        return total
    }

    private fun boolExprCost(expr: BoolExpr?, config: RuleConfig): Int {
        if (!config.mixedLogicalOperatorsEnabled || expr == null || expr.operators.isEmpty()) return 0
        var cost = 1
        for (i in 1 until expr.operators.size) {
            if (expr.operators[i] != expr.operators[i - 1]) cost++
        }
        return cost
    }
}
