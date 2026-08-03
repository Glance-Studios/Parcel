plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.plugin.yml) apply false
    alias(libs.plugins.run.paper) apply false
}

subprojects {
    group = "com.glance.parcel"
    version = "0.2.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
