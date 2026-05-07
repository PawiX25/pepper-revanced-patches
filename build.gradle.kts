plugins {
    kotlin("jvm") version "2.3.10"
    `java-library`
}

group = "app.revanced.patches"
version = "1.4.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("app.revanced:patcher-jvm:22.0.1-SNAPSHOT")
    compileOnly("com.android.tools.smali:smali-dexlib2:3.0.5")
}

val dexOutDir = layout.buildDirectory.dir("dex")

val dexJar by tasks.registering {
    group = "build"
    description = "Runs d8 on the compiled jar to produce classes.dex"
    dependsOn(tasks.named("jar"))
    val jarTask = tasks.named<Jar>("jar")
    inputs.files(jarTask)
    inputs.files(configurations.compileClasspath)
    outputs.dir(dexOutDir)
    doLast {
        val outDir = dexOutDir.get().asFile
        outDir.mkdirs()
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
        val d8 = if (androidHome != null) {
            val buildTools = file("$androidHome/build-tools").listFiles()
                ?.filter { it.isDirectory }
                ?.sortedDescending()
                ?: emptyList()
            buildTools.map { File(it, if (System.getProperty("os.name").lowercase().contains("win")) "d8.bat" else "d8") }
                .firstOrNull { it.exists() }?.absolutePath
                ?: "d8"
        } else "d8"

        val classpathArgs = configurations.compileClasspath.get().files
            .filter { it.exists() }
            .flatMap { listOf("--classpath", it.absolutePath) }

        exec {
            commandLine(
                listOf(d8, "--release") +
                    classpathArgs +
                    listOf("--output", outDir.absolutePath, jarTask.get().archiveFile.get().asFile.absolutePath)
            )
        }
    }
}

tasks.register<Jar>("buildPatchBundle") {
    group = "build"
    description = "Builds the ReVanced patches bundle (.rvp)"
    dependsOn(dexJar)
    archiveExtension.set("rvp")
    archiveBaseName.set("pepper-patches")
    // Bundle has both .class (for desktop revanced-cli) and classes.dex
    // (for Android ReVanced Manager via DexClassLoader).
    from(sourceSets.main.get().output)
    from(dexOutDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Bundle metadata read by ReVanced Manager from META-INF/MANIFEST.MF —
    // without these the bundle shows up as "Bez nazwy" in the source list.
    manifest {
        attributes(
            "Name" to "Pepper Patches",
            "Description" to "Patches for Pepper.com Group apps (Pepper PL/NL/SE/US, Mydealz, HotUKDeals, Dealabs, PromoDescuentos, Preisjäger, Chollometros)",
            "Version" to project.version,
            "Source" to "https://github.com/PawiX25/pepper-revanced-patches",
            "Author" to "PawiX25",
            "License" to "GPL-3.0",
        )
    }
}
