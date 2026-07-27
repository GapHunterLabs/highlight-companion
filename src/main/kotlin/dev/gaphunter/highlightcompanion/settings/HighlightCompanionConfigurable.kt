package dev.gaphunter.highlightcompanion.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import dev.gaphunter.highlightcompanion.codeInsight.ComplexityCache
import javax.swing.JComponent

/**
 * Settings > Tools > Highlight Companion. Every checkbox maps 1:1 to a
 * RuleConfig field - there is deliberately no "always on" rule and no
 * checkbox that silently does nothing, unlike the competitor complaint
 * this plugin exists to answer ("I hate that I cannot disable Cognitive
 * Complexity. Even after unchecking all checkboxes all code is
 * underlined.").
 */
class HighlightCompanionConfigurable : Configurable {

    private val settings = HighlightCompanionSettings.getInstance()
    private var dialogPanel: DialogPanel? = null

    override fun getDisplayName(): String = "Highlight Companion"

    override fun createComponent(): JComponent {
        val state = settings.state
        val built = panel {
            row {
                checkBox("Show cognitive complexity gutter icons").bindSelected(state::enabled)
            }
            group("Rules (each one independently toggle-able)") {
                row { checkBox("if / else if / else").bindSelected(state::ifElseEnabled) }
                row { checkBox("for / while / do-while loops").bindSelected(state::loopsEnabled) }
                row { checkBox("switch / when").bindSelected(state::switchEnabled) }
                row { checkBox("catch blocks").bindSelected(state::catchEnabled) }
                row {
                    checkBox("Mixed logical operators in one condition (a && b || c)")
                        .bindSelected(state::mixedLogicalOperatorsEnabled)
                }
                row { checkBox("Labeled break / continue").bindSelected(state::labeledJumpsEnabled) }
                row { checkBox("Recursion").bindSelected(state::recursionEnabled) }
            }
            group("Thresholds") {
                row("Yellow starting at:") {
                    spinner(1..500).bindIntValue(state::yellowThreshold)
                }
                row("Red starting at:") {
                    spinner(1..500).bindIntValue(state::redThreshold)
                }
                row("Minimum complexity to show an icon:") {
                    spinner(0..500).bindIntValue(state::minimumToShow)
                }
            }
        }
        dialogPanel = built
        return built
    }

    override fun isModified(): Boolean = dialogPanel?.isModified() ?: false

    override fun apply() {
        dialogPanel?.apply()
        ComplexityCache.invalidateAll()
        for (project in ProjectManager.getInstance().openProjects) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    override fun reset() {
        dialogPanel?.reset()
    }
}
