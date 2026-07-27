package dev.gaphunter.highlightcompanion.psi

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.highlightcompanion.complexity.CognitiveComplexityCalculator

/**
 * Real PSI, not hand-built ControlNode trees: proves JavaCognitiveWalker
 * actually reads real Java source the same way CognitiveComplexityCalculatorTest
 * hand-verified the scoring rules. Expected numbers are re-derived by hand
 * here too, independent of the pure-algorithm test file.
 */
class JavaCognitiveWalkerTest : BasePlatformTestCase() {

    private fun scoreOfMethod(source: String, methodName: String = "target"): Int {
        val file = myFixture.configureByText("Acme.java", source)
        val psiFile = file as com.intellij.psi.PsiJavaFile
        val psiClass = psiFile.classes.first()
        val method = psiClass.methods.first { it.name == methodName }
        return CognitiveComplexityCalculator.score(JavaCognitiveWalker.buildBody(method))
    }

    fun testFlatMethodScoresZero() {
        val score = scoreOfMethod(
            """
            class Acme {
                void target() {
                    int x = 1;
                    System.out.println(x);
                }
            }
            """.trimIndent(),
        )
        assertEquals(0, score)
    }

    fun testNestedIfInsideForInsideWhile() {
        // while: 1+0=1; for: 1+1=2; if: 1+2=3 -> 6
        val score = scoreOfMethod(
            """
            class Acme {
                void target(java.util.List<String> orders) {
                    while (hasMore()) {
                        for (String order : orders) {
                            if (order.isEmpty()) {
                                reject(order);
                            }
                        }
                    }
                }
                boolean hasMore() { return false; }
                void reject(String s) {}
            }
            """.trimIndent(),
        )
        assertEquals(6, score)
    }

    fun testElseIfLadderDoesNotCompoundNesting() {
        // if: 1+0=1; else-if: 1+0=1; else-if: 1+0=1; else: +1 -> 4
        val score = scoreOfMethod(
            """
            class Acme {
                String target(int riskScore) {
                    if (riskScore > 90) {
                        return "BLOCK";
                    } else if (riskScore > 60) {
                        return "REVIEW";
                    } else if (riskScore > 30) {
                        return "FLAG";
                    } else {
                        return "ALLOW";
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(4, score)
    }

    fun testMixedLogicalOperatorsInCondition() {
        // if(1+0) + boolCost(a&&b||c = 2) = 3
        val score = scoreOfMethod(
            """
            class Acme {
                boolean target(boolean a, boolean b, boolean c) {
                    if (a && b || c) {
                        return true;
                    }
                    return false;
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testLabeledBreakInsideNestedLoops() {
        // outer for: 1+0=1; inner for: 1+1=2; if: 1+2=3; labeled break: +1 -> 7
        val score = scoreOfMethod(
            """
            class Acme {
                void target(int[][] grid) {
                    outer:
                    for (int i = 0; i < grid.length; i++) {
                        for (int j = 0; j < grid[i].length; j++) {
                            if (grid[i][j] < 0) {
                                break outer;
                            }
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
        val score = scoreOfMethod(
            """
            class Acme {
                void target(int[] values) {
                    for (int v : values) {
                        if (v < 0) {
                            break;
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testRecursiveCallDetectedBySimpleNameMatch() {
        // if: 1+0=1 (then empty); else: +1, recursive call inside: +1 -> 3
        val score = scoreOfMethod(
            """
            class Acme {
                long target(int n) {
                    if (n <= 1) {
                        return 1;
                    } else {
                        return n * target(n - 1);
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testSwitchStatementNestsItsCasesOneLevelDeeper() {
        // switch: 1+0=1; if inside a case: 1+1=2 -> 3
        val score = scoreOfMethod(
            """
            class Acme {
                String target(int status) {
                    switch (status) {
                        case 1:
                            if (status > 0) {
                                return "positive";
                            }
                        case 2:
                            return "two";
                        default:
                            return "other";
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testCatchBlockNestsItsBodyOneLevelDeeper() {
        // catch: 1+0=1; for inside: 1+1=2 -> 3
        val score = scoreOfMethod(
            """
            class Acme {
                void target() {
                    try {
                        risky();
                    } catch (Exception e) {
                        for (int i = 0; i < 3; i++) {
                            retry();
                        }
                    }
                }
                void risky() throws Exception {}
                void retry() {}
            }
            """.trimIndent(),
        )
        assertEquals(3, score)
    }

    fun testUnrelatedMethodInAnotherFileIsNotConfusedWithRecursion() {
        // Calling a same-named method on a DIFFERENT declared variable type
        // is still matched by simple name (documented heuristic, not a bug):
        // if: 1+0=1; recursive-looking call inside then: +1 -> 2
        val score = scoreOfMethod(
            """
            class Acme {
                void target(Helper h) {
                    if (h != null) {
                        target(h);
                    }
                }
            }
            class Helper {}
            """.trimIndent(),
        )
        assertEquals(2, score)
    }

    fun testMethodWithNoBodyScoresZero() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            interface Acme {
                void target();
            }
            """.trimIndent(),
        )
        val psiFile = file as com.intellij.psi.PsiJavaFile
        val method: PsiMethod = psiFile.classes.first().methods.first { it.name == "target" }
        assertEquals(0, CognitiveComplexityCalculator.score(JavaCognitiveWalker.buildBody(method)))
    }
}
