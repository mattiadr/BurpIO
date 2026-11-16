package com.mattiadr.burpIO.utils

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection

private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
private val TOKEN = Regex("""\$(\d+)|\$\{([^}]+)}|\$\$""")

fun stringToClipboard(string: String, message: String = "Copied to Clipboard") {
	val selection = StringSelection(string)
	clipboard.setContents(selection, selection)
	toast("📋  $message")
}

/**
 * Renders [template] against this match's capture groups.
 *
 * Supports `$N` for the Nth numbered group, `${name}` for a named group, and `$$` for a literal `$`.
 * A named group that didn't participate in the match renders as an empty string.
 */
fun MatchResult.render(template: String): String =
	TOKEN.replace(template) { t ->
		when {
			t.value == "$$" -> "$"
			t.groupValues[1].isNotEmpty() -> groupValues[t.groupValues[1].toInt()]
			else -> groups[t.groupValues[2]]?.value ?: ""
		}
	}

/**
 * Finds the first match of [regex] in [text] and returns either the whole match, or - if [template] is
 * given and non-blank - that match rendered against [template] (see [render]).
 */
fun extractStringRegexTemplate(text: String, regex: Regex, template: String? = null): String? {
	val match = regex.find(text) ?: return null
	return if (template.isNullOrBlank()) match.value else match.render(template)
}
