package org.mattiadr.burpIO.functions

import burp.api.montoya.http.message.HttpRequestResponse
import org.mattiadr.burpIO.stringToClipboard
import java.awt.Component
import javax.swing.JMenuItem

object CopyAsMarkdown {

	private enum class MdSyntax(
		val before: String,
		val middle: String,
		val after: String,
		val separator: String,
	) {
		MARKDOWN(
			"**Request:**\n```HTTP\n",
			"\n```\n\n**Response:**\n```HTTP\n",
			"\n```\n",
			"\n\n\n"
		),
		DRADIS(
			"*Request:*\n\nbc.. ",
			"\n\np. *Response:*\n\nbc.. ",
			"\n\np. \n\n",
			""
		)
	}

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
		JMenuItem("Copy as Dradis").apply {
			addActionListener { copyFull(requestResponses, MdSyntax.DRADIS) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Dradis (Fewer Headers)").apply {
			addActionListener { copyFewerHeaders(requestResponses, MdSyntax.DRADIS) }
			menuItems.add(this)
		}
	}

	private fun copyFull(requestResponseList: List<HttpRequestResponse>, syntax: MdSyntax = MdSyntax.MARKDOWN) {
		requestResponseList.joinToString(syntax.separator) {
			syntax.before + (it.request()?.toString() ?: "") + syntax.middle + (it.response()?.toString() ?: "") + syntax.after
		}.let { stringToClipboard(it) }
	}

	private fun copyFewerHeaders(requestResponseList: List<HttpRequestResponse>, syntax: MdSyntax = MdSyntax.MARKDOWN) {
		requestResponseList.joinToString(syntax.separator) { requestResponse ->
			val request = requestResponse.request()
			val reqText = request?.toString()
			val reqLine = reqText?.substring(0, reqText.indexOf("\n") + 1)
			val response = requestResponse.response()
			val resText = response?.toString()
			val resLine = resText?.substring(0, resText.indexOf("\n") + 1)

			buildString {
				append(syntax.before)
				if (reqLine != null) {
					append(reqLine)
					request.headers().mapNotNull {
						if (it.name().lowercase() in REQUEST_HEADERS) it.toString() else null
					}.joinToString("\n").let { append(it) }
					append("\n[...]\n\n")
					append(request.bodyToString())
				}
				append(syntax.middle)
				if (resLine != null) {
					append(resLine)
					response.headers().mapNotNull {
						if (it.name().lowercase() in RESPONSE_HEADERS) it.toString() else null
					}.joinToString("\n").let { append(it) }
					append("\n[...]\n\n")
					append(response.bodyToString())
				}
				append(syntax.after)
			}
		}.let { stringToClipboard(it) }
	}

}
