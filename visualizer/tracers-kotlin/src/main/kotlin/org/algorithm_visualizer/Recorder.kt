@file:JvmName("Recorder")

package org.algorithm_visualizer

import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

/**
 * Runs a visualization's `main` and writes the commands it recorded to a file, for the player to
 * replay. Usage: `Recorder <main class> <output file>`.
 *
 * If the visualization throws (a failed self-check, say), nothing is written to the output file;
 * the error goes to `<output file>.error` instead and the process exits with status 1.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: Recorder <main class> <output file>" }
    val (mainClass, outputPath) = args
    val output = File(outputPath)
    val error = File("$outputPath.error")
    output.parentFile.mkdirs()
    output.delete()
    error.delete()

    try {
        Class.forName(mainClass).getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>())
    } catch (e: InvocationTargetException) {
        val cause = e.targetException
        error.writeText(cause.toString())
        cause.printStackTrace()
        exitProcess(1)
    }
    output.writeText(Commander.commandsJson())
    println("${Commander.commandCount} commands")
}
