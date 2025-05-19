package org.mattiadr.burpIO.functions

import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpRequestResponse
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.JMenuItem

object CopyAsMarkdown {

	private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
	private val uninterestingHeaders = listOf(
		"accept",
		"accept-encoding",
		"accept-language",
		"if-modified-since",
		"if-none-match",
		"priority",
		"sec-ch-ua",
		"sec-ch-ua-arch",
		"sec-ch-ua-bitness",
		"sec-ch-ua-full-version",
		"sec-ch-ua-mobile",
		"sec-ch-ua-model",
		"sec-ch-ua-platform",
		"sec-ch-ua-platform-version",
		"sec-ch-ua-wow64",
		"sec-fetch-dest",
		"sec-fetch-mode",
		"sec-fetch-site",
		"sec-fetch-user",
		"upgrade-insecure-requests",
		"x-requested-with",
	).map(HttpHeader::httpHeader)

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		JMenuItem("Copy as Markdown").apply {
			addActionListener { copyAsMarkdown(requestResponses, false) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Markdown (Fewer Headers)").apply {
			addActionListener { copyAsMarkdown(requestResponses, true) }
			menuItems.add(this)
		}
	}

	// clipboard utility
	private fun stringToClipboard(string: String) {
		val selection = StringSelection(string)
		clipboard.setContents(selection, selection)
	}

	private fun copyAsMarkdown(requestResponseList: List<HttpRequestResponse>, hideHeaders: Boolean) {
		val start = "**Request:**\n```HTTP\n"
		val middle = "\n```\n\n**Response:**\n```HTTP\n"
		val end = "\n```\n"

		requestResponseList.map { requestResponse ->
			// strip headers if needed
			val request = if (hideHeaders)
				requestResponse.request()?.withRemovedHeaders(uninterestingHeaders)
			else
				requestResponse.request()
			val response = if (hideHeaders)
				requestResponse.response()?.withRemovedHeaders(uninterestingHeaders)
			else
				requestResponse.response()

			// build markdown
			start + (request?.toString() ?: "") + middle + (response?.toString() ?: "") + end
		}
			.joinToString("\n\n\n")
			.let { stringToClipboard(it) }
	}
}
