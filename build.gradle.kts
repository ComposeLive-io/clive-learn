// Kotlin visualizations. Every algorithms/<Category>/<Algorithm>/code.kt is compiled on its own
// against the tracers library in visualizer/tracers-kotlin, then run; the commands it records are
// written to build/recorded/<Category>/<Algorithm>.json, which the site replays.
//
//   ./gradlew record     record every Kotlin visualization (fails if one throws)

plugins {
    kotlin("jvm") version "2.4.21"
}

layout.buildDirectory = layout.projectDirectory.dir("build/gradle")

kotlin {
    jvmToolchain(17)
}

// The tracers library.
sourceSets.main {
    kotlin.setSrcDirs(listOf("visualizer/tracers-kotlin/src/main/kotlin"))
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
}

val record = tasks.register("record") {
    group = "verification"
    description = "Runs every Kotlin visualization and records its commands in build/recorded."
}

val recordedDir = layout.projectDirectory.dir("build/recorded")

file("algorithms").listFiles().orEmpty().filter { it.isDirectory }.sorted().forEach { category ->
    category.listFiles().orEmpty().filter { it.resolve("code.kt").isFile }.sorted().forEach { algorithm ->
        // Each visualization gets a source set of its own, so they can all declare the same names.
        val name = "${category.name} ${algorithm.name}"
            .split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        val sourceSet = sourceSets.create("visualization$name") {
            kotlin.setSrcDirs(listOf(algorithm))
            kotlin.include("code.kt")
            java.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(emptyList<String>())
        }
        dependencies.add(sourceSet.implementationConfigurationName, sourceSets.main.get().output)

        val recordOne = tasks.register<JavaExec>("record$name") {
            group = "verification"
            description = "Records ${category.name} / ${algorithm.name}."
            val output = recordedDir.file("${category.name}/${algorithm.name}.json")
            classpath = sourceSet.runtimeClasspath
            mainClass = "org.algorithm_visualizer.Recorder"
            args("CodeKt", output.asFile.path)
            outputs.file(output)
        }
        record { dependsOn(recordOne) }
    }
}
