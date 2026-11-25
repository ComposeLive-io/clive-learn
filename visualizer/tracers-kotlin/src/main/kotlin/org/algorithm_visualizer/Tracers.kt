package org.algorithm_visualizer

// The tracers of the JavaScript library (`algorithm-visualizer` 2.3.9), with the same methods and
// arguments. An optional argument left out is left out of the command too, as in JavaScript.

/** A panel of the visualization. A `null` title lets the player pick its default. */
abstract class Tracer(title: String?) : Commander(title ?: Omitted) {
    /** Clears the panel. */
    fun reset() = command("reset")

    companion object {
        /**
         * Ends a step: the player pauses here and highlights the line that called it. The line is
         * taken from the call stack, so the highlighted line is the one in the visualization's source.
         */
        fun delay() {
            val line = StackWalker.getInstance().walk { frames ->
                frames.filter { !it.className.startsWith("org.algorithm_visualizer.") }
                    .findFirst().map { it.lineNumber }.orElse(-1)
            }
            // The player counts lines from 0.
            if (line > 0) delay(line - 1) else Commander.record(null, "delay", emptyArray())
        }

        /** Ends a step and highlights line [lineNumber], counted from 0. */
        fun delay(lineNumber: Int) = Commander.record(null, "delay", arrayOf(lineNumber))
    }
}

/** Text, one line per [println]. */
open class LogTracer(title: String? = null) : Tracer(title) {
    fun set(log: String? = null) = command("set", log ?: Omitted)
    fun print(message: Any?) = command("print", message)
    fun println(message: Any?) = command("println", message)

    /** Formats like JavaScript's `sprintf`: `%s`, `%d`, `%f` and so on, filled by the player. */
    fun printf(format: String, vararg args: Any?) = command("printf", format, *args)
}

/** A table. `x` is the row, `y` the column. */
open class Array2DTracer(title: String? = null) : Tracer(title) {
    fun set(array2d: Any? = Omitted) = command("set", array2d)
    fun patch(x: Int, y: Int, v: Any? = Omitted) = command("patch", x, y, v)
    fun depatch(x: Int, y: Int) = command("depatch", x, y)
    fun select(sx: Int, sy: Int, ex: Int? = null, ey: Int? = null) =
        command("select", sx, sy, ex ?: Omitted, ey ?: Omitted)
    fun selectRow(x: Int, sy: Int, ey: Int) = command("selectRow", x, sy, ey)
    fun selectCol(y: Int, sx: Int, ex: Int) = command("selectCol", y, sx, ex)
    fun deselect(sx: Int, sy: Int, ex: Int? = null, ey: Int? = null) =
        command("deselect", sx, sy, ex ?: Omitted, ey ?: Omitted)
    fun deselectRow(x: Int, sy: Int, ey: Int) = command("deselectRow", x, sy, ey)
    fun deselectCol(y: Int, sx: Int, ex: Int) = command("deselectCol", y, sx, ex)
}

/** Points; each row of the array is an `[x, y]` pair. */
open class ScatterTracer(title: String? = null) : Array2DTracer(title)

/** A row of values. Red (`patch`) marks a change, blue (`select`) a range, inclusive. */
open class Array1DTracer(title: String? = null) : Tracer(title) {
    fun set(array1d: Any? = Omitted) = command("set", array1d)
    fun patch(x: Int, v: Any? = Omitted) = command("patch", x, v)
    fun depatch(x: Int) = command("depatch", x)
    fun select(sx: Int, ex: Int? = null) = command("select", sx, ex ?: Omitted)
    fun deselect(sx: Int, ex: Int? = null) = command("deselect", sx, ex ?: Omitted)

    /** Mirrors this array's highlights in [chartTracer]. */
    fun chart(chartTracer: ChartTracer) = command("chart", chartTracer)
}

/** An [Array1DTracer] drawn as bars. */
open class ChartTracer(title: String? = null) : Array1DTracer(title)

/** Nodes and edges. */
open class GraphTracer(title: String? = null) : Tracer(title) {
    /** Sets the graph from an adjacency matrix. */
    fun set(array2d: Any? = Omitted) = command("set", array2d)
    fun directed(isDirected: Boolean? = null) = apply { command("directed", isDirected ?: Omitted) }
    fun weighted(isWeighted: Boolean? = null) = apply { command("weighted", isWeighted ?: Omitted) }
    fun layoutCircle() = apply { command("layoutCircle") }
    fun layoutTree(root: Any? = Omitted, sorted: Boolean? = null) =
        apply { command("layoutTree", root, sorted ?: Omitted) }
    fun layoutRandom() = apply { command("layoutRandom") }
    fun addNode(id: Any?, weight: Any? = Omitted, x: Number? = null, y: Number? = null) =
        command("addNode", id, weight, x ?: Omitted, y ?: Omitted)
    fun updateNode(id: Any?, weight: Any? = Omitted, x: Number? = null, y: Number? = null) =
        command("updateNode", id, weight, x ?: Omitted, y ?: Omitted)
    fun removeNode(id: Any?) = command("removeNode", id)
    fun addEdge(source: Any?, target: Any?, weight: Any? = Omitted) = command("addEdge", source, target, weight)
    fun updateEdge(source: Any?, target: Any?, weight: Any? = Omitted) = command("updateEdge", source, target, weight)
    fun removeEdge(source: Any?, target: Any?) = command("removeEdge", source, target)
    fun visit(target: Any?, source: Any? = Omitted, weight: Any? = Omitted) = command("visit", target, source, weight)
    fun leave(target: Any?, source: Any? = Omitted, weight: Any? = Omitted) = command("leave", target, source, weight)
    fun select(target: Any?, source: Any? = Omitted) = command("select", target, source)
    fun deselect(target: Any?, source: Any? = Omitted) = command("deselect", target, source)

    /** Narrates visits in [logTracer]. */
    fun log(logTracer: LogTracer) = command("log", logTracer)
}

/** Arranges tracers and other layouts. */
abstract class Layout(children: List<Commander>) : Commander(children) {
    fun add(child: Commander, index: Int? = null) = command("add", child, index ?: Omitted)
    fun remove(child: Commander) = command("remove", child)
    fun removeAll() = command("removeAll")

    companion object {
        /** Shows [root] as the whole visualization. */
        fun setRoot(root: Commander) = Commander.record(null, "setRoot", arrayOf(root))
    }
}

/** Stacks its children top to bottom. */
open class VerticalLayout(children: List<Commander>) : Layout(children)

/** Places its children left to right. */
open class HorizontalLayout(children: List<Commander>) : Layout(children)
