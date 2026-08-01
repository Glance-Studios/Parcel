plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
    alias(libs.plugins.run.paper)
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jetbrains.annotations)

    // Bundled into the plugin jar - consumers compile against the published api artifact instead.
    implementation(project(":parcel-api"))

    // Resolved at runtime by the bootstrap loader rather than shaded.
    paperLibrary("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    paperLibrary(libs.cloud.paper)
    paperLibrary(libs.cloud.annotations)

    // paper-api is compileOnly for the plugin, but tests need it at runtime - Face carries
    // org.bukkit.Axis, so its static init fails without it.
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}

paper {
    name = "Parcel"
    version = project.version.toString()
    description = "Multi-region custom shape system with an in-game editor"
    authors = listOf("Glance Studios")
    website = "https://github.com/Glance-Studios/Parcel"

    apiVersion = "1.21"

    main = "com.glance.parcel.platform.paper.ParcelPlugin"
    loader = "com.glance.parcel.platform.paper.bootstrap.ParcelLibLoader"
    generateLibrariesJson = true
}
