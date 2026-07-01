package com.mattiadr.burpIO.functions

import burp.api.montoya.http.message.HttpRequestResponse
import com.mattiadr.burpIO.AppContext
import com.mattiadr.burpIO.stringToClipboard
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

	private val newlineRegex = Regex("\r\n|\n|\r")

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
		JMenuItem("Request Parameter").apply {
			addActionListener {
				showPopup("Insert Parameter to Extract", requestResponses) { rr, parameter ->
					listOf(rr.request().parameterValue(parameter))
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Request Header").apply {
			addActionListener {
				showPopup("Insert Header to Extract", requestResponses) { rr, header ->
					listOf(rr.request().headerValue(header))
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Request Body (Regex)").apply {
			addActionListener {
				showPopup("Insert Regex to Extract", requestResponses) { rr, pattern ->
					val regex = if (pattern.isNotBlank()) Regex(pattern) else Regex(".*")
					regex.findAll(rr.request().bodyToString()).mapNotNull(::processMatchResult).toList()
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Response Header").apply {
			addActionListener {
				showPopup("Insert Header to Extract", requestResponses) { rr, header ->
					if (rr.hasResponse()) listOf(rr.response().headerValue(header)) else emptyList()
				}
			}
			extractSubMenu.add(this)
		}
		JMenuItem("Response Body (Regex)").apply {
			addActionListener {
				showPopup("Insert Regex to Extract", requestResponses) { rr, pattern ->
					if (!rr.hasResponse()) return@showPopup emptyList()
					val regex = if (pattern.isNotBlank()) Regex(pattern) else Regex(".*")
					regex.findAll(rr.response().bodyToString()).mapNotNull(::processMatchResult).toList()
				}
			}
			extractSubMenu.add(this)
		}

		menuItems.add(extractSubMenu)
	}

	private fun showPopup(
		title: String,
		requestResponses: List<HttpRequestResponse>,
		callback: (HttpRequestResponse, String) -> List<String>
	) {
		val dialog = JDialog(AppContext.swingUtils.suiteFrame(), title, true)
		dialog.layout = BorderLayout()

		// form panel
		val formPanel = JPanel(GridLayout(0, 1, 5, 5))
		val textField = JTextField()
		formPanel.add(textField)
		val uniqueURLsCheckbox = JCheckBox("Unique URLs")
		formPanel.add(uniqueURLsCheckbox)
		val uniqueResultsCheckbox = JCheckBox("Unique Results")
		uniqueResultsCheckbox.isSelected = true
		formPanel.add(uniqueResultsCheckbox)
		dialog.add(formPanel, BorderLayout.CENTER)

		// button panel
		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
		JButton("OK").apply {
			addActionListener { _ ->
				// make requestResponses unique by url if needed
				val requestResponses = if (uniqueURLsCheckbox.isSelected) requestResponses.distinctBy {
					it.request().url()
				} else requestResponses
				// invoke callback
				var extractedValues: List<String> = requestResponses.flatMap { callback(it, textField.text) }
				// make distinct if needed
				extractedValues = if (uniqueResultsCheckbox.isSelected) extractedValues.distinct() else extractedValues
				// copy to clipboard
				val text = extractedValues.joinToString("\n") { it.replace(newlineRegex, "\\n") }
				stringToClipboard(text, "Copied ${extractedValues.size} lines to Clipboard")
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
