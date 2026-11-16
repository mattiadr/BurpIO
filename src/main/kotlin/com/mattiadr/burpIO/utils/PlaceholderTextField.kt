package com.mattiadr.burpIO.utils

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTextField

// JTextField has no native placeholder support (unlike e.g. HTML's placeholder attribute) - this paints
// grayed-out hint text when the field is empty, using the text component's own disabledTextColor so it
// matches the current look-and-feel instead of a hardcoded gray. Empty placeholder is a safe no-op (drawn
// as nothing), so callers can pass one unconditionally without an extra branch.
class PlaceholderTextField(text: String, columns: Int, private val placeholder: String) : JTextField(text, columns) {
	override fun paintComponent(g: Graphics) {
		super.paintComponent(g)
		if (text.isNotEmpty() || placeholder.isEmpty()) return

		val g2 = g as Graphics2D
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		g2.color = disabledTextColor
		g2.font = font
		val fontMetrics = g2.fontMetrics
		val x = insets.left
		val y = (height - fontMetrics.height) / 2 + fontMetrics.ascent
		g2.drawString(placeholder, x, y)
	}
}
