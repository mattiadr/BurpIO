package org.mattiadr.burpIO

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.logging.Logging
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.swing.SwingUtils
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import kotlin.jvm.optionals.getOrNull

class BurpIOMenuItemProvider(api: MontoyaApi) : ContextMenuItemsProvider {

	@Suppress("unused")
	private val logging: Logging = api.logging()
	private val swingUtils: SwingUtils = api.userInterface().swingUtils()
	private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
	private val filenameRegex = Regex("filename\\*?=\"?(.+?)(?:\"|$|\\s|;)")
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

	override fun provideMenuItems(event: ContextMenuEvent?): MutableList<Component> {
		event ?: return Collections.emptyList()

		val menuItems = mutableListOf<Component>()

		(event.selectedRequestResponses().getOrNull(0)
			?: event.messageEditorRequestResponse()?.getOrNull()?.requestResponse())
			?.let { httpRequestResponse ->
				// invoked when you only care about the (first) requestResponse and not the message editor

			}

		event.messageEditorRequestResponse()?.getOrNull()?.let { messageEditorHttpRequestResponse ->
			// invoked when right-clicking in the message editor
			JMenuItem("Copy as Markdown").apply {
				addActionListener { copyAsMarkdown(listOf(messageEditorHttpRequestResponse.requestResponse()), false) }
				menuItems.add(this)
			}

			JMenuItem("Copy as Markdown (Fewer Headers)").apply {
				addActionListener { copyAsMarkdown(listOf(messageEditorHttpRequestResponse.requestResponse()), true) }
				menuItems.add(this)
			}

			if (messageEditorHttpRequestResponse.requestResponse().response() != null) {
				JMenuItem("Save Response Body").apply {
					addActionListener { saveResponseBody(messageEditorHttpRequestResponse.requestResponse()) }
					menuItems.add(this)
				}
			}
		}

		event.selectedRequestResponses()?.let { selectedRequestResponses ->
			if (selectedRequestResponses.isEmpty()) return@let

			// invoked when right-clicking in request/response lists
			JMenuItem("Copy as Markdown").apply {
				addActionListener { copyAsMarkdown(selectedRequestResponses, false) }
				menuItems.add(this)
			}

			JMenuItem("Copy as Markdown (Fewer Headers)").apply {
				addActionListener { copyAsMarkdown(selectedRequestResponses, true) }
				menuItems.add(this)
			}

			val requestResponsesWithBodies = selectedRequestResponses.filter { it.response() != null }
			if (requestResponsesWithBodies.isNotEmpty()) {
				val text = if (requestResponsesWithBodies.size == 1) "Save Response Body"
				else "Save ${requestResponsesWithBodies.size} Response Bodies"

				JMenuItem(text).apply {
					if (selectedRequestResponses.size == 1) {
						addActionListener { saveResponseBody(selectedRequestResponses[0]) }
					} else {
						addActionListener { saveResponseBodies(selectedRequestResponses) }
					}
					menuItems.add(this)
				}
			}
		}

		return menuItems
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

	private fun requestResponseToFileName(requestResponse: HttpRequestResponse): String {
		// first check if this is a download and use content disposition
		var filename = requestResponse
			.response()
			.headerValue("Content-Disposition")
			?.let { filenameRegex.find(it) }
			?.groupValues
			?.getOrNull(1)

		// if not found just use the path
		filename = filename ?: requestResponse.request().pathWithoutQuery()
		filename = File(filename).name

		return if (filename.isNotBlank()) filename else "noname"
	}

	private fun saveResponseBody(requestResponse: HttpRequestResponse) {
		val filename = requestResponseToFileName(requestResponse)

		JFileChooser().apply {
			selectedFile = File(filename)
			if (showSaveDialog(swingUtils.suiteFrame()) == JFileChooser.APPROVE_OPTION) {
				try {
					FileOutputStream(selectedFile).use {
						it.write(requestResponse.response().body().bytes)
					}
				} catch (e: IOException) {
					JOptionPane.showMessageDialog(
						swingUtils.suiteFrame(), e.toString(), "Error while writing to file", JOptionPane.ERROR_MESSAGE
					)
				}
			}
		}
	}

	private fun saveResponseBodies(requestResponseList: List<HttpRequestResponse>) {
		JFileChooser().apply {
			dialogTitle = "Choose a Directory"
			fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
			if (showSaveDialog(swingUtils.suiteFrame()) == JFileChooser.APPROVE_OPTION) {
				try {
					selectedFile.mkdirs()
					// loop through requests and save them
					requestResponseList.forEach { requestResponse ->
						val filename = requestResponseToFileName(requestResponse)
						val path = File(selectedFile, filename)
						var writePath = path
						var count = 0
						while (writePath.exists()) {
							writePath = File(selectedFile, "${path.nameWithoutExtension}-${++count}.${path.extension}")
						}

						FileOutputStream(writePath).use {
							it.write(requestResponse.response().body().bytes)
						}
					}

					// done
					JOptionPane.showMessageDialog(swingUtils.suiteFrame(), "Wrote ${requestResponseList.size} files.")
				} catch (e: IOException) {
					JOptionPane.showMessageDialog(
						swingUtils.suiteFrame(), e.toString(), "Error while writing to file", JOptionPane.ERROR_MESSAGE
					)
				}
			}
		}
	}

}
