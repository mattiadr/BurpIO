package org.mattiadr.burpIO

import burp.api.montoya.MontoyaApi

object AppContext {
	lateinit var api: MontoyaApi private set

	fun init(api: MontoyaApi) {
		this.api = api
	}
}
