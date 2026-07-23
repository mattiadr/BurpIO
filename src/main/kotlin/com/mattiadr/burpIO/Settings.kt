package com.mattiadr.burpIO

import burp.api.montoya.ui.settings.SettingsPanelBuilder
import burp.api.montoya.ui.settings.SettingsPanelPersistence
import burp.api.montoya.ui.settings.SettingsPanelSetting
import burp.api.montoya.ui.settings.SettingsPanelWithData
import com.mattiadr.burpIO.functions.CopyAsMarkdown
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object Settings {

	// Must stay declared above every `by ...Setting()` property: each delegate registers
	// itself against this builder while the object is initializing, so the builder has to
	// exist first (otherwise it would be read as null and withSetting would throw).
	private val settingsPanelBuilder = SettingsPanelBuilder.settingsPanel()
		.withPersistence(SettingsPanelPersistence.USER_SETTINGS)
		.withTitle("BurpIO")
		.withKeywords("BurpIO")

	/**
	 * Copy as Markdown Settings
	 */
	val copyAsMarkdown_mdFlavor: String by listSetting(
		"Markdown Flavor", CopyAsMarkdown.MdFlavor.entries.map { it.name }, "Markdown",
		"Which Markdown Flavor to use for wrapping Requests and Responses."
	)
	val copyAsMarkdown_requestHeaders: String by stringSetting(
		"Request Headers", "Host,Authorization,Cookie",
		"When copying as Markdown keep these Request Headers."
	)
	val copyAsMarkdown_responseHeaders: String by stringSetting(
		"Response Headers", "Date,Location,Authorization,Set-Cookie",
		"When copying as Markdown keep these Response Headers."
	)
	val copyAsMarkdown_bodyTruncate: Int by integerSetting(
		"Body Truncate", 1000,
		"When copying as Markdown truncate bodies after this many characters."
	)
	val copyAsMarkdown_selectionContext: Int by integerSetting(
		"Selection Context", 250,
		"When copying as Markdown with a selection also copy this many characters before and after the selection."
	)

	private lateinit var settingsPanel: SettingsPanelWithData

	/**
	 * Builds the panel from every setting registered by the delegates above and keeps a
	 * reference so their getters can read live values. Must be called once (at extension
	 * startup) before any setting property is accessed, as the getters rely on [settingsPanel].
	 */
	fun buildSettingsPanel(): SettingsPanelWithData {
		settingsPanel = settingsPanelBuilder.build()
		return settingsPanel
	}

	/**
	 * Delegate that registers its [SettingsPanelSetting] as soon as the property is created,
	 * and reads the live value back from the built panel on every access.
	 */
	private class SettingDelegate<T>(
		setting: SettingsPanelSetting,
		private val reader: () -> T,
	) : ReadOnlyProperty<Any?, T> {
		init {
			settingsPanelBuilder.withSetting(setting)
		}

		override fun getValue(thisRef: Any?, property: KProperty<*>): T = reader()
	}

	private fun stringSetting(name: String, default: String = "", description: String? = null) =
		SettingDelegate(if (description == null) {
			SettingsPanelSetting.stringSetting(name, default)
		} else {
			SettingsPanelSetting.stringSetting(description, name, default)
		}) {
			settingsPanel.getString(name)
		}

	private fun integerSetting(name: String, default: Int = 0, description: String? = null) =
		SettingDelegate(if (description == null) {
			SettingsPanelSetting.integerSetting(name, default)
		} else {
			SettingsPanelSetting.integerSetting(description, name, default)
		}) {
			settingsPanel.getInteger(name)
		}

	private fun booleanSetting(name: String, default: Boolean = false, description: String? = null) =
		SettingDelegate(if (description == null) {
			SettingsPanelSetting.booleanSetting(name, default)
		} else {
			SettingsPanelSetting.booleanSetting(description, name, default)
		}) {
			settingsPanel.getBoolean(name)
		}

	private fun listSetting(name: String, values: List<String>, defaultValue: String?, description: String? = null) =
		SettingDelegate(if (description == null) {
			SettingsPanelSetting.listSetting(name, values, defaultValue)
		} else {
			SettingsPanelSetting.listSetting(description, name, values, defaultValue)
		}) {
			settingsPanel.getString(name)
		}
}
