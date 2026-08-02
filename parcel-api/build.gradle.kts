plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jetbrains.annotations)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

/**
 * Only the API is published. The plugin jar is a server artifact, not something anyone compiles
 * against, and publishing it would invite people to depend on internals that are deliberately
 * `internal`.
 *
 * Credentials come from `gpr.user`/`gpr.key` in ~/.gradle/gradle.properties, or GPR_USER/GPR_TOKEN
 * in the environment - same convention Codex uses to consume CollectableCodexAPI.
 */
publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "parcel-api"

            pom {
                name.set("Parcel API")
                description.set("Region geometry, selections and events for the Parcel plugin")
                url.set("https://github.com/Glance-Studios/Parcel")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/Glance-Studios/Parcel/blob/main/LICENSE")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Glance-Studios/Parcel")
            credentials {
                username = project.findProperty("gpr.user") as String?
                    ?: System.getenv("GPR_USER")
                password = project.findProperty("gpr.key") as String?
                    ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}
