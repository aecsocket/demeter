plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

dependencies {
    compileOnly(libs.minecommons)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.minecommons)
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
