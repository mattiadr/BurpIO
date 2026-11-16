package com.mattiadr.burpIO.contextMenu

import burp.api.montoya.http.message.ContentType
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.MimeType
import com.mattiadr.burpIO.AppContext
import com.mattiadr.burpIO.utils.PlaceholderTextField
import com.mattiadr.burpIO.utils.extractStringRegexTemplate
import com.mattiadr.burpIO.utils.stringToClipboard
import com.mattiadr.burpIO.utils.toast
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyEvent
import java.util.regex.PatternSyntaxException
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingWorker

private class ParameterConfig<out T> private constructor(
	val name: String,
	val hint: String,
	val validationError: String,
	/**
	 * Validates and maps the raw input to the parameter's type.
	 * Returns the mapped value, or null to signal a validation error.
	 */
	val validator: (String) -> T?,
) {
	companion object {
		fun string(name: String, hint: String = "", allowBlank: Boolean = false) =
			ParameterConfig(name, hint, "Must not be blank") { if (allowBlank || it.isNotBlank()) it else null }

		fun regex(name: String, hint: String = "") =
			ParameterConfig(name, hint, "Invalid Regex") {
				try { it.toRegex() } catch (_: PatternSyntaxException) { null }
			}

		// Montoya JSON path: elements delimited by '.', each one a key, an array index `[n]`
		// (zero-based), or `[]` for the last array element. E.g. `account.[0].user.name`.
		private val jsonPathRegex = Regex("""(?:\[\d+]|\[]|[^.\[\]]+)(?:\.(?:\[\d+]|\[]|[^.\[\]]+))*""")

		fun jsonPath(name: String, hint: String = "account.[0].user") =
			ParameterConfig(name, hint, "Invalid JSON Path") { it.takeIf(jsonPathRegex::matches) }
	}
}

private class ExtractionConfig(
	val name: String,
	val parameters: List<ParameterConfig<*>>,
	/** Receives the validated + mapped parameter values, aligned by index with [parameters]. */
	val extractor: (HttpRequestResponse, List<Any?>) -> String?,
)

/** Extraction with no parameters. */
private fun extraction(name: String, extractor: (HttpRequestResponse) -> String?) =
	ExtractionConfig(name, emptyList()) { rr, _ -> extractor(rr) }

/** Extraction with one typed parameter. The cast is safe: the GUI collects values aligned with [p1]. */
private fun <A> extraction(
	name: String,
	p1: ParameterConfig<A>,
	extractor: (HttpRequestResponse, A) -> String?,
) = ExtractionConfig(name, listOf(p1)) { rr, args ->
	@Suppress("UNCHECKED_CAST")
	extractor(rr, args[0] as A)
}

/** Extraction with two typed parameters. The casts are safe: the GUI collects values aligned with [p1], [p2]. */
private fun <A, B> extraction(
	name: String,
	p1: ParameterConfig<A>,
	p2: ParameterConfig<B>,
	extractor: (HttpRequestResponse, A, B) -> String?,
) = ExtractionConfig(name, listOf(p1, p2)) { rr, args ->
	@Suppress("UNCHECKED_CAST")
	extractor(rr, args[0] as A, args[1] as B)
}

object ExtractStrings {

	private val configs: List<ExtractionConfig> = listOf(
		extraction("Path") { rr -> rr.request().path() },
		extraction("Path without Query") { rr -> rr.request().pathWithoutQuery() },
		extraction("Request Parameter", ParameterConfig.string("Parameter")) { rr, param ->
			rr.request().parameterValue(param)
		},
		extraction("Request Header", ParameterConfig.string("Header")) { rr, header ->
			rr.request().headerValue(header)
		},
		extraction("Request Body (Regex)",
			ParameterConfig.regex("Regex", "user-(\\d+)"),
			ParameterConfig.string("Template", "$1", allowBlank = true),
		) { rr, regex, template ->
			extractStringRegexTemplate(rr.request().bodyToString(), regex, template)?.ifEmpty { null }
		},
		extraction("Request Body (JSON)",
			ParameterConfig.jsonPath("JSON Path")
		) { rr, jsonPath ->
			if (rr.request().contentType() != ContentType.JSON) return@extraction null
			val body = rr.request().bodyToString()
			var out = AppContext.jsonUtils.readString(body, jsonPath)
			if (out == null) out = AppContext.jsonUtils.read(body, jsonPath)
			return@extraction out
		},
		extraction("Response Header", ParameterConfig.string("Header")) { rr, header ->
			if (!rr.hasResponse()) return@extraction null
			rr.response().headerValue(header)
		},
		extraction("Response Body (Regex)",
			ParameterConfig.regex("Regex", "user-(\\d+)"),
			ParameterConfig.string("Template", "$1", allowBlank = true),
		) { rr, regex, template ->
			if (!rr.hasResponse()) return@extraction null
			extractStringRegexTemplate(rr.response().bodyToString(), regex, template)?.ifEmpty { null }
		},
		extraction("Response Body (JSON)",
			ParameterConfig.jsonPath("JSON Path")
		) { rr, jsonPath ->
			if (!rr.hasResponse()) return@extraction null
			if (rr.response().inferredMimeType() != MimeType.JSON) return@extraction null
			val body = rr.response().bodyToString()
			var out = AppContext.jsonUtils.readString(body, jsonPath)
			if (out == null) out = AppContext.jsonUtils.read(body, jsonPath)
			return@extraction out
		},
	)

