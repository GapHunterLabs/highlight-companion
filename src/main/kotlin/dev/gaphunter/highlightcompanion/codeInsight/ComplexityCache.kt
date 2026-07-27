package dev.gaphunter.highlightcompanion.codeInsight

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

/**
 * Per-function cache, keyed by user data stored directly on the anchor PSI
 * element (the method/function name identifier's parent). Two independent
 * invalidation triggers:
 *
 * - The containing file's own modification stamp (`PsiFile.modificationStamp`,
 *   which is per-file, not global): editing file A never invalidates a cached
 *   score for a function living in file B, which is the direct fix for the
 *   competitor's "recompute on every keystroke, even in unrelated files"
 *   defect. In the common case of an incremental block reparse (editing
 *   inside one function's body leaves sibling functions' PSI elements
 *   untouched), sibling functions in the SAME file also survive without
 *   recomputation, though this isn't guaranteed for edits that force a full
 *   file reparse.
 * - A global epoch counter, bumped once whenever Settings changes any rule
 *   or threshold (see HighlightCompanionConfigurable.apply). This is
 *   intentionally coarse: correctness (never show a score computed under
 *   stale rules) matters more than avoiding one extra recompute pass right
 *   after the user hits Apply.
 */
object ComplexityCache {
    private val KEY = Key.create<Entry>("dev.gaphunter.highlightcompanion.complexity.cache")

    @Volatile
    private var epoch = 0

    private data class Entry(val fileModificationStamp: Long, val epoch: Int, val score: Int)

    fun getOrCompute(anchor: PsiElement, compute: () -> Int): Int {
        val stamp = anchor.containingFile?.modificationStamp ?: -1L
        val cached = anchor.getUserData(KEY)
        if (cached != null && cached.fileModificationStamp == stamp && cached.epoch == epoch) {
            return cached.score
        }
        val score = compute()
        anchor.putUserData(KEY, Entry(stamp, epoch, score))
        return score
    }

    fun invalidateAll() {
        epoch++
    }
}
