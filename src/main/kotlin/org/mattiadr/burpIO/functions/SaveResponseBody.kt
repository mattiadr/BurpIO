package org.mattiadr.burpIO.functions

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.ui.swing.SwingUtils
import java.awt.Component
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JOptionPane

object SaveResponseBody {

	private lateinit var swingUtils: SwingUtils
	private val filenameRegex = Regex("filename\\*?=\"?(.+?)(?:\"|$|\\s|;)")

	fun initApi(api: MontoyaApi) {
		swingUtils = api.userInterface().swingUtils()
	}

	fun setupMenuItems(menuItems: MutableList<Component>, requestResponses: List<HttpRequestResponse>) {
		val requestResponsesWithBodies = requestResponses.filter { it.response() != null }
		if (requestResponsesWithBodies.isNotEmpty()) {
			val text = if (requestResponsesWithBodies.size == 1) "Save Response Body"
			else "Save ${requestResponsesWithBodies.size} Response Bodies"

			JMenuItem(text).apply {
				if (requestResponses.size == 1) {
					addActionListener { saveResponseBody(requestResponses[0]) }
				} else {
					addActionListener { saveResponseBodies(requestResponses) }
				}
				menuItems.add(this)
			}
		}
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

		return filename.ifBlank { "noname" }
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
