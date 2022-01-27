plugins {
    id("java-library")
    id("maven-publish")
    id("com.github.johnrengelman.shadow")
    id("net.minecrell.plugin-yml.bukkit")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    compileOnly(libs.paper) {
        exclude("junit", "junit")
    }

    implementation(libs.paperBStats)
    compileOnly(libs.bundles.paperCloud)

    testImplementation(libs.bundles.junit)

    // Plugins
    compileOnly(libs.bundles.paperMinecommons)
    compileOnly(libs.paperProtocolLib)
}

tasks {
    shadowJar {
        listOf(
                "org.bstats"
        ).forEach { relocate(it, "${rootProject.group}.${rootProject.name}.lib.$it") }
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.18.1")
    }
}

bukkit {
    name = "Demeter"
    main = "${project.group}.${rootProject.name}.paper.DemeterPlugin"
    apiVersion = "1.18"
    depend = listOf("Minecommons", "ProtocolLib")
    website = "https://github.com/aecsocket/demeter"
    authors = listOf("aecsocket")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aecsocket/demeter")
            credentials {
                username = System.getenv("GPR_ACTOR")
                password = System.getenv("GPR_TOKEN")
            }
        }
    }
}
