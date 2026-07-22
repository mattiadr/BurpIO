package com.mattiadr.burpIO.functions

import burp.api.montoya.core.HighlightColor
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.proxy.ProxyHttpRequestResponse
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse
import com.mattiadr.burpIO.AppContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.util.regex.Pattern
import javax.swing.AbstractCellEditor
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingWorker
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import kotlin.jvm.optionals.getOrNull

object HistorySearch {

	fun setupMenuItems(menuItems: MutableList<Component>, messageEditorRR: MessageEditorHttpRequestResponse) {
		// if nothing is selected quit
		val offset = messageEditorRR.selectionOffsets().getOrNull() ?: return

		val isRequest = messageEditorRR.selectionContext() == MessageEditorHttpRequestResponse.SelectionContext.REQUEST

		JMenuItem(if (isRequest) "Search Response History" else "Search Request History").apply {
			addActionListener {
				val rr = messageEditorRR.requestResponse()
				// get selected string in request or response based on isRequest
				val content = (if (isRequest) rr.request() else rr.response()).toString()
				val selectedString = content.substring(offset.startIndexInclusive(), offset.endIndexExclusive())
				// if we have a request we want to look in responses and vice versa
				showDialog(!isRequest, selectedString, currentProxyId(rr))
			}
			menuItems.add(this)
		}
	}

	/**
	 * Best-effort lookup of the proxy history id of the item the selection was made in.
	 *
	 * Montoya exposes neither the proxy id nor usable timing data through the context menu, and the
	 * annotations there are read-only, so we identify the item by discriminators that stay distinct
	 * even for repeated identical requests (e.g. hitting the same path twice hours apart): method,
	 * full URL (host included), response body length and the response Date header. The cheap integer
	 * check (body length) runs first, then the string comparisons. Iterating from the newest entry
	 * returns the most recent match. An item without a response (a timeout, rarely of interest) or
	 * with no match yields null, leaving the id unset so the causal filter is skipped.
	 */
	private fun currentProxyId(current: HttpRequestResponse): Int? {
		val response = current.response() ?: return null
		val method = current.request().method()
		val url = current.request().url()
		val bodyLength = response.body().length()
		val date = response.headerValue("Date")

		for (rr in AppContext.api.proxy().history().asReversed()) {
			val res = rr.response() ?: continue
			// cheapest, most selective check first (int), then the string comparisons
			if (res.body().length() != bodyLength) continue
			if (rr.request().method() != method) continue
			if (res.headerValue("Date") != date) continue
			if (rr.request().url() != url) continue
			return rr.id()
		}
		return null
	}

