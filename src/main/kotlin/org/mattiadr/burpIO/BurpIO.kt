package org.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi

class BurpIO : BurpExtension {

	private var api: MontoyaApi? = null

	override fun initialize(api: MontoyaApi?) {
		this.api = api!!
		api.extension().setName("BurpIO")

		val menuItemProvider = BurpIOMenuItemProvider(api)
		api.userInterface().registerContextMenuItemsProvider(menuItemProvider)
	}

}
