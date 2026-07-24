package com.mattiadr.burpIO.contextMenu

import burp.api.montoya.http.message.HttpRequestResponse
import com.mattiadr.burpIO.AppContext
import com.mattiadr.burpIO.stringToClipboard
import com.mattiadr.burpIO.toast
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
import javax.swing.SwingWorker

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
		callback: (HttpRequestResponse, String) -> List<String?>
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
		val okButton = JButton("OK")
		val cancelButton = JButton("Cancel").apply { addActionListener { dialog.dispose() } }

		// The dialog closes immediately so the user can keep working in Burp; the extraction runs
		// on a background thread and copies to the clipboard once it completes.
		okButton.addActionListener {
			// snapshot the UI state on the EDT before closing the dialog and handing off to the worker
			val query = textField.text
			val uniqueURLs = uniqueURLsCheckbox.isSelected
			val uniqueResults = uniqueResultsCheckbox.isSelected
			dialog.dispose()

			object : SwingWorker<List<String>, Void>() {
				override fun doInBackground(): List<String> {
					// make requestResponses unique by url if needed
					val rrs = if (uniqueURLs) requestResponses.distinctBy { it.request().url() } else requestResponses
					// invoke callback; drop nulls that leak in from Montoya's platform-typed getters
					// (headerValue/parameterValue return null when the header/parameter is absent)
					val extracted = rrs.flatMap { callback(it, query) }.filterNotNull()
					return if (uniqueResults) extracted.distinct() else extracted
				}

				override fun done() {
					try {
						val extractedValues = get()
						val text = extractedValues.joinToString("\n") { it.replace(newlineRegex, "\\n") }
						stringToClipboard(text, "Copied ${extractedValues.size} lines to Clipboard")
					} catch (e: Exception) {
						toast("Extraction failed: ${e.message}", 10000)
					}
				}
			}.execute()
		}

		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
			add(okButton)
			add(cancelButton)
		}
		dialog.add(buttonPanel, BorderLayout.SOUTH)
		dialog.rootPane.defaultButton = okButton

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
