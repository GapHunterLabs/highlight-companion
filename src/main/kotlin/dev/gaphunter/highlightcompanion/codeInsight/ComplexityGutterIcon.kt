package dev.gaphunter.highlightcompanion.codeInsight

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/**
 * A small colored badge with the complexity number drawn on top, rendered
 * on demand rather than picked from a fixed icon set - the number is
 * arbitrary and the color depends on the user's own configurable
 * thresholds, so a static icon set can't cover it. The badge's own
 * background is always one of three solid, opaque colors (JBColor.GREEN /
 * ORANGE / RED, matching the convention already used for expiry warnings in
 * Cert Companion), so white text on top stays legible in both themes
 * without depending on editor/IDE background contrast.
 */
class ComplexityGutterIcon(private val score: Int, private val color: JBColor) : Icon {

    private val size = JBUI.scale(16)

    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            val arc = JBUI.scale(5).toFloat()
            g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(), arc, arc))

            g2.color = JBColor.WHITE
            g2.font = Font(Font.SANS_SERIF, Font.BOLD, JBUI.scale(10))
            val text = if (score > 99) "99+" else score.toString()
            val fm = g2.fontMetrics
            val textX = x + (size - fm.stringWidth(text)) / 2
            val textY = y + (size + fm.ascent - fm.descent) / 2
            g2.drawString(text, textX, textY)
        } finally {
            g2.dispose()
        }
    }
}
