package com.mattiadr.burpIO.functions

import burp.api.montoya.core.HighlightColor
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
import kotlin.jvm.optionals.getOrNull

object HistorySearch {

	fun setupMenuItems(menuItems: MutableList<Component>, messageEditorRR: MessageEditorHttpRequestResponse) {
		// if nothing is selected quit
		val offset = messageEditorRR.selectionOffsets().getOrNull() ?: return

		val isRequest = messageEditorRR.selectionContext() == MessageEditorHttpRequestResponse.SelectionContext.REQUEST

		JMenuItem(if (isRequest) "Search Response History" else "Search Request History").apply {
			addActionListener {
				val content = messageEditorRR.requestResponse().let {
					// get selected string in request or response based on isRequest
					if (isRequest) it.request() else it.response()
				}.toString()
				val selectedString = content.substring(offset.startIndexInclusive(), offset.endIndexExclusive())
				// if we have a request we want to look in responses and vice versa
				showDialog(!isRequest, selectedString)
			}
			menuItems.add(this)
		}
	}

	/**
	 * Builds and shows the search window and runs an initial search.
	 *
	 * A non-modal [JFrame] is used (instead of a modal dialog) so it can be minimized to an icon
	 * and does not block interaction with the rest of Burp.
	 *
	 * @param searchIsRequest if true the search targets request history, otherwise response history
	 * @param initialText the string to prefill the search field with
	 */
	private fun showDialog(searchIsRequest: Boolean, initialText: String) {
		val frame = JFrame("History Search")
		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		frame.layout = BorderLayout()

		// --- top controls ---
		val searchField = JTextField(initialText, 30)
		val exactMatchCheck = JCheckBox("Exact word match", true)
		val inScopeCheck = JCheckBox("In scope only", true)
		val requestRadio = JRadioButton("Request", searchIsRequest)
		val responseRadio = JRadioButton("Response", !searchIsRequest)
		ButtonGroup().apply {
			add(requestRadio)
			add(responseRadio)
		}
		val searchButton = JButton("Search")

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
			preferredWidth = 100
			minWidth = 100
			maxWidth = 100
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

			searchButton.isEnabled = false
			statusLabel.text = " Searching..."

			object : SwingWorker<List<ProxyHttpRequestResponse>, Void>() {
				override fun doInBackground(): List<ProxyHttpRequestResponse> =
					search(text, searchInRequest, exactMatch, inScopeOnly)

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
	 */
	private fun search(
		text: String,
		searchInRequest: Boolean,
		exactMatch: Boolean,
		inScopeOnly: Boolean,
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