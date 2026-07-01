package com.mattiadr.burpIO

import burp.api.montoya.ui.Theme
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

fun stringToClipboard(string: String, message: String = "Copied to Clipboard") {
	val selection = StringSelection(string)
	clipboard.setContents(selection, selection)
	toast("📋  $message")
}

private fun slideWindow(
	window: JWindow,
	startX: Int,
	startY: Int,
	endX: Int,
	endY: Int,
	duration: Int = 300,
	callback: (JWindow) -> Unit
) {
	val timer = Timer(15, null)
	var steps = duration / 15
	val deltaX = (endX - startX) / steps
	val deltaY = (endY - startY) / steps
	window.setLocation(startX, startY)

	timer.addActionListener {
		window.setLocation(window.x + deltaX, window.y + deltaY)
		steps--
		if (steps <= 0) {
			timer.stop()
			callback(window)
		}
	}
	timer.start()
}

fun toast(message: String, duration: Int = 3000) {
	SwingUtilities.invokeLater {
		// init window
		val window = JWindow()
		window.isAlwaysOnTop = true

		// create panel according to theme
		val panel = JPanel(BorderLayout())
		val borderColor = if (AppContext.api.userInterface().currentTheme() == Theme.DARK) Color.WHITE else Color.BLACK
		panel.border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor, 1, true),
			BorderFactory.createEmptyBorder(12, 24, 12, 24)
		)
		panel.add(JLabel(message), BorderLayout.CENTER)

		// add panel and pack
		window.contentPane = panel
		window.pack()

		// animate toast
		val screen = Toolkit.getDefaultToolkit().screenSize
		val x = (screen.width - window.width) / 2
		window.isVisible = true
		slideWindow(window, x, -window.height, x, 20) {
			Timer(duration) {
				slideWindow(window, x, 20, x, -window.height) {
					window.dispose()
				}
			}.apply {
				isRepeats = false
				start()
			}
		}
	}
}
