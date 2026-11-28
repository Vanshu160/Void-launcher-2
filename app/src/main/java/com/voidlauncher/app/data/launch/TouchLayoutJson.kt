package com.voidlauncher.app.data.launch

/** A layout file that cannot be read. The message is shown to the player. */
class TouchLayoutFormatException(message: String) : Exception(message)

/** A decoded JSON object. */
private typealias JsonObject = Map<String, Any?>

/**
 * Reads and writes layouts as JSON, for saving in the app and for import and export. The format is
 * {"format":"void-touch-layout","version":1,"buttons":[...]}.
 *
 * Reading is strict about the envelope (format, version, a bounded size and nesting depth) and forgiving about the
 * buttons: a button without an id is dropped, a repeated id keeps its first copy, numbers are clamped to the range the
 * editor allows, and unknown binding types are skipped. Unknown fields are ignored, so newer files still open.
 *
 * No Android dependency, so the codec can be unit tested on the JVM.
 */
object TouchLayoutJson {
    const val FORMAT = "void-touch-layout"
    const val VERSION = 1

    /** Larger text is refused before parsing, so a bad file cannot use much memory. */
    const val MAX_CHARS = 512 * 1024

    private const val MAX_DEPTH = 8
    private const val MAX_STRING = 4096

    fun encode(layout: TouchLayout): String {
        val out = StringBuilder()
        out.append("{\"format\":").append(quote(FORMAT))
        out.append(",\"version\":").append(VERSION)
        out.append(",\"buttons\":[")
        layout.buttons.forEachIndexed { index, button ->
            if (index > 0) out.append(',')
            encodeButton(out, button)
        }
        out.append("]}")
        return out.toString()
    }

    fun decode(text: String): TouchLayout {
        if (text.length > MAX_CHARS) throw TouchLayoutFormatException("The layout file is too large.")
        val root = Parser(text).parseDocument() as? JsonObject
            ?: throw TouchLayoutFormatException("The file is not a control layout.")
        if (root["format"] != FORMAT) throw TouchLayoutFormatException("The file is not a Void Launcher control layout.")
        val version = (root["version"] as? Double)?.toInt()
        if (version != VERSION) throw TouchLayoutFormatException("Layout version $version is not supported.")
        val entries = root["buttons"] as? List<*> ?: throw TouchLayoutFormatException("The layout has no button list.")
        if (entries.size > TouchLayouts.MAX_BUTTONS) {
            throw TouchLayoutFormatException("A layout can have at most ${TouchLayouts.MAX_BUTTONS} controls.")
        }
        val seen = HashSet<String>()
        val buttons = ArrayList<TouchButton>(entries.size)
        for (entry in entries) {
            val obj = asObject(entry) ?: continue
            val button = decodeButton(obj) ?: continue
            if (seen.add(button.id)) buttons += button
        }
        return TouchLayout(buttons)
    }

    private fun encodeButton(out: StringBuilder, button: TouchButton) {
        val bindings = button.bindings.joinToString(prefix = "[", postfix = "]") { encodeBinding(it) }
        val fields = listOf(
            "id" to quote(button.id),
            "name" to quote(button.name),
            "kind" to quote(if (button.isJoystick) "joystick" else "button"),
            "bindings" to bindings,
            "x" to finite(button.x).toString(),
            "y" to finite(button.y).toString(),
            "anchorRight" to button.anchorRight.toString(),
            "anchorBottom" to button.anchorBottom.toString(),
            "size" to finite(button.size).toString(),
            "opacity" to finite(button.opacity).toString(),
            "background" to button.background.toString(),
            "stroke" to button.stroke.toString(),
            "cornerRadius" to finite(button.cornerRadius).toString(),
            "toggle" to button.toggle.toString(),
            "swipe" to button.swipe.toString(),
            "forwardLock" to button.forwardLock.toString(),
            "absolute" to button.absolute.toString(),
            "inWorld" to button.inWorld.toString(),
            "inMenu" to button.inMenu.toString(),
        )
        out.append('{')
        out.append(fields.joinToString(",") { (name, value) -> "${quote(name)}:$value" })
        out.append('}')
    }

    private fun encodeBinding(binding: Binding): String = when (binding) {
        is Binding.Key -> "{\"type\":\"key\",\"code\":${binding.glfwKey}}"
        is Binding.Mouse -> "{\"type\":\"mouse\",\"button\":${binding.button}}"
        Binding.ScrollUp -> "{\"type\":\"scroll\",\"direction\":\"up\"}"
        Binding.ScrollDown -> "{\"type\":\"scroll\",\"direction\":\"down\"}"
    }

