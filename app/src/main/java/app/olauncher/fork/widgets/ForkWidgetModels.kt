package app.olauncher.fork.widgets

/**
 * Configures which side of the home screen hosts the dedicated Widget Screen.
 */
enum class WidgetScreenPosition {
    DISABLED,
    LEFT,
    RIGHT,
    ;

    val isEnabled: Boolean
        get() = this != DISABLED

    companion object {
        fun fromStorageValue(raw: String?): WidgetScreenPosition =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DISABLED
    }
}

/**
 * Persisted layout and metadata descriptor for a widget pinned on the Widget Screen.
 *
 * [resizeMode] mirrors `android.appwidget.AppWidgetProviderInfo.RESIZE_*` bitflags so
 * this model stays pure Kotlin and unit-testable on the JVM without Android dependencies.
 */
data class ForkWidgetEntry(
    val appWidgetId: Int,
    val colSpan: Int = DEFAULT_SPAN,
    val rowSpan: Int = DEFAULT_SPAN,
    val order: Int = 0,
    val minColSpan: Int = DEFAULT_MIN_SPAN,
    val minRowSpan: Int = DEFAULT_MIN_SPAN,
    val resizeMode: Int = RESIZE_BOTH,
) {
    companion object {
        const val DEFAULT_SPAN = 2
        const val DEFAULT_MIN_SPAN = 1
        const val RESIZE_NONE = 0
        const val RESIZE_HORIZONTAL = 1
        const val RESIZE_VERTICAL = 2
        const val RESIZE_BOTH = 3
    }
}

/**
 * Pure Kotlin JSON serializer/deserializer for [ForkWidgetEntry] lists, avoiding
 * `android.jar` `org.json` stubs in JVM unit tests and external JSON dependencies.
 */
object ForkWidgetEntryJson {
    private const val KEY_APP_WIDGET_ID = "appWidgetId"
    private const val KEY_COL_SPAN = "colSpan"
    private const val KEY_ROW_SPAN = "rowSpan"
    private const val KEY_ORDER = "order"
    private const val KEY_MIN_COL_SPAN = "minColSpan"
    private const val KEY_MIN_ROW_SPAN = "minRowSpan"
    private const val KEY_RESIZE_MODE = "resizeMode"

    fun encode(entries: List<ForkWidgetEntry>): String {
        if (entries.isEmpty()) return "[]"
        val sb = StringBuilder(entries.size * 96)
        sb.append('[')
        entries.forEachIndexed { index, entry ->
            if (index > 0) sb.append(',')
            sb.append('{')
                .append('"').append(KEY_APP_WIDGET_ID).append("\":").append(entry.appWidgetId).append(',')
                .append('"').append(KEY_COL_SPAN).append("\":").append(entry.colSpan).append(',')
                .append('"').append(KEY_ROW_SPAN).append("\":").append(entry.rowSpan).append(',')
                .append('"').append(KEY_ORDER).append("\":").append(entry.order).append(',')
                .append('"').append(KEY_MIN_COL_SPAN).append("\":").append(entry.minColSpan).append(',')
                .append('"').append(KEY_MIN_ROW_SPAN).append("\":").append(entry.minRowSpan).append(',')
                .append('"').append(KEY_RESIZE_MODE).append("\":").append(entry.resizeMode)
                .append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decode(raw: String?): List<ForkWidgetEntry> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "[]") return emptyList()
        return try {
            Parser(text).parseEntryArray()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parseEntryArray(): List<ForkWidgetEntry> {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            if (peek() == ']') {
                pos++
                skipWhitespace()
                if (pos != input.length) throw IllegalArgumentException("Trailing content")
                return emptyList()
            }

            val result = mutableListOf<ForkWidgetEntry>()
            while (true) {
                skipWhitespace()
                result += parseEntryObject()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        break
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            skipWhitespace()
            if (pos != input.length) throw IllegalArgumentException("Trailing content at $pos")
            return result
        }

        private fun parseEntryObject(): ForkWidgetEntry {
            expect('{')
            skipWhitespace()
            if (peek() == '}') {
                pos++
                throw IllegalArgumentException("Missing required appWidgetId")
            }

            var appWidgetId: Int? = null
            var colSpan = ForkWidgetEntry.DEFAULT_SPAN
            var rowSpan = ForkWidgetEntry.DEFAULT_SPAN
            var order = 0
            var minColSpan = ForkWidgetEntry.DEFAULT_MIN_SPAN
            var minRowSpan = ForkWidgetEntry.DEFAULT_MIN_SPAN
            var resizeMode = ForkWidgetEntry.RESIZE_BOTH

            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                when (key) {
                    KEY_APP_WIDGET_ID -> appWidgetId = parseInt()
                    KEY_COL_SPAN -> colSpan = parseInt()
                    KEY_ROW_SPAN -> rowSpan = parseInt()
                    KEY_ORDER -> order = parseInt()
                    KEY_MIN_COL_SPAN -> minColSpan = parseInt()
                    KEY_MIN_ROW_SPAN -> minRowSpan = parseInt()
                    KEY_RESIZE_MODE -> resizeMode = parseInt()
                    else -> skipValue()
                }
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        break
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }

            val resolvedId = appWidgetId ?: throw IllegalArgumentException("Missing appWidgetId")
            return ForkWidgetEntry(
                appWidgetId = resolvedId,
                colSpan = colSpan,
                rowSpan = rowSpan,
                order = order,
                minColSpan = minColSpan,
                minRowSpan = minRowSpan,
                resizeMode = resizeMode,
            )
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= input.length) throw IllegalArgumentException("Unterminated escape")
                        val esc = input[pos++]
                        when (esc) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > input.length) throw IllegalArgumentException("Bad unicode escape")
                                val code = input.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape \\$esc")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseInt(): Int {
            val start = pos
            if (pos < input.length && input[pos] == '-') pos++
            val digitsStart = pos
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos == digitsStart) throw IllegalArgumentException("Expected integer at $start")
            return input.substring(start, pos).toIntOrNull()
                ?: throw IllegalArgumentException("Integer overflow at $start")
        }

        private fun skipValue() {
            skipWhitespace()
            if (pos >= input.length) throw IllegalArgumentException("Unexpected EOF")
            when (input[pos]) {
                '"' -> parseString()
                '{' -> {
                    pos++
                    skipWhitespace()
                    if (peek() == '}') {
                        pos++
                        return
                    }
                    while (true) {
                        skipWhitespace()
                        parseString()
                        skipWhitespace()
                        expect(':')
                        skipValue()
                        skipWhitespace()
                        when (peek()) {
                            ',' -> pos++
                            '}' -> {
                                pos++
                                return
                            }
                            else -> throw IllegalArgumentException("Expected ',' or '}'")
                        }
                    }
                }
                '[' -> {
                    pos++
                    skipWhitespace()
                    if (peek() == ']') {
                        pos++
                        return
                    }
                    while (true) {
                        skipValue()
                        skipWhitespace()
                        when (peek()) {
                            ',' -> pos++
                            ']' -> {
                                pos++
                                return
                            }
                            else -> throw IllegalArgumentException("Expected ',' or ']'")
                        }
                    }
                }
                else -> {
                    val start = pos
                    while (pos < input.length && input[pos] !in charArrayOf(',', '}', ']', ' ', '\t', '\n', '\r')) {
                        pos++
                    }
                    if (pos == start) throw IllegalArgumentException("Unexpected token at $pos")
                }
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun peek(): Char {
            if (pos >= input.length) throw IllegalArgumentException("Unexpected EOF")
            return input[pos]
        }

        private fun expect(expected: Char) {
            if (pos >= input.length || input[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}
