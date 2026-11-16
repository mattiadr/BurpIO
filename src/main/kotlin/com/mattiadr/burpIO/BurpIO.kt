package com.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.UserInterface
import burp.api.montoya.ui.swing.SwingUtils
import burp.api.montoya.utilities.json.JsonUtils
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
	lateinit var userInterface: UserInterface private set
	lateinit var jsonUtils: JsonUtils private set

	fun init(api: MontoyaApi) {
		this.api = api
		this.swingUtils = api.userInterface().swingUtils()
		this.userInterface = api.userInterface()
		this.jsonUtils = api.utilities().jsonUtils()
	}
}
