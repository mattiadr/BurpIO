package org.mattiadr.burpIO.functions

import burp.api.montoya.http.message.HttpRequestResponse
import org.mattiadr.burpIO.stringToClipboard
import java.awt.Component
import javax.swing.JMenuItem

object CopyAsMarkdown {

	private const val MD_BEFORE = "**Request:**\n```HTTP\n"
	private const val MD_MIDDLE = "\n```\n\n**Response:**\n```HTTP\n"
	private const val MD_AFTER = "\n```\n"
	private const val MD_SEPARATOR = "\n\n\n"

	private val REQUEST_HEADERS = listOf(
		"host",
		"authorization",
		"cookie",
	)

	private val RESPONSE_HEADERS = listOf(
		"date",
		"set-cookie",
	)

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		JMenuItem("Copy as Markdown").apply {
			addActionListener { copyFull(requestResponses) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Markdown (Fewer Headers)").apply {
			addActionListener { copyFewerHeaders(requestResponses) }
			menuItems.add(this)
		}
	}

	private fun copyFull(requestResponseList: List<HttpRequestResponse>) {
		requestResponseList.joinToString(MD_SEPARATOR) {
			MD_BEFORE + (it.request()?.toString() ?: "") + MD_MIDDLE + (it.response()?.toString() ?: "") + MD_AFTER
		}.let { stringToClipboard(it) }
	}

	private fun copyFewerHeaders(requestResponseList: List<HttpRequestResponse>) {
		requestResponseList.joinToString(MD_SEPARATOR) { requestResponse ->
			val request = requestResponse.request()
			val reqText = request?.toString()
			val reqLine = reqText?.substring(0, reqText.indexOf("\n") + 1)
			val response = requestResponse.response()
			val resText = response?.toString()
			val resLine = resText?.substring(0, resText.indexOf("\n") + 1)

			buildString {
				append(MD_BEFORE)
				if (reqLine != null) {
					append(reqLine)
					request.headers().mapNotNull {
						if (it.name().lowercase() in REQUEST_HEADERS) it.toString() else null
					}.joinToString("\n").let { append(it) }
					append("\n[...]\n\n")
					append(request.bodyToString())
				}
				append(MD_MIDDLE)
				if (resLine != null) {
					append(resLine)
					response.headers().mapNotNull {
						if (it.name().lowercase() in RESPONSE_HEADERS) it.toString() else null
					}.joinToString("\n").let { append(it) }
					append("\n[...]\n\n")
					append(response.bodyToString())
				}
				append(MD_AFTER)
			}
		}.let { stringToClipboard(it) }
	}

}
