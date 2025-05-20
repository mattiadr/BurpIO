package org.mattiadr.burpIO.functions

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection

private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

fun stringToClipboard(string: String) {
	val selection = StringSelection(string)
	clipboard.setContents(selection, selection)
}