	/**
	 * Builds and shows the search window and runs an initial search.
	 *
	 * A non-modal [JFrame] is used (instead of a modal dialog) so it can be minimized to an icon
	 * and does not block interaction with the rest of Burp.
	 *
	 * @param searchIsRequest if true the search targets request history, otherwise response history
	 * @param initialText the string to prefill the search field with
	 * @param defaultId the reference id used by the causal (before/after) filter, or null to leave it unset
	 */
	private fun showDialog(searchIsRequest: Boolean, initialText: String, defaultId: Int?) {
		val frame = JFrame("History Search")
		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		frame.layout = BorderLayout()

		// --- top controls ---
		val searchField = JTextField(initialText, 30)
		val exactMatchCheck = JCheckBox("Exact word match", false)
		val inScopeCheck = JCheckBox("In scope only", true)
		val requestRadio = JRadioButton("Request", searchIsRequest)
		val responseRadio = JRadioButton("Response", !searchIsRequest)
		ButtonGroup().apply {
			add(requestRadio)
			add(responseRadio)
		}
		val searchButton = JButton("Search")

		// causal filter: only keep results that could be causally related to the current item, i.e.
		// requests issued AFTER the reference id, or responses received BEFORE it. Its checkbox label
		// doubles as the descriptive text and follows the request/response radio.
		val idField = makeIntegerField(defaultId?.toString() ?: "").apply { isEnabled = defaultId != null }
		val causalCheck = JCheckBox("", defaultId != null)
		// pin the checkbox to the wider of the two labels so the id field does not shift when the text
		// toggles between request/response
		causalCheck.text = "Only responses before ID:"
		causalCheck.preferredSize = causalCheck.preferredSize
		val updateCausalText = {
			causalCheck.text = if (requestRadio.isSelected) "Only requests after ID:" else "Only responses before ID:"
		}
		updateCausalText()
		requestRadio.addActionListener { updateCausalText() }
		responseRadio.addActionListener { updateCausalText() }
		causalCheck.addActionListener { idField.isEnabled = causalCheck.isSelected }

		val controlsRow1 = JPanel(BorderLayout(5, 5)).apply {
			add(JLabel("Search:"), BorderLayout.WEST)
			add(searchField, BorderLayout.CENTER)
			add(searchButton, BorderLayout.EAST)
		}
		val controlsRow2 = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
			add(exactMatchCheck)
			add(inScopeCheck)
			add(requestRadio)
			add(responseRadio)
			add(causalCheck)
			add(idField)
		}
		val topPanel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			border = EmptyBorder(5, 5, 5, 5)
			add(controlsRow1)
			add(controlsRow2)
		}
		frame.add(topPanel, BorderLayout.NORTH)

		// --- results table ---
		val model = ResultsTableModel()
		val table = JTable(model)
		table.rowHeight = 24
		table.fillsViewportHeight = true
		table.preferredScrollableViewportSize = Dimension(680, 320)

		// fixed widths for id and the highlight button, host/path take the rest
		table.columnModel.getColumn(0).apply {
			preferredWidth = 50
			maxWidth = 80
		}
		table.columnModel.getColumn(3).apply {
			preferredWidth = 120
			minWidth = 120
			maxWidth = 120
			cellRenderer = ButtonRenderer("Highlight")
			cellEditor = ButtonEditor("Highlight") { row ->
				model.resultAt(row).annotations().setHighlightColor(HighlightColor.RED)
				table.repaint()
			}
		}

		frame.add(JScrollPane(table), BorderLayout.CENTER)

		// --- south bar ---
		val statusLabel = JLabel(" ")
		val closeButton = JButton("Close").apply { addActionListener { frame.dispose() } }
		val southPanel = JPanel(BorderLayout()).apply {
			border = EmptyBorder(2, 5, 2, 5)
			add(statusLabel, BorderLayout.WEST)
			add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(closeButton) }, BorderLayout.EAST)
		}
		frame.add(southPanel, BorderLayout.SOUTH)

		// --- search wiring ---
		// The search runs on a background thread so the UI stays responsive; the button is
		// disabled while a search is in flight and re-enabled once it completes.
		val runSearch = {
			// snapshot the UI state on the EDT before handing off to the worker
			val text = searchField.text
			val searchInRequest = requestRadio.isSelected
			val exactMatch = exactMatchCheck.isSelected
			val inScopeOnly = inScopeCheck.isSelected
			// an invalid/empty id simply skips the causal filter instead of returning nothing
			val refId = idField.text.trim().toIntOrNull()
			val causalEnabled = causalCheck.isSelected && refId != null
			val referenceId = refId ?: 0

			searchButton.isEnabled = false
			statusLabel.text = " Searching..."

			object : SwingWorker<List<ProxyHttpRequestResponse>, Void>() {
				override fun doInBackground(): List<ProxyHttpRequestResponse> =
					search(text, searchInRequest, exactMatch, inScopeOnly, causalEnabled, referenceId)

				override fun done() {
					try {
						val results = get()
						model.setResults(results)
						statusLabel.text = " ${results.size} result(s)"
					} catch (e: Exception) {
						statusLabel.text = " Search failed: ${e.message}"
					} finally {
						searchButton.isEnabled = true
					}
				}
			}.execute()
		}
		searchButton.addActionListener { runSearch() }
		searchField.addActionListener { runSearch() } // Enter in the text field

		// keybinds
		val esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
		frame.rootPane.registerKeyboardAction({ frame.dispose() }, esc, JComponent.WHEN_IN_FOCUSED_WINDOW)
		frame.rootPane.defaultButton = searchButton

		// initial search
		runSearch()

		// window sizing
		frame.pack()
		clampToScreen(frame)
		frame.setLocationRelativeTo(AppContext.swingUtils.suiteFrame())
		frame.isVisible = true
	}

	/**
	 * Filters the proxy history and returns matches in descending id order.
	 *
	 * @param text the string to search for
	 * @param searchInRequest if true searches the whole request, otherwise the whole response
	 * @param exactMatch if true wraps the term with word boundaries (\b)
	 * @param inScopeOnly if true keeps only in-scope items
	 * @param causalEnabled if true keeps only causally-plausible items relative to [referenceId]
	 * @param referenceId requests are kept if their id is greater, responses if it is smaller
	 */
	private fun search(
		text: String,
		searchInRequest: Boolean,
		exactMatch: Boolean,
		inScopeOnly: Boolean,
		causalEnabled: Boolean,
		referenceId: Int,
	): List<ProxyHttpRequestResponse> {
		if (text.isEmpty()) return emptyList()

		val pattern = if (exactMatch) {
			Pattern.compile("\\b" + Pattern.quote(text) + "\\b", Pattern.CASE_INSENSITIVE)
		} else {
			null
		}

		return AppContext.api.proxy().history()
			.filter { rr ->
				if (inScopeOnly && !rr.request().isInScope) return@filter false
				// keep only requests after / responses before the reference item
				if (causalEnabled) {
					val plausible = if (searchInRequest) rr.id() > referenceId else rr.id() < referenceId
					if (!plausible) return@filter false
				}
				// search the whole message (headers included), not only the body
				val msg = (if (searchInRequest) rr.request() else rr.response()) ?: return@filter false
				if (pattern != null) msg.contains(pattern) else msg.contains(text, false)
			}
			// history is assumed sorted by id ascending, we want it descending
			.reversed()
	}

	/** Clamps the window to at most 90% of the screen, with a sensible minimum. */
	private fun clampToScreen(frame: JFrame) {
		val screen = Toolkit.getDefaultToolkit().screenSize
		val maxW = (screen.width * 0.9).toInt()
		val maxH = (screen.height * 0.9).toInt()
		frame.minimumSize = Dimension(500, 300)
		frame.setSize(
			frame.width.coerceIn(500, maxOf(500, maxW)),
			frame.height.coerceIn(300, maxOf(300, maxH)),
		)
	}

	/** Builds a text field that only accepts digits (and an empty value). */
	private fun makeIntegerField(text: String, columns: Int = 6): JTextField {
		val field = JTextField(text, columns)
		(field.document as AbstractDocument).documentFilter = object : DocumentFilter() {
			private fun digitsOnly(s: String?) = s == null || s.all { it.isDigit() }

			override fun insertString(fb: FilterBypass, offset: Int, string: String?, attr: AttributeSet?) {
				if (digitsOnly(string)) super.insertString(fb, offset, string, attr)
			}

			override fun replace(fb: FilterBypass, offset: Int, length: Int, string: String?, attrs: AttributeSet?) {
				if (digitsOnly(string)) super.replace(fb, offset, length, string, attrs)
			}
		}
		return field
	}

	/** Backing model for the results table. Columns: ID, Host, Path, Highlight button. */
	private class ResultsTableModel : AbstractTableModel() {

		private val columns = arrayOf("ID", "Host", "Path", "")
		private var results: List<ProxyHttpRequestResponse> = emptyList()

		fun setResults(newResults: List<ProxyHttpRequestResponse>) {
			results = newResults
			fireTableDataChanged()
		}

		fun resultAt(row: Int): ProxyHttpRequestResponse = results[row]

		override fun getRowCount(): Int = results.size

		override fun getColumnCount(): Int = columns.size

		override fun getColumnName(column: Int): String = columns[column]

		override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 3

		override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
			val result = results[rowIndex]
			return when (columnIndex) {
				0 -> result.id()
				1 -> result.request().headerValue("Host") ?: ""
				2 -> result.request().pathWithoutQuery()
				else -> "Highlight"
			}
		}
	}

	/** Renders a static button in a table cell. */
	private class ButtonRenderer(text: String) : JButton(text), TableCellRenderer {

		init {
			isOpaque = true
		}

		override fun getTableCellRendererComponent(
			table: JTable,
			value: Any?,
			isSelected: Boolean,
			hasFocus: Boolean,
			row: Int,
			column: Int,
		): Component = this
	}

	/** Editor turning a table cell into a clickable button. */
	private class ButtonEditor(
		private val label: String,
		private val onClick: (Int) -> Unit,
	) : AbstractCellEditor(), TableCellEditor {

		private val button = JButton(label)
		private var editingRow = -1

		init {
			button.addActionListener {
				val row = editingRow
				fireEditingStopped()
				if (row >= 0) onClick(row)
			}
		}

		override fun getTableCellEditorComponent(
			table: JTable,
			value: Any?,
			isSelected: Boolean,
			row: Int,
			column: Int,
		): Component {
			editingRow = row
			return button
		}

		override fun getCellEditorValue(): Any = label
	}
}
