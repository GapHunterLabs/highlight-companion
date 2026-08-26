package dev.gaphunter.highlightcompanion.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.ui.JBColor
import dev.gaphunter.highlightcompanion.complexity.CognitiveComplexityCalculator
import dev.gaphunter.highlightcompanion.psi.JavaCognitiveWalker
import dev.gaphunter.highlightcompanion.psi.KotlinCognitiveWalker
import dev.gaphunter.highlightcompanion.review.ReviewPrompt
import dev.gaphunter.highlightcompanion.settings.HighlightCompanionSettings
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * The whole reason this plugin can claim "never freezes the IDE": all real
 * work happens in collectSlowLineMarkers, which the platform contract
 * guarantees runs on a background thread as part of the daemon's "slow line
 * markers" pass - never on the EDT, never inline with typing. getLineMarkerInfo
 * (the fast, per-element pass invoked far more often) is a hard no-op on
 * purpose: the competitor's own changelog ("(Bug Fix) Move slow checks out of
 * EDT thread to decrease amount of freezes", repeated across three releases
 * and still not fully fixed) is exactly what putting real work there causes.
 */
class CognitiveComplexityLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {

    override fun getName(): String = "Cognitive complexity"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val settings = HighlightCompanionSettings.getInstance()
        val state = settings.state
        if (!state.enabled) return
        val config = settings.toRuleConfig()

        for (element in elements) {
            val anchor = anchorFor(element) ?: continue
            val score = scoreOf(anchor, config)
            if (score < state.minimumToShow) continue
            result.add(buildMarker(element, score, state))

            // Only the red (genuinely concerning) threshold counts as a
            // real, actionable finding -- green/yellow markers show up on
            // ordinary, healthy methods too and would inflate the CTA
            // counter on code that doesn't actually need attention.
            if (score >= state.redThreshold) {
                val file = element.containingFile
                val path = file.virtualFile?.path ?: continue
                val lineNumber = file.viewProvider.document?.getLineNumber(element.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
            }
        }
    }

    private fun scoreOf(anchor: PsiElement, config: dev.gaphunter.highlightcompanion.complexity.RuleConfig): Int = when (anchor) {
        is PsiMethod -> ComplexityCache.getOrCompute(anchor) {
            CognitiveComplexityCalculator.score(JavaCognitiveWalker.buildBody(anchor), config)
        }
        is KtNamedFunction -> ComplexityCache.getOrCompute(anchor) {
            CognitiveComplexityCalculator.score(KotlinCognitiveWalker.buildBody(anchor), config)
        }
        else -> 0
    }

    private fun anchorFor(element: PsiElement): PsiElement? {
        val parent = element.parent
        return when {
            parent is PsiMethod && parent.nameIdentifier === element -> parent
            parent is KtNamedFunction && parent.nameIdentifier === element -> parent
            else -> null
        }
    }

    private fun buildMarker(nameIdentifier: PsiElement, score: Int, state: HighlightCompanionSettings.State): LineMarkerInfo<PsiElement> {
        val color = colorFor(score, state)
        val tooltip = "Cognitive complexity: $score (yellow >= ${state.yellowThreshold}, red >= ${state.redThreshold})"
        return LineMarkerInfo(
            nameIdentifier,
            nameIdentifier.textRange,
            ComplexityGutterIcon(score, color),
            { _: PsiElement -> tooltip },
            null,
            GutterIconRenderer.Alignment.RIGHT,
            { tooltip },
        )
    }

    private fun colorFor(score: Int, state: HighlightCompanionSettings.State): JBColor = when {
        score >= state.redThreshold -> JBColor.RED
        score >= state.yellowThreshold -> JBColor.ORANGE
        else -> JBColor.GREEN
    }
}
