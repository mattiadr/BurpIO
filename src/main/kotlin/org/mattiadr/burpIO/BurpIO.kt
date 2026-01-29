package org.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem

class BurpIO : BurpExtension {

	override fun initialize(api: MontoyaApi?) {
		AppContext.init(api!!)

		api.extension().setName("BurpIO")

		val menuItemProvider = BurpIOMenuItemProvider()
		api.userInterface().registerContextMenuItemsProvider(menuItemProvider)

		val httpHandler = BurpIOHttpHandler()
		api.http().registerHttpHandler(httpHandler)

		BurpIOQuickSession.initHandlers()

		// top menu
		val topMenu = JMenu("BurpIO")
		JCheckBoxMenuItem("Auto Repeater").apply {
			isSelected = false
			addActionListener { AppContext.autoRepeater = this.isSelected }
			topMenu.add(this)
		}
		JMenuItem("Copy Auto Repeater Bruno Script").apply {
			addActionListener { stringToClipboard("req.setHeader(\"X-Burp-Repeater\", req.getName())") }
			topMenu.add(this)
		}
		api.userInterface().menuBar().registerMenu(topMenu)
	}

}
