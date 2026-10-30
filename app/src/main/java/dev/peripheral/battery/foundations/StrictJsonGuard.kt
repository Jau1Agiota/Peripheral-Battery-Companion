package dev.peripheral.battery.foundations

/** Bounded strict JSON syntax gate for the platform parser (which otherwise accepts extensions).
 * Does not deserialize or interpret protocol semantics. No I/O.
 */
internal class StrictJsonGuard(private val text: String) {
    private var at = 0
    fun validate() { value(0); ws(); require(at == text.length) }
    private fun ws() { while (at < text.length && text[at] in " \t\r\n") at++ }
    private fun take(c: Char): Boolean { ws(); return if (at < text.length && text[at] == c) { at++; true } else false }
    private fun value(depth: Int) {
        require(depth <= 16); ws(); require(at < text.length)
        when (text[at]) {
            '{' -> {
                at++; val names = mutableSetOf<String>()
                if (!take('}')) {
                    do { ws(); val name = string(); require(names.add(name)); require(take(':')); value(depth + 1) } while (take(','))
                    require(take('}'))
                }
            }
            '[' -> { at++; if (!take(']')) { do { value(depth + 1) } while (take(',')); require(take(']')) } }
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> {
                val start = at
                if (text[at] == '-') at++
                require(at < text.length)
                if (text[at] == '0') at++ else { require(text[at] in '1'..'9'); while (at < text.length && text[at] in '0'..'9') at++ }
                if (at < text.length && text[at] == '.') { at++; digits() }
                if (at < text.length && text[at] in "eE") { at++; if (at < text.length && text[at] in "+-") at++; digits() }
                require(at > start)
            }
        }
    }
    private fun digits() { val start = at; while (at < text.length && text[at] in '0'..'9') at++; require(at > start) }
    private fun literal(word: String) { require(text.startsWith(word, at)); at += word.length }
    private fun string(): String {
        require(at < text.length && text[at++] == '"')
        val out = StringBuilder()
        while (at < text.length) {
            val c = text[at++]
            if (c == '"') return out.toString()
            require(c >= ' ')
            if (c != '\\') out.append(c) else {
                require(at < text.length)
                when (val e = text[at++]) {
                    '"', '\\', '/' -> out.append(e)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        require(at + 4 <= text.length)
                        val hex = text.substring(at, at + 4); require(hex.all { it in "0123456789abcdefABCDEF" })
                        out.append(hex.toInt(16).toChar()); at += 4
                    }
                    else -> error("Invalid JSON escape")
                }
            }
        }
        error("Unterminated JSON string")
    }
}
