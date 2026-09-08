package com.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.swing.SwingUtils
import com.mattiadr.burpIO.modules.BurpIOMenuItemProvider
import com.mattiadr.burpIO.modules.BurpIOQuickSession

@Suppress("unused")
class BurpIO : BurpExtension {

	override fun initialize(api: MontoyaApi?) {
		AppContext.init(api!!)

		api.extension().setName("BurpIO")

		// setup context menu
		val menuItemProvider = BurpIOMenuItemProvider()
		api.userInterface().registerContextMenuItemsProvider(menuItemProvider)

		// setup hotkeys
		BurpIOQuickSession.setupHotKeys()

		// register settings
		api.userInterface().registerSettingsPanel(Settings.buildSettingsPanel())
	}

}

object AppContext {
	lateinit var api: MontoyaApi private set
	lateinit var swingUtils: SwingUtils private set

	fun init(api: MontoyaApi) {
		this.api = api
		this.swingUtils = api.userInterface().swingUtils()
	}
}
