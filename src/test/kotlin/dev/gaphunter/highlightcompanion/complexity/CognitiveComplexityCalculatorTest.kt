package dev.gaphunter.highlightcompanion.complexity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every expected number here is hand-derived from the SonarSource Cognitive
 * Complexity whitepaper's rules (see CognitiveComplexityCalculator's own
 * doc comment), not just asserted to be "greater than zero" - the whole
 * point of keeping the algorithm PSI-free is that these numbers are exact
 * and reproducible without touching the IntelliJ platform.
 */
class CognitiveComplexityCalculatorTest {

    private fun ifNode(then: List<ControlNode> = emptyList(), condition: BoolExpr? = null) =
        ControlNode.If(condition = condition, then = then)

    @Test
    fun emptyBodyScoresZero() {
        assertEquals(0, CognitiveComplexityCalculator.score(emptyList()))
    }

    @Test
    fun singleFlatIfScoresOne() {
        val body = listOf(ifNode())
        assertEquals(1, CognitiveComplexityCalculator.score(body))
    }

    @Test
    fun sequentialUnnestedIfsDoNotAccumulateNestingPenalty() {
        // if (a) {}; if (b) {}; if (c) {} - three siblings, none nested in
        // one another, so each is 1 + nesting(0) = 1. Total 3, not 6.
        val body = listOf(ifNode(), ifNode(), ifNode())
        assertEquals(3, CognitiveComplexityCalculator.score(body))
    }

    @Test
    fun fourLevelsOfIfNestingAccumulateTheNestingPenalty() {
        // if(a){ if(b){ if(c){ if(d){ } } } }
        // level0: 1+0=1, level1: 1+1=2, level2: 1+2=3, level3: 1+3=4 -> 10
        val innermost = ifNode()
        val level3 = ifNode(then = listOf(innermost))
        val level2 = ifNode(then = listOf(level3))
        val level1 = ifNode(then = listOf(level2))
        assertEquals(10, CognitiveComplexityCalculator.score(listOf(level1)))
    }

    @Test
    fun forContainingIfContainingWhileAccumulatesAcrossDifferentConstructs() {
        // for(...) { if(...) { while(...) { } } }
        // for: 1+0=1, if: 1+1=2, while: 1+2=3 -> 6
        val whileNode = ControlNode.Loop()
        val ifInside = ifNode(then = listOf(whileNode))
        val forNode = ControlNode.Loop(body = listOf(ifInside))
        assertEquals(6, CognitiveComplexityCalculator.score(listOf(forNode)))
    }

    @Test
    fun mixedLogicalOperatorsInOneConditionCostTwo() {
        // if (a && b || c): one AND-run + one OR-run = 2, plus the if itself (1) = 3
        val condition = BoolExpr(listOf(LogicalOp.AND, LogicalOp.OR))
        val body = listOf(ifNode(condition = condition))
        assertEquals(3, CognitiveComplexityCalculator.score(body))
    }

    @Test
    fun repeatedSameLogicalOperatorCostsOnlyOne() {
        // if (a && b && c): a single run of && = 1, plus the if itself (1) = 2
        val condition = BoolExpr(listOf(LogicalOp.AND, LogicalOp.AND))
        val body = listOf(ifNode(condition = condition))
        assertEquals(2, CognitiveComplexityCalculator.score(body))
    }

    @Test
    fun threeOperatorChangesCostThree() {
        // a && b || c && d: AND, OR, AND -> operator changes twice more after the first run = 3
        val condition = BoolExpr(listOf(LogicalOp.AND, LogicalOp.OR, LogicalOp.AND))
        val body = listOf(ifNode(condition = condition))
        assertEquals(4, CognitiveComplexityCalculator.score(body)) // 1 (if) + 3 (operator cost)
    }

