package org.mattiadr.burpIO

import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse
import burp.api.montoya.ui.hotkey.HotKey

object BurpIOQuickSession {

	private val STORAGE_SIZE = 10
	private val SESSION_HEADERS = listOf("Authorization", "Cookie")

	private val sessionStorage: MutableList<List<HttpHeader>?> = MutableList(STORAGE_SIZE) { null }

	fun initHandlers() {
		val userInterface = AppContext.api.userInterface()
		for (i in 0..9) {
			// register store handler
			// take the request fom message editor if it exists, otherwise the first selected message
			userInterface.registerHotKeyHandler(HotKey.hotKey("Store Session #$i", "Ctrl+Shift+$i")) {
				it.messageEditorRequestResponse()
					.map { editor -> editor.requestResponse() }
					.orElseGet { it.selectedRequestResponses().firstOrNull() }
					?.request()?.let { rr ->
						storeSession(i, rr)
					}
			}
			// register apply handler
			userInterface.registerHotKeyHandler(HotKey.hotKey("Apply Session #$i", "Ctrl+$i")) {
				it.messageEditorRequestResponse().orElse(null)?.let { editor ->
					applySession(i, editor)
				}
			}
		}
	}

	private fun storeSession(id: Int, request: HttpRequest) {
		sessionStorage[id] = SESSION_HEADERS.mapNotNull { request.header(it) }
		toast("💾  Stored session to slot #$id")
	}

	private fun applySession(id: Int, editor: MessageEditorHttpRequestResponse) {
		val session = sessionStorage[id] ?: return toast("❌  Session #$id is empty")
		val req = editor.requestResponse().request()
		val newReq = session.fold(req) { acc, header ->
			acc.withHeader(header)
		}
		editor.setRequest(newReq)
		toast("📂  Applied session from slot #$id")
	}

}
