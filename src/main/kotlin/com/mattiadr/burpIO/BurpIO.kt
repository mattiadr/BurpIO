package com.mattiadr.burpIO

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem

@Suppress("unused")
class BurpIO : BurpExtension {

	override fun initialize(api: MontoyaApi?) {
		AppContext.init(api!!)

		api.extension().setName("BurpIO")

		// setup context menu
		val menuItemProvider = BurpIOMenuItemProvider()
		api.userInterface().registerContextMenuItemsProvider(menuItemProvider)

		// setup http handler
		val httpHandler = BurpIOHttpHandler()
		api.http().registerHttpHandler(httpHandler)

		// setup hotkeys
		BurpIOQuickSession.setupHotKeys()

		// setup suite menu
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