    @Test
    fun labeledBreakInsideDeepNestingAddsAFlatOneRegardlessOfDepth() {
        // outer@ for { for { if { break outer@ } } }
        // outer for: 1+0=1, inner for: 1+1=2, if: 1+2=3, labeled break: +1 flat -> 7
        val labeledBreak = ControlNode.LabeledJump(labeled = true)
        val ifNode = ifNode(then = listOf(labeledBreak))
        val innerFor = ControlNode.Loop(body = listOf(ifNode))
        val outerFor = ControlNode.Loop(body = listOf(innerFor))
        assertEquals(7, CognitiveComplexityCalculator.score(listOf(outerFor)))
    }

    @Test
    fun unlabeledJumpNeverContributes() {
        val unlabeledBreak = ControlNode.LabeledJump(labeled = false)
        assertEquals(0, CognitiveComplexityCalculator.score(listOf(unlabeledBreak)))
    }

    @Test
    fun recursiveCallInsideElseAddsAFlatOneOnTopOfTheElse() {
        // if (n <= 1) return 1 else return n * factorial(n - 1)
        // if: 1+0=1 (then empty); else: +1 (B1 only) + recursive call inside (1) = 2 -> total 3
        val recursiveCall = ControlNode.RecursiveCall("factorial")
        val node = ControlNode.If(then = emptyList(), elseBody = listOf(recursiveCall))
        assertEquals(3, CognitiveComplexityCalculator.score(listOf(node)))
    }

    @Test
    fun switchAddsOneCasesNestOneDeeper() {
        // switch(x) { case A: if(...) {} }
        // switch: 1+0=1; if inside a case body: 1+1=2 -> total 3
        val ifInCase = ifNode()
        val switchNode = ControlNode.Switch(body = listOf(ifInCase))
        assertEquals(3, CognitiveComplexityCalculator.score(listOf(switchNode)))
    }

    @Test
    fun catchBodyNestsOneDeeperThanTheCatchItself() {
        // try { } catch (e) { for (...) { } }
        // catch: 1+0=1; for inside: 1+1=2 -> total 3
        val forInCatch = ControlNode.Loop()
        val catchNode = ControlNode.Catch(body = listOf(forInCatch))
        assertEquals(3, CognitiveComplexityCalculator.score(listOf(catchNode)))
    }

    @Test
    fun elseIfLadderStaysAtTheSameNestingLevelAsTheOriginalIf() {
        // if (a) {} else if (b) {} else {}
        // if: 1+0=1; else-if (same level, not nested): 1+0=1; else: +1 (B1 only) -> total 3
        val finalElse = ControlNode.If(elseBody = emptyList())
        val elseIf = ControlNode.If(elseBody = emptyList())
        val root = ControlNode.If(elseIf = elseIf)
        assertEquals(3, CognitiveComplexityCalculator.score(listOf(root)))
    }

    @Test
    fun deepElseIfLadderNeverCompoundsNestingAcrossRungs() {
        // if / else if / else if / else if / else, five rungs, none of them nested
        // inside the others: 1 (if) + 1 (else if) + 1 (else if) + 1 (else if) + 1 (else, B1 only) = 5
        val rung4 = ControlNode.If(elseBody = emptyList())
        val rung3 = ControlNode.If(elseIf = rung4)
        val rung2 = ControlNode.If(elseIf = rung3)
        val rung1 = ControlNode.If(elseIf = rung2)
        assertEquals(5, CognitiveComplexityCalculator.score(listOf(rung1)))
    }

    // --- RuleConfig gating: proves every rule really is independently toggle-able, not decorative. ---

    @Test
    fun disablingIfRuleDropsItsOwnScoreButNotNestedLoopsInsideIt() {
        val forInside = ControlNode.Loop()
        val ifNode = ifNode(then = listOf(forInside))

        // Enabled: if(1+0) + for(1+1) = 1 + 2 = 3
        assertEquals(3, CognitiveComplexityCalculator.score(listOf(ifNode), RuleConfig.ALL_ENABLED))

        // Disabled: if contributes 0 AND stops adding a nesting level, so the
        // for is scored as if it were at the top level: 1 + 0 = 1.
        val disabled = RuleConfig.ALL_ENABLED.copy(ifElseEnabled = false)
        assertEquals(1, CognitiveComplexityCalculator.score(listOf(ifNode), disabled))
    }

