package com.mattiadr.burpIO.functions

import burp.api.montoya.core.Range
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpMessage
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse
import com.mattiadr.burpIO.Settings
import com.mattiadr.burpIO.stringToClipboard
import java.awt.Component
import javax.swing.JMenuItem

object CopyAsMarkdown {

	private enum class MdSyntax(
		val before: String,     // before request
		val middle: String,     // between request and response
		val after: String,      // after response
		val separator: String,  // between different requests
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

	fun setupListMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		JMenuItem("Copy as Markdown (Full)").apply {
			addActionListener { copyFull(requestResponses) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Dradis (Full)").apply {
			addActionListener { copyFull(requestResponses, MdSyntax.DRADIS) }
			menuItems.add(this)
		}
	}

	fun setupEditorMenuItems(menuItems: MutableList<Component>, messageEditorHttpRequestResponse: MessageEditorHttpRequestResponse) {
		JMenuItem("Copy as Markdown").apply {
			addActionListener { copyTruncated(messageEditorHttpRequestResponse) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Markdown (Full)").apply {
			addActionListener { copyFull(listOf(messageEditorHttpRequestResponse.requestResponse())) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Dradis").apply {
			addActionListener { copyTruncated(messageEditorHttpRequestResponse, MdSyntax.DRADIS) }
			menuItems.add(this)
		}
		JMenuItem("Copy as Dradis (Full)").apply {
			addActionListener { copyFull(listOf(messageEditorHttpRequestResponse.requestResponse()), MdSyntax.DRADIS) }
			menuItems.add(this)
		}
	}

	private fun copyFull(requestResponseList: List<HttpRequestResponse>, syntax: MdSyntax = MdSyntax.MARKDOWN) {
		requestResponseList.joinToString(syntax.separator) {
			syntax.before + (it.request()?.toString() ?: "") + syntax.middle + (it.response()?.toString() ?: "") + syntax.after
		}.let { stringToClipboard(it) }
	}

	private fun copyTruncated(messageEditorHttpRequestResponse: MessageEditorHttpRequestResponse, syntax: MdSyntax = MdSyntax.MARKDOWN) {
		val requestHeaders = Settings.copyAsMarkdown_requestHeaders.split(",")
		val responseHeaders = Settings.copyAsMarkdown_responseHeaders.split(",")

		val requestResponse = messageEditorHttpRequestResponse.requestResponse()
		val request = requestResponse.request()
		val response = requestResponse.response()
		// get request and response selections
		val selectionContext = messageEditorHttpRequestResponse.selectionContext()
		val selectionOffsets = messageEditorHttpRequestResponse.selectionOffsets().orElse(null)
		val requestSelection = if (selectionContext == MessageEditorHttpRequestResponse.SelectionContext.REQUEST) selectionOffsets else null
		val responseSelection = if (selectionContext == MessageEditorHttpRequestResponse.SelectionContext.RESPONSE) selectionOffsets else null

		buildString {
			append(syntax.before)
			append(truncateHttpMessage(request, requestHeaders, requestSelection))
			append(syntax.middle)
			if (requestResponse.hasResponse()) {
				append(truncateHttpMessage(response, responseHeaders, responseSelection))
			}
			append(syntax.after)
		}.let { stringToClipboard(it) }
	}

	private fun truncateHttpMessage(message: HttpMessage, headersToKeep: List<String>, selection: Range?): String {
		val bodyTruncateLen = Settings.copyAsMarkdown_bodyTruncate
		val selectionContext = Settings.copyAsMarkdown_selectionContext

		val raw = message.toString()
		val bodyOffset = message.bodyOffset().coerceIn(0, raw.length)

		// first line (request/status line)
		val headerPart = raw.substring(0, bodyOffset)
		val firstLineEnd = headerPart.indexOf("\r\n").let { if (it == -1) headerPart.indexOf("\n") else it }
		val firstLine = if (firstLineEnd == -1) headerPart.trimEnd() else headerPart.substring(0, firstLineEnd)

		// filter headers, keep order in headersToKeep
		val allHeaders: List<HttpHeader> = message.headers()
		val keptHeaders = mutableListOf<HttpHeader>()
		for (name in headersToKeep) {
			allHeaders.filterTo(keptHeaders) { it.name().equals(name, ignoreCase = true) }
		}

		val headerLines = mutableListOf<String>()
		headerLines.add(firstLine)
		keptHeaders.forEach { headerLines.add(it.toString()) }
		if (keptHeaders.size < allHeaders.size) headerLines.add("[...]")

		// truncate body
		val body = raw.substring(bodyOffset)
		val bodyLength = body.length

		val truncatedBody: String = when {
			bodyLength == 0 -> ""

			selection == null -> {
				if (bodyLength <= bodyTruncateLen) {
					body
				} else {
					body.substring(0, bodyTruncateLen) + "\n[...]"
				}
			}

			else -> {
				// move selection range relative to body, ignoring headers
				// coerce to 0 if selection is before body
				val relStart = (selection.startIndexInclusive() - bodyOffset).coerceIn(0, bodyLength)
				val relEnd = (selection.endIndexExclusive() - bodyOffset).coerceIn(0, bodyLength)

				val contextStart = (relStart - selectionContext).coerceAtLeast(0)
				val contextEnd = (relEnd + selectionContext).coerceAtMost(bodyLength)

				buildString {
					if (contextStart > 0) append("[...]\n")
					append(body.substring(contextStart, contextEnd))
					if (contextEnd < bodyLength) append("\n[...]")
				}
			}
		}

		return buildString {
			append(headerLines.joinToString("\n"))
			append("\n\n")
			append(truncatedBody)
		}
	}

}
