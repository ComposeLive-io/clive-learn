package org.algorithm_visualizer

/**
 * Records the commands a visualization issues, in the format the player reads: a JSON array of
 * `{ "key": <tracer key or null>, "method": <name>, "args": [...] }`, the same as the JavaScript
 * tracers library (`algorithm-visualizer` on npm) records.
 *
 * Arguments are encoded when the command is issued, so changing a list after passing it doesn't
 * change what was recorded. A [Commander] argument is recorded as its key.
 */
abstract class Commander protected constructor(vararg args: Any?) {
    internal val key: String = "t${++keyCount}"

    init {
        objectCount++
        command(javaClass.simpleName, *args)
    }

    /** Removes the tracer or layout from the visualization. */
    fun destroy() {
        objectCount--
        command("destroy")
    }

    protected fun command(method: String, vararg args: Any?) = record(key, method, args)

    companion object {
        private const val MAX_COMMANDS = 1_000_000
        private const val MAX_OBJECTS = 100

        private val recorded = mutableListOf<String>()
        private var objectCount = 0
        private var keyCount = 0

        /** How many commands have been recorded so far. */
        val commandCount: Int get() = recorded.size

        /** Everything recorded so far, as the JSON array the player reads. */
        fun commandsJson(): String = recorded.joinToString(",", "[", "]")

        internal fun record(key: String?, method: String, args: Array<out Any?>) {
            val present = args.dropLastWhile { it === Omitted }
            require(Omitted !in present) { "$method: an argument was left out before one that was given" }
            recorded += buildString {
                append("{\"key\":")
                appendJson(key)
                append(",\"method\":")
                appendJson(method)
                append(",\"args\":")
                appendJson(present)
                append('}')
            }
            if (recorded.size > MAX_COMMANDS) throw IllegalStateException("Too Many Commands")
            if (objectCount > MAX_OBJECTS) throw IllegalStateException("Too Many Objects")
        }
    }
}

/**
 * Default value of an optional argument. Trailing omitted arguments are left out of the command,
 * as when a JavaScript visualization doesn't pass them; `null` is recorded as `null`.
 */
internal object Omitted

/** Writes [value] as JSON the way `JSON.stringify` writes the equivalent JavaScript value. */
internal fun StringBuilder.appendJson(value: Any?) {
    when (value) {
        null -> append("null")
        is Commander -> appendJson(value.key)
        is String -> appendJsonString(value)
        is Char -> appendJsonString(value.toString())
        is Boolean -> append(value)
        is Byte, is Short, is Int, is Long -> append(value)
        is Number -> appendJsonNumber(value.toDouble())
        is Map<*, *> -> {
            append('{')
            value.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(',')
                appendJsonString(k.toString())
                append(':')
                appendJson(v)
            }
            append('}')
        }
        is Iterable<*> -> appendJsonArray(value.asSequence())
        is Sequence<*> -> appendJsonArray(value)
        is Array<*> -> appendJsonArray(value.asSequence())
        is IntArray -> appendJsonArray(value.asSequence())
        is LongArray -> appendJsonArray(value.asSequence())
        is DoubleArray -> appendJsonArray(value.asSequence())
        is FloatArray -> appendJsonArray(value.asSequence())
        is BooleanArray -> appendJsonArray(value.asSequence())
        is CharArray -> appendJsonArray(value.asSequence())
        is ShortArray -> appendJsonArray(value.asSequence())
        is ByteArray -> appendJsonArray(value.asSequence())
        else -> appendJsonString(value.toString())
    }
}

private fun StringBuilder.appendJsonArray(items: Sequence<*>) {
    append('[')
    items.forEachIndexed { i, item ->
        if (i > 0) append(',')
        appendJson(item)
    }
    append(']')
}

private fun StringBuilder.appendJsonNumber(value: Double) {
    when {
        !value.isFinite() -> append("null")
        value == Math.rint(value) && Math.abs(value) < 1e15 -> append(value.toLong())
        else -> append(value)
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}
