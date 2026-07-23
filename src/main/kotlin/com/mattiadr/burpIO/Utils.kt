package com.mattiadr.burpIO

import burp.api.montoya.ui.Theme
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.BoxLayout
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

fun toast(message: String, duration: Int = 3000) {
	SwingUtilities.invokeLater {
		ToastManager.show(message, duration)
	}
}

private object ToastManager {

	const val TOP_OFFSET = 40

	private fun slideWindow(
		window: JWindow,
		startX: Int,
		startY: Int,
		endX: Int,
		endY: Int,
		duration: Int = 300,
		callback: ((JWindow) -> Unit)? = null
	) {
		val timer = Timer(15, null)
		val maxSteps = duration / 15
		var step = 0
		val deltaX = (endX - startX).toDouble() / maxSteps
		val deltaY = (endY - startY).toDouble() / maxSteps
		window.setLocation(startX, startY)

		timer.addActionListener {
			window.setLocation(startX + (deltaX * step).toInt(), startY + (deltaY * step).toInt())
			step++
			if (step > maxSteps) {
				timer.stop()
				callback?.invoke(window)
			}
		}
		timer.start()
	}

	// the single shared toast window, or null when no toast is currently visible
	private var toastWindow: JWindow? = null
	private var toastContainer: JPanel? = null

	// keep the toast horizontally centered near the top after its size changes
	private fun repositionToast(window: JWindow) {
		val screen = Toolkit.getDefaultToolkit().screenSize
		window.pack()
		window.setLocation((screen.width - window.width) / 2, TOP_OFFSET)
	}

	// build a single message panel styled according to the current theme
	private fun createMessagePanel(message: String): JPanel {
		val panel = JPanel(BorderLayout())
		val borderColor = if (AppContext.api.userInterface().currentTheme() == Theme.DARK) Color.WHITE else Color.BLACK
		panel.border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor, 1, true),
			BorderFactory.createEmptyBorder(12, 24, 12, 24)
		)
		panel.add(JLabel(message), BorderLayout.CENTER)
		return panel
	}

	// remove a single message after its duration; slide the window away once empty
	private fun scheduleRemoval(window: JWindow, container: JPanel, messagePanel: JPanel, duration: Int) {
		Timer(duration) {
			container.remove(messagePanel)
			if (container.componentCount == 0) {
				// this was the last message: clear the shared refs and slide out
				if (toastWindow === window) {
					toastWindow = null
					toastContainer = null
				}
				val screen = Toolkit.getDefaultToolkit().screenSize
				val x = (screen.width - window.width) / 2
				slideWindow(window, x, window.y, x, -window.height) {
					window.dispose()
				}
			} else {
				repositionToast(window)
			}
		}.apply {
			isRepeats = false
			start()
		}
	}

	fun show(message: String, duration: Int) {
		val messagePanel = createMessagePanel(message)

		val existingWindow = toastWindow
		val existingContainer = toastContainer
		if (existingWindow != null && existingContainer != null) {
			// a toast is already visible: stack this message on top of the others
			existingContainer.add(messagePanel, 0)
			repositionToast(existingWindow)
			scheduleRemoval(existingWindow, existingContainer, messagePanel, duration)
		} else {
			// no toast visible: create a new window that can hold multiple messages
			val window = JWindow()
			window.isAlwaysOnTop = true

			val container = JPanel()
			container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
			container.add(messagePanel)

			window.contentPane = container
			window.pack()

			toastWindow = window
			toastContainer = container

			// animate toast in
			val screen = Toolkit.getDefaultToolkit().screenSize
			val x = (screen.width - window.width) / 2
			window.isVisible = true
			slideWindow(window, x, -window.height, x, TOP_OFFSET)
			scheduleRemoval(window, container, messagePanel, duration)
		}
	}
}
