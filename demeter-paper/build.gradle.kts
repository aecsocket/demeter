plugins {
    id("java-library")
    id("maven-publish")
    id("com.github.johnrengelman.shadow")

    id("net.minecrell.plugin-yml.bukkit")
    id("xyz.jpenilla.run-paper")
}

val pluginName = "Demeter"

repositories {
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://gitlab.com/api/v4/projects/27049637/packages/maven") // Minecommons

    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://repo.dmulloy2.net/nexus/repository/public/")
    mavenCentral()
}

dependencies {
    compileOnly(libs.paper) {
        exclude("junit", "junit")
    }

    implementation(libs.paperBStats)

    // Plugins
    compileOnly(libs.bundles.paperMinecommons)
    compileOnly(libs.paperProtocolLib)
}

tasks {
    javadoc {
        val opt = options as StandardJavadocDocletOptions
        opt.links(
                "https://docs.oracle.com/en/java/javase/17/docs/api/",
                "https://guava.dev/releases/snapshot-jre/api/docs/",
                "https://configurate.aoeu.xyz/4.1.2/apidocs/",
                "https://jd.adventure.kyori.net/api/4.9.2/",
                "https://www.javadoc.io/doc/io.leangen.geantyref/geantyref/1.3.11/",
                "https://aecsocket.gitlab.io/minecommons/javadoc/core/",

                "https://papermc.io/javadocs/paper/1.18/",
                "https://javadoc.commandframework.cloud/",
                "https://aadnk.github.io/ProtocolLib/Javadoc/",
                "https://aecsocket.gitlab.io/minecommons/javadoc/minecommons-paper/"
        )
    }

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
    website = "https://gitlab.com/aecsocket/demeter"
    authors = listOf("aecsocket")
}

publishing {
    publications {
        create<MavenPublication>("gitlab") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = uri("https://gitlab.com/api/v4/projects/25743932/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}
