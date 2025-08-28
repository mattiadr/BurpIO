package org.mattiadr.burpIO.functions

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.logging.Logging
import burp.api.montoya.ui.swing.SwingUtils
import burp.api.montoya.utilities.json.JsonNode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.Base64
import javax.imageio.ImageIO
import javax.net.ssl.HttpsURLConnection
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.border.MatteBorder

object SecureHeaders {

	// green: always present and correct, non selected
	// yellow: sometimes present and correct, non selected
	// orange: sometimes present but incorrect, non selected
	// red: never present, selected
	private data class OwaspHeader(val name: String, val expectedValue: String,
	                               var presentAlways: Boolean = true, var presentOnce: Boolean = false,
	                               var cbMissing: JCheckBox? = null, var cbWrong: JCheckBox? = null)

	private val URL_HEADERS_ADD = URL("https://owasp.org/www-project-secure-headers/ci/headers_add.json")
	private val ICON_MISSING = base64toIcon("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAAdgAAAHYBTnsmCAAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAEaSURBVDiNldNLTgJBEAbgD2PwEuqKHXgHg3GlXgJM3BK8h0Hjc+MddO0rXgG8gBEOIC7Hx6IL0xknEP+kkq6a+quqq//hLzZwjBE+wkYYoFWR/4sVnOMTT+hhN6wXsU+coV5FfsAbNuc0aWOM+3KRiyCvzhsxsBZFTmeBDRQLOpexFZwmaTmPpYRbdDK/G7EczziCF2lJOTrRoRtWlApCH0OYYqdizP0gFnEuYw/vS+HUKhIWoYbvZbyiUfrYwSUOwr/CF66znEZwDSSR5CgvsYObUs7vElvSPdv/GH9b9owkeY4lkSzCOiY4yYN1SZ5jSSTzOk9wp+J/qEvyLKT79aWn2sNhxIro/IecoyktZyhpZBrnI9mdZ/gBetNFj3bjjrwAAAAASUVORK5CYII=")
	private val ICON_WRONG = base64toIcon("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAAdQAAAHUB48IHZQAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAEeSURBVDiNjdK9SkNBEAXgb42NVTBFQLDIA8SfgFXa1BY2PoFg4xsIPkU6BUGwsxEULATRUtFGa8HGzlKwMFmbEa7JXnVgYOfMOWdnl0k5Z6VIKSUsRfmY64g556lEE3fIkXdolrgzRVcOMY9B5Hxgf08QgjH6Fawf2GCKPyFu4AnHBePj6DV+M9jBOxaj3sJ2nBejt1M0QAtv2KtgD7it1HvBaZUMhnjBXAW7wmWlngvO8IcBuvjE5sSTTnEygW0Gt1s1uMRN4eMOsV/Ab74ngw2M0CsQ22gX8F5oNr7feVSzkbs4qOkd4WoWK7gubhnnSDW9Z6zDGe6xXLqp5vbV0FxAJ9zG+PhnjkPTSTlnKaUG1rBQM+5kvOI+5zz6AjS3d6l57uAjAAAAAElFTkSuQmCC")
	private const val STR_MISSING = "The following **missing security headers** were detected:\n"
	private const val STR_WRONG = "The following **misconfigured security headers** were detected:\n"

	private lateinit var owaspHeaders: List<OwaspHeader>
	private lateinit var swingUtils: SwingUtils
	private lateinit var logging: Logging

	fun initApi(api: MontoyaApi) {
		val preferences = api.persistence().preferences()
		swingUtils = api.userInterface().swingUtils()
		logging = api.logging()

		// download the latest list
		val connection = URL_HEADERS_ADD.openConnection() as HttpsURLConnection
		if (connection.responseCode in 200..299) {
			val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
			preferences.setString("headers_add", response)
		}

		// convert json to object
		owaspHeaders = preferences.getString("headers_add")?.let {
			JsonNode.jsonNode(it).asObject().get("headers").asArray().value
		}?.map { it.asObject() }?.map { OwaspHeader(it.getString("name"), it.getString("value")) }?.sortedBy { it.name } ?: listOf()
	}

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		if (owaspHeaders.isEmpty()) return //TODO print when this fails
		val requestResponsesWithBodies = requestResponses.filter { it.response() != null }
		if (requestResponsesWithBodies.isEmpty()) return

