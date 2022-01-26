<div align="center">

<a href="https://aecsocket.github.io/demeter"><img src="banner.png" width="1024" alt="Demeter banner" /></a>

`1.1.0-SNAPSHOT`:
[![build](https://github.com/aecsocket/demeter/actions/workflows/build.yml/badge.svg)](https://github.com/aecsocket/demeter/actions/workflows/build.yml)

</div>

Provides several features which imitate real-life natural effects,
such as seasons, weather types, and temperature.

# Features

- [x] Time dilation for day/night, influenced by season
- [x] Fully configurable seasons, able to change foliage colour
- [ ] Dynamic climate, determining temperature/humidity at positions in the world
- [ ] Crop fertility based on climate
- [ ] Expanded weather effects like rain, snowstorms, sandstorms
- [x] ...and everything is able to be exposed in a display e.g. boss/action bar
- [x] All licensed under GNU GPL v3

Possibly:
* PAPI support?

# Usage

## Downloads

## Dependencies

<details open>
<summary>Paper</summary>

* [Java >=17](https://adoptium.net/)
* [Paper >=1.18.1](https://papermc.io/)
* [Minecommons >=1.4.0](https://github.com/aecsocket/minecommons)
* [ProtocolLib >=4.7.0](https://www.spigotmc.org/resources/protocollib.1997/)

</details>

### [Stable Releases](https://github.com/aecsocket/demeter/releases)

### [Latest Snapshots](https://github.com/aecsocket/demeter/actions/workflows/build.yml)

## Packages

Using any package from the GitHub Packages registry requires you to
authorize with GitHub Packages.

To create a token:

1. Visit https://github.com/settings/tokens/new
2. Create a token with only the `read:packages` scope
3. Save that token as an environment variable and use that in builds

**Note: Never include your token directly in your build scripts!**
Always use an environment variable (or similar).

<details>
<summary>Maven</summary>

### [How to authorize](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

#### In `~/.m2/settings.xml`

```xml
<servers>
  <server>
    <id>github-demeter</id>
    <username>[username]</username>
    <password>[token]</password>
  </server>
</servers>
```

#### In `pom.xml`

Repository
```xml
<repositories>
  <repository>
    <id>github-demeter</id>
    <url>https://maven.pkg.github.com/aecsocket/demeter</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

Dependency
```xml
<dependencies>
  <dependency>
    <groupId>com.github.aecsocket</groupId>
    <artifactId>demeter-[module]</artifactId>
    <version>[version]</version>
  </dependency>
</dependencies>
```

</details>

<details>
<summary>Gradle</summary>

The Kotlin DSL is used here.

### [How to authorize](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)

When building, make sure the `GPR_USERNAME` and `GPR_TOKEN` environment variables are set.

Repository
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/aecsocket/demeter")
        credentials {
            username = System.getenv("GPR_USERNAME")
            password = System.getenv("GPR_TOKEN")
        }
    }
}
```

Dependency
```kotlin
dependencies {
    compileOnly("com.github.aecsocket", "demeter-[module]", "[version]")
}
```

</details>

# Documentation

### [Javadoc](https://aecsocket.github.io/demeter/docs)

### [Wiki](https://github.com/aecsocket/demeter/wiki)
