package org.mattiadr.burpIO

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.handler.HttpHandler
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.http.handler.HttpResponseReceived
import burp.api.montoya.http.handler.RequestToBeSentAction
import burp.api.montoya.http.handler.ResponseReceivedAction

class BurpIOHttpHandler(api: MontoyaApi, val burpIO: BurpIO) : HttpHandler {

	private val repeater = api.repeater()

	override fun handleHttpRequestToBeSent(p0: HttpRequestToBeSent?): RequestToBeSentAction? {
		if (burpIO.autoRepeater && p0?.hasHeader("X-Burp-Repeater") ?: false) {
			repeater.sendToRepeater(p0.withRemovedHeader("X-Burp-Repeater"), p0.headerValue("X-Burp-Repeater"))
		}
		return RequestToBeSentAction.continueWith(p0?.withRemovedHeader("X-Burp-Repeater"))
	}

	override fun handleHttpResponseReceived(p0: HttpResponseReceived?): ResponseReceivedAction? {
		return ResponseReceivedAction.continueWith(p0)
	}

}
