package org.mattiadr.burpIO

import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.swing.SwingUtils

object AppContext {
	lateinit var api: MontoyaApi private set
	lateinit var swingUtils: SwingUtils private set

	var autoRepeater: Boolean = false

	fun init(api: MontoyaApi) {
		this.api = api
		this.swingUtils = api.userInterface().swingUtils()
	}
}
