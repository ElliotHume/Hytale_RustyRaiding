import java.net.URI
import java.io.ByteArrayOutputStream
import java.io.InputStream

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "com.apophisgames.rustyraiding"
version = "1.0.0"


repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.hytale.com/release")
    }
    maven {
        url = uri("https://maven.hytale.com/pre-release")
    }
}

dependencies {
    // Hytale Server API
    compileOnly("com.hypixel.hytale:Server:2026.01.24-6e2d4fc36")

    // SQLite JDBC driver
    implementation("org.xerial:sqlite-jdbc:3.51.1.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.17")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }

    shadowJar {
        archiveBaseName.set("RustyRaiding")
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    register("upgradeServer") {
        group = "hytale"
        description = "Downloads the latest downloader, gets the current version, and updates build.gradle.kts"

        doLast {
            val tempDir = project.layout.buildDirectory.dir("tmp/upgrade-server").get().asFile
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            val zipFile = tempDir.resolve("hytale-downloader.zip")
            val downloaderUrl = "https://downloader.hytale.com/hytale-downloader.zip"

            println("Downloading downloader from $downloaderUrl...")
            URI(downloaderUrl).toURL().openStream().use { input: InputStream ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("Unzipping...")
            project.copy {
                from(project.zipTree(zipFile))
                into(tempDir)
            }

            val executable = tempDir.listFiles()?.find { it.name == "hytale-downloader-linux-amd64" && it.isFile }
            if (executable == null) {
                error("Could not find hytale-downloader executable in the zip")
            }

            executable.setExecutable(true)
            println("Found executable: ${executable.name}")

            println("Running ${executable.name} -print-version...")
            val process = ProcessBuilder(executable.absolutePath, "-print-version")
                .redirectErrorStream(true)
                .start()

            val newVersion = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() != 0) {
                error("Downloader failed with exit code ${process.exitValue()}: $newVersion")
            }

            println("Latest version: $newVersion")

            val buildFile = project.file("build.gradle.kts")
            val content = buildFile.readText()
            val regex = Regex("""compileOnly\("com\.hypixel\.hytale:Server:[^"]*"\)""")
            val updatedContent = content.replace(regex, "compileOnly(\"com.hypixel.hytale:Server:$newVersion\")")

            if (content != updatedContent) {
                buildFile.writeText(updatedContent)
                println("Updated build.gradle.kts with version $newVersion")
            } else {
                println("build.gradle.kts is already up to date or version pattern not found.")
            }

            tempDir.deleteRecursively()
        }
    }

    build {
        dependsOn(shadowJar)
    }

}

// Task to deploy to server
tasks.register<Copy>("deploy") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into("${rootProject.projectDir}/../z-server/Server/mods")

    doLast {
        println("Deployed ${tasks.shadowJar.get().archiveFile.get().asFile.name} to z-server/mods/")
    }
}