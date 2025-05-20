package org.mattiadr.burpIO

import burp.api.montoya.MontoyaApi
import burp.api.montoya.logging.Logging
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import org.mattiadr.burpIO.functions.CopyAsMarkdown
import org.mattiadr.burpIO.functions.ExtractStrings
import org.mattiadr.burpIO.functions.SaveResponseBody
import java.awt.Component
import java.util.Collections
import kotlin.jvm.optionals.getOrNull

class BurpIOMenuItemProvider(api: MontoyaApi) : ContextMenuItemsProvider {

	@Suppress("unused")
	private val logging: Logging = api.logging()

	init {
		SaveResponseBody.initApi(api)
		ExtractStrings.initApi(api)
	}

	override fun provideMenuItems(event: ContextMenuEvent?): MutableList<Component> {
		event ?: return Collections.emptyList()

		val menuItems = mutableListOf<Component>()

		// invoked when right-clicking in request/response lists
		event.selectedRequestResponses()?.let {
			if (it.isEmpty()) return@let

			CopyAsMarkdown.setupMenuItems(menuItems, it)
			SaveResponseBody.setupMenuItems(menuItems, it)
			ExtractStrings.setupMenuItems(menuItems, it)
		}

		// invoked when right-clicking in the message editor
		event.messageEditorRequestResponse()?.getOrNull()?.let { messageEditorRequestResponse ->
			val it = listOf(messageEditorRequestResponse.requestResponse())

			CopyAsMarkdown.setupMenuItems(menuItems, it)
			SaveResponseBody.setupMenuItems(menuItems, it)
			ExtractStrings.setupMenuItems(menuItems, it)
		}

		return menuItems
	}
}