    @Test
    fun disablingLoopsRuleZeroesLoopsButKeepsOtherRulesWorking() {
        val recursiveCall = ControlNode.RecursiveCall("walk")
        val loop = ControlNode.Loop(body = listOf(recursiveCall))

        assertEquals(2, CognitiveComplexityCalculator.score(listOf(loop), RuleConfig.ALL_ENABLED)) // 1 (loop) + 1 (recursion)

        val disabled = RuleConfig.ALL_ENABLED.copy(loopsEnabled = false)
        assertEquals(1, CognitiveComplexityCalculator.score(listOf(loop), disabled)) // loop gone, recursion still counted
    }

    @Test
    fun disablingSwitchRuleDropsItButNestedContentStillCounts() {
        val ifInCase = ifNode()
        val switchNode = ControlNode.Switch(body = listOf(ifInCase))

        assertEquals(3, CognitiveComplexityCalculator.score(listOf(switchNode), RuleConfig.ALL_ENABLED))

        val disabled = RuleConfig.ALL_ENABLED.copy(switchEnabled = false)
        assertEquals(1, CognitiveComplexityCalculator.score(listOf(switchNode), disabled)) // just the inner if at nesting 0
    }

    @Test
    fun disablingCatchRuleDropsItButNestedContentStillCounts() {
        val forInCatch = ControlNode.Loop()
        val catchNode = ControlNode.Catch(body = listOf(forInCatch))

        val disabled = RuleConfig.ALL_ENABLED.copy(catchEnabled = false)
        assertEquals(1, CognitiveComplexityCalculator.score(listOf(catchNode), disabled))
    }

    @Test
    fun disablingMixedLogicalOperatorsRuleZeroesTheirCostOnly() {
        val condition = BoolExpr(listOf(LogicalOp.AND, LogicalOp.OR))
        val body = listOf(ifNode(condition = condition))

        assertEquals(3, CognitiveComplexityCalculator.score(body, RuleConfig.ALL_ENABLED)) // 1 + 2

        val disabled = RuleConfig.ALL_ENABLED.copy(mixedLogicalOperatorsEnabled = false)
        assertEquals(1, CognitiveComplexityCalculator.score(body, disabled)) // just the if
    }

    @Test
    fun disablingLabeledJumpsRuleZeroesThem() {
        val labeledBreak = ControlNode.LabeledJump(labeled = true)
        val disabled = RuleConfig.ALL_ENABLED.copy(labeledJumpsEnabled = false)
        assertEquals(0, CognitiveComplexityCalculator.score(listOf(labeledBreak), disabled))
    }

    @Test
    fun disablingRecursionRuleZeroesIt() {
        val recursiveCall = ControlNode.RecursiveCall("fibonacci")
        val disabled = RuleConfig.ALL_ENABLED.copy(recursionEnabled = false)
        assertEquals(0, CognitiveComplexityCalculator.score(listOf(recursiveCall), disabled))
    }

    @Test
    fun allRulesDisabledAtOnceScoresZeroForAKitchenSinkFunction() {
        val recursiveCall = ControlNode.RecursiveCall("solve")
        val labeledContinue = ControlNode.LabeledJump(labeled = true)
        val condition = BoolExpr(listOf(LogicalOp.AND, LogicalOp.OR))
        val catchNode = ControlNode.Catch(body = listOf(ControlNode.Loop(body = listOf(labeledContinue))))
        val switchNode = ControlNode.Switch(body = listOf(recursiveCall))
        val root = ifNode(condition = condition, then = listOf(catchNode, switchNode))

        assertEquals(0, CognitiveComplexityCalculator.score(listOf(root), RuleConfig.ALL_DISABLED))
    }
}
