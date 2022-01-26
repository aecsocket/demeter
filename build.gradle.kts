plugins {
    id("java-library")
    id("maven-publish")
    id("io.freefair.aggregate-javadoc")
}

allprojects {
    group = "com.github.aecsocket"
    version = "1.1.0-SNAPSHOT"
    description = "Natural climate, seasons and weather effects"
}

tasks.aggregateJavadoc {
    val opt = this.options as StandardJavadocDocletOptions
    opt.encoding = "UTF-8"
    opt.addBooleanOption("html5", true)
    opt.addStringOption("-release", "17")
    opt.linkSource()
    opt.links(
            "https://docs.oracle.com/en/java/javase/17/docs/api/",
            "https://jd.adventure.kyori.net/api/4.9.3/",
            "https://aecsocket.github.io/minecommons/docs/",

            "https://papermc.io/javadocs/paper/1.18/",
            "https://javadoc.commandframework.cloud/",
            "https://aadnk.github.io/ProtocolLib/Javadoc/"
    )
}

subprojects {
    apply<JavaLibraryPlugin>()

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    tasks {
        compileJava {
            options.encoding = Charsets.UTF_8.name()
        }

        test {
            useJUnitPlatform()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("github") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aecsocket/demeter")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_ACTOR")
                password = project.findProject("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}
