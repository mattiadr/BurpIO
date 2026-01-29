package org.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem

class BurpIO : BurpExtension {

	// storage
	var autoRepeater: Boolean = false

	override fun initialize(api: MontoyaApi?) {
		AppContext.init(api!!)

		api.extension().setName("BurpIO")

		val menuItemProvider = BurpIOMenuItemProvider(api)
		api.userInterface().registerContextMenuItemsProvider(menuItemProvider)

		val httpHandler = BurpIOHttpHandler(api, this)
		api.http().registerHttpHandler(httpHandler)

		BurpIOQuickSession.initHandlers()

		// top menu
		val topMenu = JMenu("BurpIO")
		JCheckBoxMenuItem("Auto Repeater").apply {
			isSelected = false
			addActionListener { autoRepeater = this.isSelected }
			topMenu.add(this)
		}
		JMenuItem("Copy Auto Repeater Bruno Script").apply {
			addActionListener { stringToClipboard("req.setHeader(\"X-Burp-Repeater\", req.getName())") }
			topMenu.add(this)
		}
		api.userInterface().menuBar().registerMenu(topMenu)
	}

}
