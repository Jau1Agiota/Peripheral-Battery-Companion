package dev.peripheral.battery.contract

/** Bounded RFC 8259 reader. Numbers retain their spelling so integer slots cannot coerce strings/floats. */
internal class JsonNumber(val text: String)
internal class StrictJson(private val text: String) {
    private var pos = 0
    fun read(): Any? = value(0).also { whitespace(); require(pos == text.length) { "Trailing JSON" } }
    private fun whitespace() { while (pos < text.length && text[pos] in " \r\n\t") pos++ }
    private fun take(c: Char): Boolean = (pos < text.length && text[pos] == c).also { if (it) pos++ }
    private fun expect(c: Char) { require(take(c)) { "Expected $c" } }
    private fun value(depth: Int): Any? {
        whitespace()
        require(pos < text.length) { "Missing value" }
        return when (text[pos]) {
            '{' -> {
                require(depth < 16) { "JSON depth exceeds 16" }; pos++
                val fields = linkedMapOf<String, Any?>()
                whitespace()
                if (!take('}')) while (true) {
                    whitespace(); val key = string(); whitespace(); expect(':')
                    require(!fields.containsKey(key)) { "Duplicate key" }
                    fields[key] = value(depth + 1); whitespace()
                    if (take('}')) break
                    expect(',')
                }
                fields
            }
            '[' -> {
                require(depth < 16) { "JSON depth exceeds 16" }; pos++
                val items = mutableListOf<Any?>(); whitespace()
                if (!take(']')) while (true) {
                    items.add(value(depth + 1)); whitespace()
                    if (take(']')) break
                    expect(',')
                }
                items
            }
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> {
                val start = pos
                take('-')
                if (!take('0')) { require(pos < text.length && text[pos] in '1'..'9'); while (pos < text.length && text[pos] in '0'..'9') pos++ }
                if (take('.')) digits()
                if (take('e') || take('E')) { if (!take('+')) take('-'); digits() }
                JsonNumber(text.substring(start, pos))
            }
        }
    }
    private fun digits() { val start = pos; while (pos < text.length && text[pos] in '0'..'9') pos++; require(pos > start) }
    private fun literal(word: String, result: Any?): Any? { require(text.startsWith(word, pos)); pos += word.length; return result }
    private fun string(): String {
        expect('"'); val result = StringBuilder()
        while (true) {
            require(pos < text.length) { "Unterminated string" }
            val c = text[pos++]
            if (c == '"') break
            require(c >= ' ') { "Control character" }
            if (c != '\\') result.append(c) else {
                require(pos < text.length)
                result.append(when (val e = text[pos++]) {
                    '"', '\\', '/' -> e
                    'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                    'u' -> { require(pos + 4 <= text.length); val hex = text.substring(pos, pos + 4); require(hex.all { it in "0123456789abcdefABCDEF" }); pos += 4; hex.toInt(16).toChar() }
                    else -> throw IllegalArgumentException("Invalid escape")
                })
            }
        }
        var i = 0
        while (i < result.length) {
            val c = result[i++]
            if (c.isHighSurrogate()) { require(i < result.length && result[i].isLowSurrogate()); i++ }
            else require(!c.isLowSurrogate())
        }
        return result.toString()
    }
}