		JMenuItem("OWASP Secure Headers").apply {
			addActionListener {
				val headersResult = compareHeaders(requestResponsesWithBodies)
				showResults(headersResult)
			}
			menuItems.add(this)
		}
	}

	private fun compareHeaders(requestResponses: List<HttpRequestResponse>): List<OwaspHeader> {
		val headersCopy = owaspHeaders.map { it.copy() }
		// this compares the response headers in the provided list with owaspHeaders and stores the results in it
		requestResponses.forEach { rr ->
			headersCopy.forEach { header ->
				if (rr.response().hasHeader(header.name)) {
					header.presentOnce = true
				} else {
					header.presentAlways = false
				}
			}
		}
		return headersCopy
	}

	private fun base64toIcon(base64: String): ImageIcon {
		val bytes = Base64.getDecoder().decode(base64)
		return ImageIcon(ImageIO.read(ByteArrayInputStream(bytes)))
	}

	private fun showResults(headersResult: List<OwaspHeader>) {
		val dialog = JDialog(swingUtils.suiteFrame(), "OWASP Secure Headers", true)
		dialog.layout = BorderLayout()

		// table panel
		val tablePanel = JPanel()
		tablePanel.layout = BoxLayout(tablePanel, BoxLayout.Y_AXIS)
		dialog.add(tablePanel, BorderLayout.CENTER)
		
		// table header
		JPanel(FlowLayout(FlowLayout.LEFT)).apply {
			border = MatteBorder(0, 0, 2, 0, Color.BLACK)

			val tmp = JCheckBox()
			add(JLabel(ICON_MISSING).apply { preferredSize = tmp.preferredSize })
			add(JLabel(ICON_WRONG).apply { preferredSize = tmp.preferredSize })
			add(JLabel("Header Name"))

			tablePanel.add(this)
		}

		// iterate over results
		headersResult.forEach { header ->
			JPanel(FlowLayout(FlowLayout.LEFT)).apply {
				border = MatteBorder(0, 0, 2, 0, Color.BLACK)

				// init checkboxes
				val cbMissing = JCheckBox()
				val cbWrong = JCheckBox()
				cbMissing.addActionListener { if (cbMissing.isSelected) cbWrong.isSelected = false }
				cbWrong.addActionListener { if (cbWrong.isSelected) cbMissing.isSelected = false }

				// set checkboxes starting values
				if (!header.presentOnce) {
					cbMissing.isSelected = true
				}

				// store checkboxes references
				header.cbMissing = cbMissing
				header.cbWrong = cbWrong

				// color row
				background = if (!header.presentOnce) {
					Color.RED
				} else if (header.presentAlways) {
					Color.GREEN
				} else {
					Color.ORANGE
				}

				// build ui
				add(cbMissing)
				add(cbWrong)
				add(JLabel(header.name).apply { foreground = Color.BLACK })
				tablePanel.add(this)
			}
		}

		// button panel
		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
		JButton("Copy").apply {
			addActionListener {
				val text = mutableListOf<String>()
				val missingHeaders = headersResult.filter { it.cbMissing?.isSelected ?: false }.map { "- ${it.name}" }
				val wrongHeaders = headersResult.filter { it.cbWrong?.isSelected ?: false }.map { "- ${it.name}" }
				if (missingHeaders.isNotEmpty()) text += STR_MISSING + missingHeaders.joinToString("\n")
				if (wrongHeaders.isNotEmpty()) text += STR_WRONG + wrongHeaders.joinToString("\n")
				stringToClipboard(text.joinToString("\n\n"))
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
}
