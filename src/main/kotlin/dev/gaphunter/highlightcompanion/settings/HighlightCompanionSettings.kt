package dev.gaphunter.highlightcompanion.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import dev.gaphunter.highlightcompanion.complexity.RuleConfig

/**
 * Application-level, persisted, and - this is the point - fully mutable:
 * every rule below can be turned off from Settings > Tools > Highlight
 * Companion with no exceptions and no hidden always-on checks. Direct
 * answer to "I hate that I cannot disable Cognitive Complexity. Even
 * after unchecking all checkboxes all code is underlined."
 */
@Service
@State(name = "HighlightCompanionSettings", storages = [Storage("highlight-companion.xml")])
class HighlightCompanionSettings : PersistentStateComponent<HighlightCompanionSettings.State> {

    class State {
        var enabled: Boolean = true

        var ifElseEnabled: Boolean = true
        var loopsEnabled: Boolean = true
        var switchEnabled: Boolean = true
        var catchEnabled: Boolean = true
        var mixedLogicalOperatorsEnabled: Boolean = true
        var labeledJumpsEnabled: Boolean = true
        var recursionEnabled: Boolean = true

        var yellowThreshold: Int = 8
        var redThreshold: Int = 15
        var minimumToShow: Int = 1
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun toRuleConfig(): RuleConfig = RuleConfig(
        ifElseEnabled = myState.ifElseEnabled,
        loopsEnabled = myState.loopsEnabled,
        switchEnabled = myState.switchEnabled,
        catchEnabled = myState.catchEnabled,
        mixedLogicalOperatorsEnabled = myState.mixedLogicalOperatorsEnabled,
        labeledJumpsEnabled = myState.labeledJumpsEnabled,
        recursionEnabled = myState.recursionEnabled,
    )

    companion object {
        fun getInstance(): HighlightCompanionSettings =
            ApplicationManager.getApplication().getService(HighlightCompanionSettings::class.java)
    }
}
