enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://papermc.io/repo/repository/maven-public/")
    }

    plugins {
        id("java-library")
        id("maven-publish")
        id("com.github.johnrengelman.shadow") version "7.1.0"

        id("net.minecrell.plugin-yml.bukkit") version "0.5.1"
        id("xyz.jpenilla.run-paper") version "1.0.6"
    }
}

rootProject.name = "demeter"

include("demeter-paper")
