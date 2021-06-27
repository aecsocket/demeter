# Natura

Natural weather and climate effects

---

This plugin provides several features which imitate real-life natural effects,
such as seasons, weather types, and temperature.

## Setup

### Dependencies

* [Java >=16](https://adoptopenjdk.net/?variant=openjdk16&jvmVariant=hotspot)
* [Paper >=1.17](https://papermc.io/)
* [ProtocolLib >=4.6.0 Dev](https://ci.dmulloy2.net/job/ProtocolLib/lastSuccessfulBuild/)

### [Download](https://gitlab.com/aecsocket/natura/-/jobs/artifacts/master/raw/target/Natura.jar?job=build)

### Coordinates

Repository
```xml
<repository>
    <id>gitlab-maven-natura</id>
    <url>https://gitlab.com/api/v4/projects/25743932/packages/maven</url>
</repository>
```

Dependency
```xml
<dependency>
    <groupId>com.gitlab.aecsocket</groupId>
    <artifactId>natura</artifactId>
    <version>[VERSION]</version>
</dependency>
```

### Configuration

All configuration is stored in the `settings.conf` file. Read that to learn how to configure the plugin.

### API

Both main features - Persistence and DeathCorpses - interact with the Bodies API.
You can read the Javadocs to get usage information. Javadocs not hosted yet.