	private val newlineRegex = Regex("\r\n|\n|\r")

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		val extractSubMenu = JMenu("Extract")

		for (config in configs) {
			JMenuItem(config.name).apply {
				addActionListener { extract(config, requestResponses) }
				extractSubMenu.add(this)
			}
		}

		menuItems.add(extractSubMenu)
	}

	/**
	 * Entry point for a menu action: if [config] has parameters, prompts the user for their values
	 * (with per-parameter validation and the Unique URLs / Unique Results options) and then runs the
	 * extraction; if it has none, runs immediately with both unique options off and no popup.
	 */
	private fun extract(config: ExtractionConfig, requestResponses: List<HttpRequestResponse>) {
		if (config.parameters.isEmpty()) {
			executeExtraction(config, requestResponses, emptyList(), uniqueURLs = false, uniqueResults = false)
			return
		}

		val dialog = JDialog(AppContext.swingUtils.suiteFrame(), config.name, true)
		dialog.layout = BorderLayout()

		// form panel: one row per parameter (label + field) with a hidden error label underneath, laid
		// out on a grid so that labels and fields line up in two columns
		val formPanel = JPanel(GridBagLayout())
		val gbc = GridBagConstraints().apply {
			insets = Insets(2, 5, 2, 5)
			anchor = GridBagConstraints.WEST
		}
		val fields = ArrayList<JTextField>(config.parameters.size)
		val errorLabels = ArrayList<JLabel>(config.parameters.size)
		config.parameters.forEachIndexed { i, param ->
			gbc.gridy = i * 2
			gbc.gridx = 0
			gbc.weightx = 0.0
			gbc.fill = GridBagConstraints.NONE
			formPanel.add(JLabel(param.name), gbc)

			val field = PlaceholderTextField("", 20, param.hint)
			gbc.gridx = 1
			gbc.weightx = 1.0
			gbc.fill = GridBagConstraints.HORIZONTAL
			formPanel.add(field, gbc)
			fields.add(field)

			// error label sits under the field and stays hidden until that parameter fails validation
			val errorLabel = JLabel().apply {
				foreground = Color.RED
				isVisible = false
			}
			gbc.gridy = i * 2 + 1
			formPanel.add(errorLabel, gbc)
			errorLabels.add(errorLabel)
		}
		dialog.add(formPanel, BorderLayout.CENTER)

		// unique options, side by side below the form
		val uniqueURLsCheckbox = JCheckBox("Unique URLs")
		val uniqueResultsCheckbox = JCheckBox("Unique Results").apply { isSelected = true }
		val checkboxPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
			add(uniqueURLsCheckbox)
			add(uniqueResultsCheckbox)
		}

		// button panel
		val okButton = JButton("OK")
		val cancelButton = JButton("Cancel").apply { addActionListener { dialog.dispose() } }

		okButton.addActionListener {
			// validate every parameter exactly once; the validator doubles as a parser, so we keep its
			// mapped result (e.g. an already-compiled Regex) and reuse it for every request/response
			val values = ArrayList<Any?>(config.parameters.size)
			var allValid = true
			config.parameters.forEachIndexed { i, param ->
				val mapped = param.validator(fields[i].text)
				if (mapped == null) {
					errorLabels[i].text = param.validationError
					errorLabels[i].isVisible = true
					allValid = false
				} else {
					errorLabels[i].isVisible = false
					values.add(mapped)
				}
			}
			if (!allValid) {
				// grow the dialog to fit the newly shown error labels
				dialog.pack()
				AppContext.userInterface.applyThemeToComponent(dialog)
				return@addActionListener
			}

			// snapshot the UI state on the EDT before closing the dialog and handing off to the worker
			val uniqueURLs = uniqueURLsCheckbox.isSelected
			val uniqueResults = uniqueResultsCheckbox.isSelected
			dialog.dispose()
			executeExtraction(config, requestResponses, values, uniqueURLs, uniqueResults)
		}

		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
			add(okButton)
			add(cancelButton)
		}

		// checkboxes above the buttons, both pinned to the bottom of the dialog
		val southPanel = JPanel(BorderLayout()).apply {
			add(checkboxPanel, BorderLayout.NORTH)
			add(buttonPanel, BorderLayout.SOUTH)
		}
		dialog.add(southPanel, BorderLayout.SOUTH)
		dialog.rootPane.defaultButton = okButton

		// keybinds
		val esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
		dialog.rootPane.registerKeyboardAction({ dialog.dispose() }, esc, JComponent.WHEN_IN_FOCUSED_WINDOW)

		// popup
		dialog.pack()
		dialog.setLocationRelativeTo(null)
		dialog.isVisible = true
	}

	/**
	 * Runs [config]'s extractor over every request/response on a background thread (so the dialog can
	 * close and Burp stays responsive) and copies the collected lines to the clipboard. [args] holds the
	 * validated + mapped parameter values, already computed once and shared across all request/responses.
	 */
	private fun executeExtraction(
		config: ExtractionConfig,
		requestResponses: List<HttpRequestResponse>,
		args: List<Any?>,
		uniqueURLs: Boolean,
		uniqueResults: Boolean,
	) {
		object : SwingWorker<List<String>, Void>() {
			override fun doInBackground(): List<String> {
				// make requestResponses unique by url if needed
				val rrs = if (uniqueURLs) requestResponses.distinctBy { it.request().url() } else requestResponses
				// drop nulls: the extractor returns null when it has nothing for that request/response
				val extracted = rrs.mapNotNull { config.extractor(it, args) }
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
}
