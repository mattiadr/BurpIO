package org.mattiadr.burpIO.functions

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.ui.swing.SwingUtils
import org.mattiadr.burpIO.stringToClipboard
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

object ExtractStrings {

	private lateinit var swingUtils: SwingUtils
	private val newlineRegex = Regex("\r\n|\n|\r")

	fun initApi(api: MontoyaApi) {
		swingUtils = api.userInterface().swingUtils()
	}

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		val extractSubMenu = JMenu("Extract")

		JMenuItem("Path").apply {
			addActionListener { stringToClipboard(requestResponses.joinToString("\n") { it.request().path() }) }
			extractSubMenu.add(this)
		}
		JMenuItem("Path without Query").apply {
			addActionListener {
				stringToClipboard(requestResponses.joinToString("\n") {
					it.request().pathWithoutQuery()
				})
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Request Header").apply {
			addActionListener {
				showPopup("Insert Header to Extract") { header ->
					requestResponses.mapNotNull { it.request().headerValue(header) }
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Request Body (Regex)").apply {
			addActionListener {
				showPopup("Insert Regex to Extract") { pattern ->
					val regex = if (pattern.isNotBlank()) Regex(pattern) else Regex(".*")
					requestResponses.mapNotNull { rr ->
						rr.request().bodyToString().let {
							regex.find(it)
						}?.let {
							processMatchResult(it)
						}
					}
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Response Header").apply {
			addActionListener {
				showPopup("Insert Header to Extract") { header ->
					requestResponses.mapNotNull { it.response().headerValue(header) }
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Response Body (Regex)").apply {
			addActionListener {
				showPopup("Insert Regex to Extract") { pattern ->
					val regex = if (pattern.isNotBlank()) Regex(pattern) else Regex(".*")
					requestResponses.mapNotNull { rr ->
						rr.takeIf { it.hasResponse() }?.response()?.bodyToString()?.let {
							regex.find(it)
						}?.let {
							processMatchResult(it)
						}
					}
				}
			}
			extractSubMenu.add(this)
		}

		menuItems.add(extractSubMenu)
	}

	private fun showPopup(title: String, callback: (String) -> List<String>) {
		val dialog = JDialog(swingUtils.suiteFrame(), title, true)
		dialog.layout = BorderLayout()

		// form panel
		val formPanel = JPanel(GridLayout(0, 1, 5, 5))
		val textField = JTextField()
		formPanel.add(textField)
		val removeDuplicatesCheckbox = JCheckBox("Remove Duplicates")
		removeDuplicatesCheckbox.isSelected = true
		formPanel.add(removeDuplicatesCheckbox)
		dialog.add(formPanel, BorderLayout.CENTER)

		// button panel
		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
		JButton("OK").apply {
			addActionListener {
				// invoke callback to collect data
				var values = callback(textField.text)
				// make distinct if needed and copy to clipboard
				values = if (removeDuplicatesCheckbox.isSelected) values.distinct() else values
				stringToClipboard(values.joinToString("\n") { it.replace(newlineRegex, "\\n") })
				dialog.dispose()
			}
			buttonPanel.add(this)
			dialog.rootPane.defaultButton = this
		}
		JButton("Cancel").apply {
			addActionListener { dialog.dispose() }
			buttonPanel.add(this)
		}
		dialog.add(buttonPanel, BorderLayout.SOUTH)

		// keybinds
		val esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
		dialog.rootPane.registerKeyboardAction({ dialog.dispose() }, esc, JComponent.WHEN_IN_FOCUSED_WINDOW)

		// popup
		dialog.pack()
		dialog.setLocationRelativeTo(null)
		dialog.isVisible = true
	}

	private fun processMatchResult(match: MatchResult): String? {
		return if (match.groupValues.size > 1)
			match.groupValues.drop(1).joinToString("\t") { it.replace("\t", "\\t") }
		else
			match.groupValues.getOrNull(0)?.replace("\t", "\\t")
	}
}
