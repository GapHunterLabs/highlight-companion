package dev.gaphunter.highlightcompanion.complexity

/**
 * Which cognitive-complexity rules count toward the score. Every field maps
 * 1:1 to a checkbox in Settings > Tools > Highlight Companion, and every
 * field defaults to enabled. There is deliberately no field that is
 * hardcoded "always on" and no way to leave a rule half-disabled — the
 * direct answer to the competitor complaint "I hate that I cannot disable
 * Cognitive Complexity. Even after unchecking all checkboxes all code is
 * underlined."
 */
data class RuleConfig(
    val ifElseEnabled: Boolean = true,
    val loopsEnabled: Boolean = true,
    val switchEnabled: Boolean = true,
    val catchEnabled: Boolean = true,
    val mixedLogicalOperatorsEnabled: Boolean = true,
    val labeledJumpsEnabled: Boolean = true,
    val recursionEnabled: Boolean = true,
) {
    companion object {
        val ALL_ENABLED = RuleConfig()
        val ALL_DISABLED = RuleConfig(
            ifElseEnabled = false,
            loopsEnabled = false,
            switchEnabled = false,
            catchEnabled = false,
            mixedLogicalOperatorsEnabled = false,
            labeledJumpsEnabled = false,
            recursionEnabled = false,
        )
    }
}
