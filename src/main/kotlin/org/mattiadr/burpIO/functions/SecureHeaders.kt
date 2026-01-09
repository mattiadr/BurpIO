package org.mattiadr.burpIO.functions

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.StatusCodeClass
import burp.api.montoya.logging.Logging
import burp.api.montoya.ui.swing.SwingUtils
import burp.api.montoya.utilities.json.JsonNode
import java.awt.Component
import java.io.BufferedReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import kotlin.collections.mutableMapOf

object SecureHeaders {

	data class HeaderResult(val host: String, private val headersList: List<String>) {
		val headers = headersList.associateWithTo(mutableMapOf()) { false }
		val evidence = mutableListOf<HttpRequestResponse>()
	}

	private val URL_HEADERS_ADD = URL("https://owasp.org/www-project-secure-headers/ci/headers_add.json")
	private val URL_HEADERS_REMOVE = URL("https://owasp.org/www-project-secure-headers/ci/headers_remove.json")

	private lateinit var swingUtils: SwingUtils
	private lateinit var logging: Logging
	private var owaspAdd: List<String>? = null
	private var owaspRemove: List<String>? = null

	fun initApi(api: MontoyaApi) {
		val preferences = api.persistence().preferences()
		swingUtils = api.userInterface().swingUtils()
		logging = api.logging()

		// download the latest list
		var connection = URL_HEADERS_ADD.openConnection() as HttpsURLConnection
		if (connection.responseCode in 200..299) {
			val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
			preferences.setString("owasp_headers_add", response)
		}
		connection = URL_HEADERS_REMOVE.openConnection() as HttpsURLConnection
		if (connection.responseCode in 200..299) {
			val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
			preferences.setString("owasp_headers_remove", response)
		}

		// convert json to object
		owaspAdd = preferences.getString("owasp_headers_add")?.let {
			JsonNode.jsonNode(it).asObject().get("headers").asArray().value
		}?.map { it.asObject().getString("name") }
		owaspRemove = preferences.getString("owasp_headers_remove")?.let {
			JsonNode.jsonNode(it).asObject().get("headers").asArray().value
		}?.map { it.asString() }
	}

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		JMenuItem("Project Secure Headers").apply {
			addActionListener {
				if (owaspAdd == null || owaspRemove == null) {
					JOptionPane.showMessageDialog(
						swingUtils.suiteFrame(),
						"Failed to download latest headers from OWASP.",
						"Error",
						JOptionPane.ERROR_MESSAGE
					)
				}
				val (missingHeaders, disclosureHeaders) = analyzeHeaders(requestResponses)
				displayResults(missingHeaders, disclosureHeaders)
			}
			menuItems.add(this)
		}
	}

	private fun analyzeHeaders(requestResponses: List<HttpRequestResponse>): Pair<List<HeaderResult>, List<HeaderResult>> {
		val owaspAdd = this.owaspAdd
		val owaspRemove = this.owaspRemove

		return requestResponses.groupBy { it.request().headerValue("Host") }.entries
			.sortedBy { (host, _) -> host } // TODO better sorting
			.map { (host, requestResponses) ->
				val missingHeaders = HeaderResult(host, owaspAdd!!)
				val disclosureHeaders = HeaderResult(host, owaspRemove!!)

				requestResponses.forEach { rr ->
					if (rr.response().isStatusCodeClass(StatusCodeClass.CLASS_3XX_REDIRECTION)) return@forEach

					owaspAdd.forEach { add ->
						if (!rr.response().hasHeader(add) && missingHeaders.headers[add] == false) {
							missingHeaders.headers[add] = true
							missingHeaders.evidence.add(rr)
						}
					}
					owaspRemove.forEach { remove ->
						if (rr.response().hasHeader(remove) && disclosureHeaders.headers[remove] == false) {
							disclosureHeaders.headers[remove] = true
							disclosureHeaders.evidence.add(rr)
						}
					}
				}

				Pair(missingHeaders, disclosureHeaders)
			}.unzip()
	}

	private fun displayResults(missingHeaders: List<HeaderResult>, disclosureHeaders: List<HeaderResult>) {
		logging.logToOutput("Missing Headers:")
		missingHeaders.forEach {
			logging.logToOutput("${it.host} -> ${it.headers.filterValues { x -> x }.keys.joinToString(",")}")
		}
		logging.logToOutput("Disclosure Headers:")
		disclosureHeaders.forEach {
			logging.logToOutput("${it.host} -> ${it.headers.filterValues { x -> x }.keys.joinToString(",")}")
		}
	}
}
