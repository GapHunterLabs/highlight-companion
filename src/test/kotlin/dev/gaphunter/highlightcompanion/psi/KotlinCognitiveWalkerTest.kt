package dev.gaphunter.highlightcompanion.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.highlightcompanion.complexity.CognitiveComplexityCalculator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/** Kotlin counterpart of JavaCognitiveWalkerTest, same hand-derived expected numbers. */
class KotlinCognitiveWalkerTest : BasePlatformTestCase() {

    private fun scoreOfFunction(source: String, functionName: String = "target"): Int {
        val file = myFixture.configureByText("acme.kt", source) as KtFile
        var found: KtNamedFunction? = null
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (function.name == functionName) found = function
            }
        })
        val function = found ?: error("function $functionName not found")
        return CognitiveComplexityCalculator.score(KotlinCognitiveWalker.buildBody(function))
    }

    fun testFlatFunctionScoresZero() {
        val score = scoreOfFunction(
            """
            fun target() {
                val x = 1
                println(x)
            }
            """.trimIndent(),
        )
        assertEquals(0, score)
    }

    fun testNestedIfInsideForInsideWhile() {
        // while: 1+0=1; for: 1+1=2; if: 1+2=3 -> 6
        val score = scoreOfFunction(
            """
            fun target(orders: List<String>) {
                while (hasMore()) {
                    for (order in orders) {
                        if (order.isEmpty()) {
                            reject(order)
                        }
                    }
                }
            }
            fun hasMore() = false
            fun reject(order: String) {}
            """.trimIndent(),
        )
        assertEquals(6, score)
    }

    fun testWhenAddsOneAndNestsItsBranchesOneLevelDeeper() {
        // when: 1+0=1; if inside a branch: 1+1=2 -> 3
        val score = scoreOfFunction(
            """
            fun target(status: Int): String {
                when (status) {
                    1 -> {
                        if (status > 0) {
                            return "positive"
                        }
                    }
                    else -> return "other"
                }
                return "fallthrough"
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testExpressionBodyIfIsScoredStructurally() {
        // fun target(n) = if (n < 0) -n else n
        // Kotlin has no separate ternary operator - if/else fills that role,
        // so both keywords still count on their own terms: if: 1+0=1; else
        // (B1 only, real branch present): +1 -> 2. Same as the equivalent
        // Java `if (n<0) return -n; else return n;`, deliberately consistent
        // across both languages rather than special-casing "trivial" bodies.
        val score = scoreOfFunction("fun target(n: Int) = if (n < 0) -n else n")
        assertEquals(2, score)
    }

    fun testMixedLogicalOperatorsInCondition() {
        // if(1+0) + boolCost(a&&b||c = 2) = 3
        val score = scoreOfFunction(
            """
            fun target(a: Boolean, b: Boolean, c: Boolean): Boolean {
                if (a && b || c) {
                    return true
                }
                return false
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testLabeledBreakInsideNestedLoops() {
        // outer for: 1+0=1; inner for: 1+1=2; if: 1+2=3; labeled break: +1 -> 7
        val score = scoreOfFunction(
            """
            fun target(grid: Array<IntArray>) {
                outer@ for (row in grid) {
                    for (cell in row) {
                        if (cell < 0) {
                            break@outer
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(7, score)
    }

    fun testUnlabeledBreakContributesNothing() {
        // for: 1+0=1; if: 1+1=2; unlabeled break: 0 -> 3
        val score = scoreOfFunction(
            """
            fun target(values: IntArray) {
                for (v in values) {
                    if (v < 0) {
                        break
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testRecursiveCallInReturnValueDetectedBySimpleNameMatch() {
        // if: 1+0=1 (then empty); else: +1, recursive call inside return value: +1 -> 3
        val score = scoreOfFunction(
            """
            fun target(n: Long): Long {
                if (n <= 1) {
                    return 1
                } else {
                    return n * target(n - 1)
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testCatchBlockNestsItsBodyOneLevelDeeper() {
        // catch: 1+0=1; for inside: 1+1=2 -> 3
        val score = scoreOfFunction(
            """
            fun target() {
                try {
                    risky()
                } catch (e: Exception) {
                    for (i in 0 until 3) {
                        retry()
                    }
                }
            }
            fun risky() {}
            fun retry() {}
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testElseIfLadderDoesNotCompoundNesting() {
        // if: 1+0=1; else-if: 1+0=1; else-if: 1+0=1; else: +1 -> 4
        val score = scoreOfFunction(
            """
            fun target(riskScore: Int): String {
                return if (riskScore > 90) {
                    "BLOCK"
                } else if (riskScore > 60) {
                    "REVIEW"
                } else if (riskScore > 30) {
                    "FLAG"
                } else {
                    "ALLOW"
                }
            }
            """.trimIndent(),
        )
        assertEquals(4, score)
    }

    fun testIfBuriedInAFunctionArgumentIsScoredStructurally() {
        // if: 1+0=1 (then empty, calls aren't recursive); else: +1 -> 2
        val score = scoreOfFunction(
            """
            fun target(x: Int) {
                foo(if (x > 0) a() else b())
            }
            fun foo(y: Int) {}
            fun a() = 1
            fun b() = 2
            """.trimIndent(),
        )
        assertEquals(2, score)
    }

    fun testIfBuriedInALambdaBodyIsScoredStructurally() {
        // if: 1+0=1 (then empty, flag() isn't recursive), no else -> 1
        val score = scoreOfFunction(
            """
            fun target(items: List<Int>) {
                items.forEach {
                    if (it > 0) {
                        flag()
                    }
                }
            }
            fun flag() {}
            """.trimIndent(),
        )
        assertEquals(1, score)
    }

    fun testAnAnonymousObjectsOwnMethodDoesNotBleedIntoTheEnclosingFunctionScore() {
        // the if lives inside Runnable.run(), a separate function -- target() itself is flat -> 0
        val score = scoreOfFunction(
            """
            fun target() {
                foo(object : Runnable {
                    override fun run() {
                        if (condition()) {
                            flag()
                        }
                    }
                })
            }
            fun foo(r: Runnable) {}
            fun condition() = true
            fun flag() {}
            """.trimIndent(),
        )
        assertEquals(0, score)
    }

    fun testRecursiveCallInsideALambdaArgumentIsStillDetected() {
        // no structural constructs, just the recursive call buried inside a lambda -> 1
        val score = scoreOfFunction(
            """
            fun target(n: Int): Int {
                return listOf(n).map { count(target(it - 1)) }.first()
            }
            fun count(x: Int) = x
            """.trimIndent(),
        )
        assertEquals(1, score)
    }
}