    private fun decodeButton(obj: JsonObject): TouchButton? {
        val id = (obj["id"] as? String)?.trim()?.take(40)?.takeIf { it.isNotEmpty() } ?: return null
        val name = (obj["name"] as? String)?.trim()?.take(TouchLayouts.MAX_NAME_LENGTH)?.takeIf { it.isNotEmpty() } ?: id
        val joystick = obj["kind"] == "joystick"
        val bindings = if (joystick) emptyList() else decodeBindings(obj["bindings"] as? List<*>)
        return TouchButton(
            id = id,
            name = name,
            kind = if (joystick) ButtonKind.JOYSTICK else ButtonKind.BUTTON,
            bindings = bindings,
            x = number(obj["x"], 0.5f).coerceIn(TouchGeometry.MIN_POSITION, TouchGeometry.MAX_POSITION),
            y = number(obj["y"], 0.5f).coerceIn(TouchGeometry.MIN_POSITION, TouchGeometry.MAX_POSITION),
            anchorRight = flag(obj["anchorRight"], false),
            anchorBottom = flag(obj["anchorBottom"], false),
            size = number(obj["size"], 1f).coerceIn(0.5f, 2f),
            opacity = number(obj["opacity"], 1f).coerceIn(0.2f, 1f),
            background = color(obj["background"], DEFAULT_BACKGROUND_ARGB),
            stroke = color(obj["stroke"], DEFAULT_STROKE_ARGB),
            cornerRadius = number(obj["cornerRadius"], 0.5f).coerceIn(0f, 1f),
            toggle = flag(obj["toggle"], false),
            swipe = flag(obj["swipe"], false),
            forwardLock = joystick && flag(obj["forwardLock"], false),
            absolute = joystick && flag(obj["absolute"], false),
            inWorld = flag(obj["inWorld"], true),
            inMenu = flag(obj["inMenu"], false),
        )
    }

    private fun decodeBindings(entries: List<*>?): List<Binding> {
        if (entries == null) return emptyList()
        return entries.mapNotNull { decodeBinding(asObject(it)) }.take(TouchLayouts.MAX_BINDINGS)
    }

    private fun decodeBinding(obj: JsonObject?): Binding? {
        if (obj == null) return null
        return when (obj["type"]) {
            "key" -> (obj["code"] as? Double)?.toInt()?.takeIf { it in 0..999 }?.let { Binding.Key(it) }
            "mouse" -> (obj["button"] as? Double)?.toInt()?.takeIf { it in 0..7 }?.let { Binding.Mouse(it) }
            "scroll" -> when (obj["direction"]) {
                "up" -> Binding.ScrollUp
                "down" -> Binding.ScrollDown
                else -> null
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun asObject(value: Any?): JsonObject? = value as? JsonObject

    private fun number(value: Any?, default: Float): Float {
        val number = value as? Double ?: return default
        return if (number.isFinite()) number.toFloat() else default
    }

    private fun flag(value: Any?, default: Boolean): Boolean = value as? Boolean ?: default

    private fun color(value: Any?, default: Long): Long {
        val number = value as? Double ?: return default
        return if (number.isFinite() && number >= 0.0 && number <= 4294967295.0) number.toLong() else default
    }

    private fun finite(value: Float): Float = if (value.isFinite()) value else 0.5f

    private fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c < ' ' -> out.append(String.format("\\u%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    /** A small recursive-descent JSON reader. Objects become maps, arrays lists, numbers doubles. */
    private class Parser(private val text: String) {
        private var pos = 0

        fun parseDocument(): Any? {
            val value = parseValue(0)
            skipSpace()
            if (pos != text.length) fail("Unexpected text after the layout.")
            return value
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("The layout is nested too deeply.")
            skipSpace()
            return when (peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(depth: Int): JsonObject {
            pos++
            val result = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("Expected a field name.")
                val key = parseString()
                skipSpace()
                expect(':')
                result[key] = parseValue(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or '}'.")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            pos++
            val result = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += parseValue(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or ']'.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = peek()
                pos++
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        val escape = peek()
                        pos++
                        when (escape) {
                            '"', '\\', '/' -> out.append(escape)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Bad escape in text.")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("Bad escape in text.")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Bad escape in text.")
                        }
                    }
                    else -> {
                        if (c < ' ') fail("A control character appears in text.")
                        out.append(c)
                    }
                }
                if (out.length > MAX_STRING) fail("A text value is too long.")
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < text.length && text[pos] in "+-0123456789.eE") pos++
            if (pos == start) fail("Unexpected character in the layout.")
            return text.substring(start, pos).toDoubleOrNull() ?: fail("Bad number in the layout.")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("Unexpected text in the layout.")
            pos += word.length
            return value
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        private fun peek(): Char {
            if (pos >= text.length) fail("The layout ends too early.")
            return text[pos]
        }

        private fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c' in the layout.")
            pos++
        }

        private fun fail(message: String): Nothing = throw TouchLayoutFormatException(message)
    }
}
